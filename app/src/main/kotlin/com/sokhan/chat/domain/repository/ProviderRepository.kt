package com.sokhan.chat.domain.repository

import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.model.ProviderKind
import kotlinx.coroutines.flow.Flow

/**
 * Resolved provider with a decrypted key from the [com.sokhan.chat.security.SecureStore].
 */
data class ResolvedProvider(
    val config: ProviderConfig,
    val apiKey: String?
)

interface ProviderRepository {
    fun observeAll(): Flow<List<ProviderConfig>>
    suspend fun get(id: String): ProviderConfig?

    /** Fresh read with the decrypted api key. */
    suspend fun resolve(id: String): ResolvedProvider?
    suspend fun resolveDefault(): ResolvedProvider?
    suspend fun defaultProviderId(): String?
    suspend fun setDefaultProvider(id: String)
    suspend fun upsert(
        config: ProviderConfig,
        /** New API key or null to keep the stored one (never returned back). */
        newApiKey: String? = null,
        clearApiKey: Boolean = false
    )

    suspend fun delete(id: String)
    suspend fun duplicate(id: String): String
    suspend fun markValidated(id: String, at: Long)
    suspend fun clearAll()
    suspend fun count(): Int
}

interface ModelRepository {
    fun observeForProvider(providerId: String): Flow<List<ChatModel>>
    fun observeVisibleForProvider(providerId: String): Flow<List<ChatModel>>
    suspend fun upsert(model: ChatModel)
    suspend fun upsertAll(models: List<ChatModel>)
    suspend fun delete(modelDb: ChatModel)
    suspend fun setHidden(providerId: String, modelId: String, hidden: Boolean)
    suspend fun updateCapabilities(modelDb: ChatModel)
    suspend fun get(providerId: String, modelId: String): ChatModel?
    suspend fun anyForProvider(providerId: String): List<ChatModel>
    suspend fun clearForProvider(providerId: String)
}

interface AssistantRepository {
    fun observeAll(): Flow<List<Assistant>>
    suspend fun get(id: String): Assistant?
    suspend fun upsert(assistant: Assistant): Assistant
    suspend fun delete(id: String)
    suspend fun clear()
    suspend fun count(): Int
    suspend fun seedIfEmpty(seed: AssistantSeed? = null)
}

/** Payload granting the repository access to localized seed strings. */
data class AssistantSeed(
    val generalName: String,
    val generalPrompt: String,
    val coderName: String,
    val coderPrompt: String,
    val translatorName: String,
    val translatorPrompt: String,
    val researcherName: String,
    val researcherPrompt: String
)
