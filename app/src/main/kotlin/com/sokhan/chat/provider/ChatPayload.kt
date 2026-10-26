package com.sokhan.chat.provider

import com.sokhan.chat.domain.agent.AgentTool

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
    val includeUsage: Boolean = false,
    /**
     * Tools the model may call (agent mode). Empty → the request carries no
     * `tools` array and behaves exactly like a plain chat completion.
     */
    val tools: List<AgentTool> = emptyList()
)

enum class ChatMessageRole { SYSTEM, USER, ASSISTANT, TOOL }

/** Data URLs for images are "data:<mime>;base64,<bytes>" per the OpenAI schema. */
data class PdfPayload(val name: String, val dataUrl: String)

/**
 * A tool call the assistant made in an earlier turn, echoed back so the
 * provider can pair it with the [ChatMessageRole.TOOL] result that follows.
 */
data class ToolCallRequest(
    val id: String,
    val name: String,
    /** Raw JSON arguments string, exactly as the model produced them. */
    val argumentsJson: String
)

data class ChatMessage(
    val role: ChatMessageRole,
    val text: String? = null,
    val images: List<String> = emptyList(),
    val pdfs: List<PdfPayload> = emptyList(),
    /** Set on TOOL messages: which call this result answers. */
    val toolCallId: String? = null,
    /** Set on ASSISTANT messages that requested tool calls. */
    val toolCalls: List<ToolCallRequest> = emptyList()
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
    /**
     * A fragment of a tool call. [argumentsDelta] is appended per [index]
     * until `finish_reason` arrives — the accumulated result is a JSON object.
     */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String
    ) : ChatStreamEvent()

    data class Usage(val usage: UsageInfo) : ChatStreamEvent()
    data class Finished(val finishReason: String? = null) : ChatStreamEvent()
}
