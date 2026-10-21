package com.sokhan.chat.provider.openai

import com.sokhan.chat.core.AppLog
import com.sokhan.chat.domain.model.ChatError
import com.sokhan.chat.provider.AiProviderClient
import com.sokhan.chat.provider.ChatMessage
import com.sokhan.chat.provider.ChatMessageRole
import com.sokhan.chat.provider.ChatPayload
import com.sokhan.chat.provider.ChatStreamEvent
import com.sokhan.chat.provider.ConnectionTestResult
import com.sokhan.chat.provider.ConnectionTestStep
import com.sokhan.chat.provider.ProviderContext
import com.sokhan.chat.provider.ProviderModelInfo
import com.sokhan.chat.provider.UsageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI-compatible provider client (Chat Completions + /models + SSE).
 *
 * Extensible-de-facto: works with OpenAI, DeepSeek, OpenRouter, Groq, Mistral,
 * Together, LM Studio, Ollama, llama.cpp, vLLM, Azure-style gateways (with
 * `apiVersion`) and hundreds of other compatible endpoints.
 */
class OpenAiCompatibleClient(
    private val baseClient: OkHttpClient
) : AiProviderClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val streamingClient: OkHttpClient = baseClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val probeClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── Endpoint + header plumbing ───────────────────────────────────────

    private fun rawBase(configProvider: ProviderContext): String =
        configProvider.config.baseUrl.trim().trimEnd('/')

    private fun url(context: ProviderContext, path: String): String {
        val raw = rawBase(context)
        if (raw.isEmpty()) throw ChatError.InvalidConfiguration("empty base URL")
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            throw ChatError.InvalidConfiguration("base URL must be http(s)")
        }
        val version = context.config.apiVersion?.trim()
        return if (version.isNullOrBlank()) "$raw$path" else "$raw$path?api-version=$version"
    }

    private fun buildHeaders(
        context: ProviderContext,
        withAuth: Boolean = true
    ): Headers.Builder {
        val builder = Headers.Builder()
        val custom = context.config.headers
        val customKeySet = custom.keys.map { it.lowercase() }.toSet()
        custom.forEach { (key, value) -> builder.set(key, value) }

        val org = context.config.organization?.trim()
        if (!org.isNullOrBlank() && !customKeySet.contains("openai-organization")) {
            builder.set("OpenAI-Organization", org)
        }
        val key = context.apiKey?.trim()
        if (
            withAuth && !key.isNullOrBlank() && !customKeySet.contains("authorization")
        ) {
            builder.set("Authorization", "Bearer $key")
        }
        builder.set("Accept", "application/json")
        return builder
    }

    private fun Request.Builder.withJsonBody(body: String): Request.Builder =
        this.post(body.toRequestBody(jsonMediaType))

    // ── Public API ───────────────────────────────────────────────────────

    override fun chatPayload(
        payload: ChatPayload,
        context: ProviderContext
    ): Flow<Result<ChatStreamEvent>> = flow {
        try {
            if (!payload.stream) {
                emit(Result.success(nonStreamingChat(payload, context)))
            } else {
                streamChat(payload, context).collect { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChatError) {
            emit(Result.failure(e))
        } catch (e: Throwable) {
            AppLog.e("OpenAI", "unexpected failure", e)
            emit(Result.failure(ChatError.Unknown(e, e.message)))
        }
    }

    override suspend fun listModels(
        context: ProviderContext
    ): Result<List<ProviderModelInfo>> = runCatching {
        val request = probeRequest(context, url(context, "/models"), authorizing = true)
        val (code, body) = probeClient.newCall(request).await()
        when (code) {
            200 -> parseModelsResponse(body)
            else -> throw mapHttpStatusToError(code, body)
        }
    }.recoverCatching { t ->
        when (t) {
            is CancellationException -> throw t
            is ChatError -> throw t
            else -> throw (mapTransportToChatError(t) ?: ChatError.Unknown(t, t.message))
        }
    }

    override suspend fun testConnection(context: ProviderContext): ConnectionTestResult {
        val steps = ArrayList<ConnectionTestStep>()
        var models: List<ProviderModelInfo> = emptyList()
        try {
            val request = probeRequest(context, url(context, "/models"), authorizing = true)
            val (code, body) = probeClient.newCall(request).await()
            when (code) {
                200 -> {
                    models = parseModelsResponse(body)
                    steps += ConnectionTestStep(KEY_REACH, passed = true)
                    steps += ConnectionTestStep(KEY_AUTH, passed = true)
                    steps += ConnectionTestStep(KEY_MODELS, passed = true, detail = models.size.toString())
                    return ConnectionTestResult(steps, models)
                }
                401, 403 -> {
                    steps += ConnectionTestStep(KEY_REACH, passed = true)
                    steps += ConnectionTestStep(KEY_AUTH, passed = false, detail = parseErrorDetail(body))
                    return ConnectionTestResult(
                        steps,
                        failure = ChatError.Auth(parseErrorDetail(body))
                    )
                }
                else -> {
                    steps += ConnectionTestStep(KEY_REACH, passed = true)
                    steps += ConnectionTestStep(
                        KEY_MODELS,
                        passed = false,
                        detail = "HTTP $code ${parseErrorDetail(body).orEmpty()}"
                    )
                    // models endpoint rejected; probe a chat call to decide auth
                    val probe = probeChat(context)
                    steps += ConnectionTestStep(KEY_AUTH, passed = probe.authOk, detail = probe.detail)
                    if (!probe.authOk && probe.failure != null) {
                        return ConnectionTestResult(steps, failure = probe.failure)
                    }
                    return ConnectionTestResult(steps, models)
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            val mapped = mapTransportToChatError(t) ?: ChatError.Unknown(t, t.message)
            steps += ConnectionTestStep(KEY_REACH, passed = false, detail = mapped.message)
            return ConnectionTestResult(steps, failure = mapped)
        }
    }

    // ── Streaming ────────────────────────────────────────────────────────

    private fun streamChat(
        payload: ChatPayload,
        context: ProviderContext
    ): Flow<Result<ChatStreamEvent>> = flow {
        val body = json.encodeToString(
            OpenAiChatRequestBody.serializer(),
            OpenAiChatRequestBody(
                model = payload.model,
                messages = payload.messages.map(ChatMessage::toWireMessage),
                stream = true,
                streamOptions = if (payload.includeUsage) {
                    OpenAiStreamOptions(includeUsage = true)
                } else {
                    null
                },
                temperature = payload.temperature,
                maxTokens = payload.maxTokens,
                topP = payload.topP,
                frequencyPenalty = payload.frequencyPenalty,
                presencePenalty = payload.presencePenalty
            )
        )
        val request = buildRequest(context, chatUrl(context))
            .addHeader("Accept", "text/event-stream")
            .withJsonBody(body)
            .build()

        var finishReason: String? = null
        var gotContent = false
        var skipped: Int = 0

        streamingClient.sseDataFlow(request).collect { line ->
            if (line.trim() == "[DONE]") return@collect
            val chunk = runCatching {
                json.decodeFromString(OpenAiStreamChunk.serializer(), line)
            }.getOrNull() ?: run {
                skipped++
                return@collect
            }
            chunk.usage?.let { usage ->
                emit(
                    Result.success(
                        ChatStreamEvent.Usage(
                            UsageInfo(usage.promptTokens, usage.completionTokens, usage.totalTokens)
                        )
                    )
                )
            }
            val choice = chunk.choices?.firstOrNull() ?: return@collect
            choice.delta?.let { delta ->
                val reasoning = delta.reasoningContent ?: delta.reasoning
                if (!reasoning.isNullOrBlank()) {
                    emit(Result.success(ChatStreamEvent.ReasoningDelta(reasoning)))
                }
                if (!delta.content.isNullOrEmpty()) {
                    gotContent = true
                    emit(Result.success(ChatStreamEvent.Delta(delta.content)))
                }
            }
            choice.finishReason?.let { reason ->
                finishReason = (reason as? JsonPrimitive)?.content ?: reason.toString()
            }
        }
        AppLog.d("OpenAI", "stream done; skipped=$skipped finish=$finishReason")
        if (!gotContent) {
            throw ChatError.EmptyResponse
        }
        emit(Result.success(ChatStreamEvent.Finished(finishReason)))
    }

    private suspend fun nonStreamingChat(
        payload: ChatPayload,
        context: ProviderContext
    ): ChatStreamEvent {
        val body = json.encodeToString(
            OpenAiChatRequestBody.serializer(),
            OpenAiChatRequestBody(
                model = payload.model,
                messages = payload.messages.map(ChatMessage::toWireMessage),
                temperature = payload.temperature,
                maxTokens = payload.maxTokens,
                topP = payload.topP,
                frequencyPenalty = payload.frequencyPenalty,
                presencePenalty = payload.presencePenalty
            )
        )
        val request = buildRequest(context, chatUrl(context))
            .withJsonBody(body)
            .build()
        val (code, response) = probeClient.newCall(request).await()
        if (code != 200) {
            throw mapHttpStatusToError(code, response)
        }
        val parsed = runCatching {
            json.decodeFromString(OpenAiCompletionResponse.serializer(), response.orEmpty())
        }.getOrNull() ?: throw ChatError.Malformed(parseErrorDetail(response))
        val content = parsed.choices?.firstOrNull()?.message?.content.orEmpty()
        val reasoning = parsed.choices?.firstOrNull()?.message?.let {
            it.reasoningContent ?: it.reasoning
        }
        if (content.isBlank() && reasoning.isNullOrBlank()) throw ChatError.EmptyResponse
        return ChatStreamEvent.Finished(parsed.choices?.firstOrNull()?.finishReason?.content)
    }

    // ── Probe helpers (test connection) ──────────────────────────────────

    private data class ProbeOutcome(
        val authOk: Boolean,
        val detail: String?,
        val failure: ChatError?
    )

    private suspend fun probeChat(context: ProviderContext): ProbeOutcome {
        try {
            val request = buildRequest(context, chatUrl(context))
                .withJsonBody(
                    json.encodeToString(
                        OpenAiChatRequestBody.serializer(),
                        OpenAiChatRequestBody(
                            model = PROBE_MODEL,
                            messages = listOf(
                                ChatMessage(
                                    role = ChatMessageRole.USER,
                                    text = "ping"
                                ).toWireMessage()
                            ),
                            maxTokens = 1
                        )
                    )
                ).build()
            val (code, body) = probeClient.newCall(request).await()
            return when (code) {
                401, 403 -> ProbeOutcome(false, parseErrorDetail(body), ChatError.Auth(parseErrorDetail(body)))
                429 -> ProbeOutcome(true, "accepted (rate limited)", null)
                else -> ProbeOutcome(true, parseErrorDetail(body), null)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val mapped = mapTransportToChatError(t) ?: ChatError.Unknown(t, t.message)
            return ProbeOutcome(false, mapped.message, mapped)
        }
    }

    private fun buildRequest(context: ProviderContext, targetUrl: String): Request.Builder =
        Request.Builder()
            .url(targetUrl)
            .headers(buildHeaders(context).build())

    private fun probeRequest(
        context: ProviderContext,
        targetUrl: String,
        authorizing: Boolean
    ): Request = Request.Builder()
        .url(targetUrl)
        .headers(buildHeaders(context, withAuth = authorizing).build())
        .get()
        .build()

    private fun chatUrl(context: ProviderContext): String = url(context, "/chat/completions")

    // ── Model list parsing (tolerant) ────────────────────────────────────

    private fun parseModelsResponse(body: String?): List<ProviderModelInfo> {
        val text = body.orEmpty()
        if (text.isBlank()) return emptyList()
        return runCatching {
            val element = json.parseToJsonElement(text)
            val items = when (element) {
                is JsonObject -> element["data"] as? JsonArray
                is JsonArray -> element
                else -> null
            } ?: return emptyList()
            items.mapNotNull { candidate ->
                val obj = candidate as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                ProviderModelInfo(
                    id = id,
                    owner = (obj["owned_by"] as? JsonPrimitive)?.content,
                    createdAt = (obj["created"] as? JsonPrimitive)?.content?.toLongOrNull()
                )
            }
        }.getOrElse {
            AppLog.w("OpenAI", "models payload not parseable")
            emptyList()
        }
    }

    companion object {
        const val KEY_REACH = "reach"
        const val KEY_AUTH = "auth"
        const val KEY_MODELS = "models"
        private const val PROBE_MODEL = "__sokhan_probe__"
    }
}

/** OkHttp `Call` coroutine bridge with proper cancellation on scope cancel. */
internal suspend fun Call.await(): Pair<Int, String?> =
    suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.context.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = runCatching { response.body?.string() }.getOrNull()
                continuation.resume(Pair(response.code, body))
                response.close()
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }
