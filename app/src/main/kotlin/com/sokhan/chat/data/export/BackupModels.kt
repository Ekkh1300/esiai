package com.sokhan.chat.data.export

import kotlinx.serialization.Serializable

/**
 * Backup models (Schema v1). Export intentionally mirrors messages verbatim
 * (including provider/model labels) so they can be re-imported on another
 * device without that provider being configured.
 */
const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val systemPrompt: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val streaming: Boolean? = null,
    val assistantId: String? = null,
    val messages: List<BackupMessage> = emptyList()
)

@Serializable
data class BackupMessage(
    val id: String,
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val createdAt: Long,
    val status: String? = null,
    val modelId: String? = null,
    val providerId: String? = null,
    val usagePromptTokens: Int? = null,
    val usageCompletionTokens: Int? = null,
    val usageTotalTokens: Int? = null,
    val finishReason: String? = null
)

@Serializable
data class BackupAssistant(
    val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val streaming: Boolean? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupProvider(
    val id: String,
    val name: String,
    val kind: String = "OPENAI_COMPATIBLE",
    val baseUrl: String,
    val organization: String? = null,
    val apiVersion: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val defaultModelId: String? = null,
    val includeUsageInStream: Boolean = false
)

@Serializable
data class BackupBundle(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long,
    val appName: String = "Sokhan",
    val conversations: List<BackupConversation> = emptyList(),
    val assistants: List<BackupAssistant> = emptyList(),
    val providers: List<BackupProvider> = emptyList(),
    /** Encrypted-at-rest keys are excluded from exports by default. */
    val providerKeys: Map<String, String> = emptyMap()
)

@Serializable
data class ImportSummary(
    val conversationsAdded: Int,
    val conversationsMerged: Int,
    val assistantsAdded: Int,
    val assistantsMerged: Int,
    val providersAdded: Int,
    val providersMerged: Int
) {
    fun describe(): String =
        "$conversationsAdded conversations ($conversationsMerged merged), $assistantsAdded assistants, $providersAdded providers"
}
