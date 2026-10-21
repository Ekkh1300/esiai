package com.sokhan.chat.domain.model

/** A conversation (chat thread). Settings are a snapshot copied when created. */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val systemPrompt: String? = null,
    /** null ⇒ fall back to settings/system default. */
    val providerId: String? = null,
    /** Provider-external model id, e.g. "gpt-4o-mini". */
    val modelId: String? = null,
    /** Frozen display label so old chats look right even if a provider is edited/deleted. */
    val modelLabel: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    /** null ⇒ follow the global default. */
    val streaming: Boolean? = null,
    val assistantId: String? = null
)
