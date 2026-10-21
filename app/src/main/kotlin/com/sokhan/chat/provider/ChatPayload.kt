package com.sokhan.chat.provider

/**
 * Provider-agnostic chat payload. Every provider client maps this to its own
 * wire format; the UI and engine never see provider specifics.
 */
data class ChatPayload(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    /** Send as SSE stream. */
    val stream: Boolean = true,
    /** OpenAI-only: stream_options.include_usage to receive token stats. */
    val includeUsage: Boolean = false
)

enum class ChatMessageRole { SYSTEM, USER, ASSISTANT }

/** Data URLs for images are "data:<mime>;base64,<bytes>" per the OpenAI schema. */
data class PdfPayload(val name: String, val dataUrl: String)

data class ChatMessage(
    val role: ChatMessageRole,
    val text: String? = null,
    val images: List<String> = emptyList(),
    val pdfs: List<PdfPayload> = emptyList()
)

/** Token stats returned by the provider when supported. */
data class UsageInfo(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

/**
 * Streaming events. Collectors receive deltas in order and exactly one
 * terminal [Finished], after which the flow closes (completes).
 */
sealed class ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent()
    /** Emitted for reasoning-only models (e.g. DeepSeek `reasoning_content`). */
    data class ReasoningDelta(val text: String) : ChatStreamEvent()
    data class Usage(val usage: UsageInfo) : ChatStreamEvent()
    data class Finished(val finishReason: String? = null) : ChatStreamEvent()
}
