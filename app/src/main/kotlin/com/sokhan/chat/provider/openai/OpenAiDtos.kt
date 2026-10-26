package com.sokhan.chat.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.sokhan.chat.domain.agent.AgentTool
import com.sokhan.chat.provider.ChatMessage
import com.sokhan.chat.provider.ChatMessageRole
import com.sokhan.chat.provider.ToolCallRequest

/** ── Outgoing request body (OpenAI Chat Completions schema) ───────────── */

@Serializable
internal data class OpenAiChatRequestBody(
    val model: String,
    val messages: List<OpenAiWireMessage>,
    val stream: Boolean = false,
    @SerialName("stream_options") val streamOptions: OpenAiStreamOptions? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    /** Omitted entirely when null so plain chats stay byte-identical. */
    val tools: List<OpenAiTool>? = null
)

@Serializable
internal data class OpenAiStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

/** One entry of the `tools` array (function-calling). */
@Serializable
internal data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiToolFunction
)

@Serializable
internal data class OpenAiToolFunction(
    val name: String,
    val description: String,
    /** JSON-Schema object, passed through verbatim. */
    val parameters: JsonObject
)

/** Maps the app's tool descriptors to the wire `tools` array. */
internal fun List<AgentTool>.toWireTools(): List<OpenAiTool> = map { tool ->
    OpenAiTool(
        function = OpenAiToolFunction(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters
        )
    )
}

@Serializable
internal data class OpenAiWireMessage(
    val role: String,
    /** String for text-only messages; array of typed parts for multimodal ones. */
    val content: JsonElement,
    /** ASSISTANT messages: the tool calls made in this turn. */
    @SerialName("tool_calls") val toolCalls: List<OpenAiWireToolCall>? = null,
    /** TOOL messages: which call this result answers. */
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
internal data class OpenAiWireToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiWireToolFunction
)

@Serializable
internal data class OpenAiWireToolFunction(
    val name: String,
    /** JSON arguments as a string — the OpenAI wire format requires this. */
    val arguments: String
)

internal fun List<ToolCallRequest>.toWireToolCalls(): List<OpenAiWireToolCall> = map { call ->
    OpenAiWireToolCall(
        id = call.id,
        function = OpenAiWireToolFunction(
            name = call.name,
            arguments = call.argumentsJson
        )
    )
}

/**
 * Builds the wire `content` value: plain string when the message is
 * text-only (best compatibility with older OpenAI-compatible servers),
 * otherwise the parts array with image_url / file blocks.
 */
internal fun ChatMessage.toWireMessage(): OpenAiWireMessage {
    val wireRole = when (role) {
        ChatMessageRole.SYSTEM -> "system"
        ChatMessageRole.USER -> "user"
        ChatMessageRole.ASSISTANT -> "assistant"
        ChatMessageRole.TOOL -> "tool"
    }
    val content: JsonElement = if (images.isEmpty() && pdfs.isEmpty()) {
        JsonPrimitive(text ?: "")
    } else {
        buildJsonArray {
            if (!text.isNullOrBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            }
            images.forEach { url ->
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", url) })
                })
            }
            pdfs.forEach { pdf ->
                add(buildJsonObject {
                    put("type", "file")
                    put("file", buildJsonObject {
                        put("filename", pdf.name)
                        put("file_data", pdf.dataUrl)
                    })
                })
            }
        }
    }
    return OpenAiWireMessage(
        role = wireRole,
        content = content,
        toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.toWireToolCalls(),
        toolCallId = toolCallId
    )
}

/** ── Non-streaming response ──────────────────────────────────────────── */

@Serializable
internal data class OpenAiCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<OpenAiChoice>? = null,
    val usage: OpenAiUsage? = null
)

@Serializable
internal data class OpenAiChoice(
    val index: Int? = null,
    val message: OpenAiChoiceMessage? = null,
    /** string ("stop") or null — JsonPrimitive absorbs both encodings. */
    @SerialName("finish_reason") val finishReason: JsonPrimitive? = null
)

@Serializable
internal data class OpenAiChoiceMessage(
    val role: String? = null,
    val content: String? = null,
    /**
     * `reasoning` (OpenRouter style) and `reasoning_content` (DeepSeek style)
     * are both accepted.
     */
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    /** Non-streaming tool calls (present when finish_reason == "tool_calls"). */
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null
)

@Serializable
internal data class OpenAiToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiToolCallFunction? = null
)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

/** ── Streaming chunks ────────────────────────────────────────────────── */

@Serializable
internal data class OpenAiStreamChunk(
    val id: String? = null,
    val choices: List<OpenAiStreamChoice>? = null,
    val usage: OpenAiUsage? = null
)

@Serializable
internal data class OpenAiStreamChoice(
    val index: Int? = null,
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason") val finishReason: JsonPrimitive? = null
)

@Serializable
internal data class OpenAiStreamDelta(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    /**
     * Tool calls stream as indexed fragments: `id`/`name` arrive in the
     * first chunk of each index, `arguments` accumulates afterwards.
     */
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null
)

@Serializable
internal data class OpenAiToolCallDelta(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiToolCallFunction? = null
)

@Serializable
internal data class OpenAiToolCallFunction(
    val name: String? = null,
    val arguments: String? = null
)

