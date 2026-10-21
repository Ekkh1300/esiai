package com.sokhan.chat.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.sokhan.chat.provider.ChatMessage
import com.sokhan.chat.provider.ChatMessageRole

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
    @SerialName("presence_penalty") val presencePenalty: Double? = null
)

@Serializable
internal data class OpenAiStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

@Serializable
internal data class OpenAiWireMessage(
    val role: String,
    /** String for text-only messages; array of typed parts for multimodal ones. */
    val content: JsonElement
)

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
    return OpenAiWireMessage(role = wireRole, content = content)
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
    @SerialName("reasoning_content") val reasoningContent: String? = null
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
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

