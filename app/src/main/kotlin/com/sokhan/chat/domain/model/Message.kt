package com.sokhan.chat.domain.model

/** Roles supported by the OpenAI-compatible chat schema. */
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

/** Lifecycle of one stored message row. */
enum class MessageStatus { SUCCESS, STREAMING, ERROR, PARTIAL }

/** Token usage as reported by the provider (`usage` object). Null when unknown. */
data class TokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

/**
 * One chat message. Attachment relations live in their own table and are
 * joined by the repository when needed (keeps this class light for lists).
 */
data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String? = null,
    val createdAt: Long,
    val updatedAt: Long? = null,
    val status: MessageStatus = MessageStatus.SUCCESS,
    val modelId: String? = null,
    val providerId: String? = null,
    val usage: TokenUsage? = null,
    val finishReason: String? = null,
    val errorKind: String? = null,
    val errorDetail: String? = null,
    /**
     * JSON array of the tool calls this assistant message made
     * (`[{"id","name","arguments"}]`), null for plain messages.
     */
    val toolCalls: String? = null,
    /** On TOOL messages: the id of the call this result answers. */
    val toolCallId: String? = null,
    /** On TOOL messages: which tool produced this row (e.g. `set_theme`). */
    val toolName: String? = null
)
