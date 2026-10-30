package com.sokhan.chat.provider.openai

import com.sokhan.chat.domain.agent.AgentTool
import com.sokhan.chat.domain.model.ChatError
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.provider.ChatMessage
import com.sokhan.chat.provider.ChatMessageRole
import com.sokhan.chat.provider.ChatPayload
import com.sokhan.chat.provider.ChatStreamEvent
import com.sokhan.chat.provider.ProviderContext
import com.sokhan.chat.provider.UsageInfo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiCompatibleClient

    private val config
        get() = ProviderConfig(
            id = "p1",
            name = "Test",
            baseUrl = server.url("/v1").toString(),
            defaultModelId = "gpt-test"
        )

    private fun context(apiKey: String? = "sk-test") = ProviderContext(config, apiKey)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenAiCompatibleClient(okhttp3.OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── listModels ───────────────────────────────────────────────────────

    @Test
    fun `listModels parses the data array`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"object":"list","data":[{"id":"m-a","owned_by":"o","created":5},{"id":"m-b"}]}""")
        )
        val result = client.listModels(context())
        assertTrue(result.isSuccess)
        assertEquals(listOf("m-a", "m-b"), result.getOrThrow().map { it.id })
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
    }

    @Test
    fun `listModels maps 401 to an auth error with the provider detail`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key"}}""")
        )
        val result = client.listModels(context())
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ChatError.Auth)
        assertEquals("Incorrect API key", (error as ChatError.Auth).detail)
    }

    // ── testConnection ───────────────────────────────────────────────────

    @Test
    fun `testConnection reports all steps on success`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":[{"id":"m-a"},{"id":"m-b"}]}""")
        )
        val result = client.testConnection(context())
        assertEquals(emptyList<String>(), result.steps.filter { !it.passed }.map { it.stepKey })
        assertEquals(2, result.models.size)
        assertEquals(null, result.failure)
    }

    @Test
    fun `testConnection reports auth failure on credentials rejection`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key"}}""")
        )
        val result = client.testConnection(context())
        assertTrue(result.steps.first { it.stepKey == "auth" }.passed.not())
        assertTrue(result.failure is ChatError.Auth)
        // The reach step still passed: the server answered.
        assertTrue(result.steps.first { it.stepKey == "reach" }.passed)
    }

    @Test
    fun `testConnection probes chat when the models endpoint rejects`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                if (request.path!!.endsWith("/models")) {
                    MockResponse().setResponseCode(500).setBody("""{"error":"no models"}""")
                } else if (request.path!!.endsWith("/chat/completions")) {
                    MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad auth"}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        val result = client.testConnection(context())
        assertTrue(result.steps.first { it.stepKey == "models" }.passed.not())
        assertTrue(result.steps.first { it.stepKey == "auth" }.passed.not())
        assertTrue(result.failure is ChatError.Auth)
    }

    // ── Streaming chat ───────────────────────────────────────────────────

    private fun sseBody(vararg events: String): String =
        events.joinToString("\n\n") { it } + "\n\n"

    private val samplePayload = ChatPayload(
        model = "gpt-test",
        messages = listOf(ChatMessage(ChatMessageRole.USER, text = "hello")),
        stream = true
    )

    @Test
    fun `streaming emits deltas in order and finishes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"Hel"}}]}""",
                    """data: {"choices":[{"index":0,"delta":{"content":"lo"}}]}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
                    "data: [DONE]"
                )
            )
        )
        val events = client.chatPayload(samplePayload, context()).toList().map { it.getOrThrow() }
        assertEquals(
            listOf(
                ChatStreamEvent.Delta("Hel"),
                ChatStreamEvent.Delta("lo"),
                ChatStreamEvent.Finished("stop")
            ),
            events
        )
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"stream\":true"))
    }

    @Test
    fun `streaming forwards usage frames when present`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"Hi"}}]}""",
                    """data: {"choices":[{"index":0,"delta":{}}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}""",
                    "data: [DONE]"
                )
            )
        )
        val events = client.chatPayload(samplePayload, context()).toList().map { it.getOrThrow() }
        val usage = events.filterIsInstance<ChatStreamEvent.Usage>().single()
        assertEquals(UsageInfo(promptTokens = 1, completionTokens = 2, totalTokens = 3), usage.usage)
    }

    @Test
    fun `streaming skips unparsable frames`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"ok"}}]}""",
                    "data: not-json-at-all",
                    "data: [DONE]"
                )
            )
        )
        val events = client.chatPayload(samplePayload, context()).toList().map { it.getOrThrow() }
        assertEquals(listOf(ChatStreamEvent.Delta("ok"), ChatStreamEvent.Finished(null)), events)
    }

    @Test
    fun `streaming maps http errors onto the taxonomy`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"error":{"message":"rate limited"}}""")
        )
        val results = client.chatPayload(samplePayload, context()).toList()
        val failures = results.filter { it.isFailure }
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.first().exceptionOrNull() is ChatError.RateLimit)
    }

    @Test
    fun `empty stream raises EmptyResponse`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(sseBody("data: [DONE]"))
        )
        val results = client.chatPayload(samplePayload, context()).toList()
        val failures = results.filter { it.isFailure }
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.first().exceptionOrNull() is ChatError.EmptyResponse)
    }

    // ── Non-streaming chat ───────────────────────────────────────────────

    @Test
    fun `non-streaming chat normalises content usage and finish`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"blue"},"finish_reason":"stop"}],
                   "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2},"model":"gpt-test"}"""
            )
        )
        val events = client.chatPayload(samplePayload.copy(stream = false), context()).toList().map { it.getOrThrow() }
        assertEquals(
            listOf(
                ChatStreamEvent.Delta("blue"),
                ChatStreamEvent.Usage(com.sokhan.chat.provider.UsageInfo(1, 1, 2)),
                ChatStreamEvent.Finished("stop")
            ),
            events
        )
    }

    @Test
    fun `non-streaming chat maps non-200 to the taxonomy`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"error":{"message":"no route"}}""")
        )
        val results = client.chatPayload(samplePayload.copy(stream = false), context()).toList()
        val failures = results.filter { it.isFailure }
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.first().exceptionOrNull() is ChatError.NotFound)
    }

    // ── Agent mode (function calling) ────────────────────────────────────

    private fun agentPayload() = samplePayload.copy(
        tools = listOf(
            AgentTool(
                name = "set_theme",
                description = "Change the app theme",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("theme") { put("type", "string") }
                    }
                }
            )
        )
    )

    @Test
    fun `request carries the tools array when the payload has tools`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"done"}}]}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
                    "data: [DONE]"
                )
            )
        )
        client.chatPayload(agentPayload(), context()).toList().map { it.getOrThrow() }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"tools\":["))
        assertTrue(body.contains("\"name\":\"set_theme\""))
    }

    @Test
    fun `plain chats send no tools key at all`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"ok"}}]}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
                    "data: [DONE]"
                )
            )
        )
        client.chatPayload(samplePayload, context()).toList().map { it.getOrThrow() }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(!body.contains("\"tools\""))
    }

    @Test
    fun `streaming parses fragmented tool calls without failing as empty`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"set_theme","arguments":""}}]}}]}""",
                    """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"the"}}]}}]}""",
                    """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"me\":\"dark\"}"}}]}}]}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
                    "data: [DONE]"
                )
            )
        )
        val events = client.chatPayload(agentPayload(), context()).toList().map { it.getOrThrow() }
        assertEquals(
            listOf(
                ChatStreamEvent.ToolCallDelta(0, "call_1", "set_theme", ""),
                ChatStreamEvent.ToolCallDelta(0, null, null, "{\"the"),
                ChatStreamEvent.ToolCallDelta(0, null, null, "me\":\"dark\"}"),
                ChatStreamEvent.Finished("tool_calls")
            ),
            events
        )
    }

    @Test
    fun `a tool-only turn is a valid response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"set_theme","arguments":"{}"}}]}}]}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
                    "data: [DONE]"
                )
            )
        )
        val events = client.chatPayload(agentPayload(), context()).toList().map { it.getOrThrow() }
        assertTrue(events.none { it is ChatStreamEvent.Delta })
        assertTrue(events.last() is ChatStreamEvent.Finished)
    }

    // ── OpenRouter compatibility ─────────────────────────────────────────

    @Test
    fun `a 404 on a tool request surfaces the provider detail`() = runTest {
        // OpenRouter answers this for models whose routes do not implement
        // tool calling; the engine relies on the NotFound type to fall back.
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"code":404,"message":"No endpoints found that support tool use."}}"""
            )
        )
        val results = client.chatPayload(agentPayload(), context()).toList()
        val failure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        assertTrue(failure is ChatError.NotFound)
        assertTrue(failure?.message.orEmpty().contains("tool use"))
    }

    @Test
    fun `streaming surfaces an in-band provider error`() = runTest {
        // OpenRouter reports provider failures inside a 200 SSE stream.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"partial"}}]}""",
                    """data: {"error":{"code":502,"message":"Provider returned error"}}"""
                )
            )
        )
        val results = client.chatPayload(samplePayload, context()).toList()
        val failure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        assertTrue(failure is ChatError.Server)
        assertEquals(502, (failure as ChatError.Server).code)
        assertEquals("Provider returned error", failure.detail)
    }

    @Test
    fun `an in-band 404 becomes a not-found error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"error":{"code":404,"message":"No endpoints found that support tool use."}}"""
                )
            )
        )
        val results = client.chatPayload(agentPayload(), context()).toList()
        val failure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        assertTrue(failure is ChatError.NotFound)
    }

    @Test
    fun `OpenRouter keepalive comments and a usage-only chunk do not break the stream`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    ": OPENROUTER PROCESSING",
                    """data: {"choices":[{"index":0,"delta":{"content":"ok"}}]}""",
                    ": OPENROUTER PROCESSING",
                    """data: {"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
                    "data: [DONE]"
                )
            )
        )
        val events = client.chatPayload(samplePayload, context()).toList().map { it.getOrThrow() }
        assertEquals(
            listOf(
                ChatStreamEvent.Delta("ok"),
                ChatStreamEvent.Usage(UsageInfo(3, 1, 4)),
                ChatStreamEvent.Finished("stop")
            ),
            events
        )
    }

    @Test
    fun `a rejected tool request is retried without the tools key`() = runTest {
        // Wire-level replay of the OpenRouter downgrade: 404 for the tools
        // request, then a plain request that answers normally.
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"code":404,"message":"No endpoints found that support tool use."}}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                sseBody(
                    """data: {"choices":[{"index":0,"delta":{"content":"ok"}}]}""",
                    """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
                    "data: [DONE]"
                )
            )
        )

        val first = client.chatPayload(agentPayload(), context()).toList()
        assertTrue(first.last().exceptionOrNull() is ChatError.NotFound)

        val second = client.chatPayload(samplePayload, context()).toList()
            .map { it.getOrThrow() }
        assertEquals(
            listOf(ChatStreamEvent.Delta("ok"), ChatStreamEvent.Finished("stop")),
            second
        )

        assertTrue(server.takeRequest().body.readUtf8().contains("\"tools\":["))
        assertTrue(!server.takeRequest().body.readUtf8().contains("\"tools\""))
    }

    @Test
    fun `non-streaming chat surfaces tool calls as deltas`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"c9","type":"function","function":{"name":"set_theme","arguments":"{\"theme\":\"light\"}"}}]},"finish_reason":"tool_calls"}],"model":"gpt-test"}"""
            )
        )
        val events = client.chatPayload(agentPayload().copy(stream = false), context())
            .toList().map { it.getOrThrow() }
        assertEquals(
            listOf(
                ChatStreamEvent.ToolCallDelta(0, "c9", "set_theme", "{\"theme\":\"light\"}"),
                ChatStreamEvent.Finished("tool_calls")
            ),
            events
        )
    }
}
