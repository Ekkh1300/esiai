package com.sokhan.chat.provider

import com.sokhan.chat.domain.model.ChatError
import com.sokhan.chat.domain.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

/**
 * Provider call-bundle: configuration plus the decrypted API key. Built by the
 * repository per call — the key is not cached, never logged.
 */
data class ProviderContext(
    val config: ProviderConfig,
    val apiKey: String?
)

/** A model discovered via the provider's list endpoint. */
data class ProviderModelInfo(
    val id: String,
    val owner: String? = null,
    val createdAt: Long? = null
)

/** Steps of the "Test connection" flow, surfaced one by one in the UI. */
data class ConnectionTestStep(
    val stepKey: String,
    val passed: Boolean,
    val detail: String? = null
)

data class ConnectionTestResult(
    val steps: List<ConnectionTestStep>,
    /** Models found when the endpoint supported listing. */
    val models: List<ProviderModelInfo> = emptyList(),
    val failure: ChatError? = null
)

/**
 * THE provider abstraction (requirement: "Sokhan must not hardcode OpenAI").
 *
 * Adding a provider family later means implementing this interface and
 * registering it in a [ProviderClientFactory]; UI, engine and schema layers
 * remain untouched.
 */
interface AiProviderClient {
    /**
     * Fetches the provider's model list when the endpoint supports it.
     * Returns failure with a typed [ChatError] otherwise.
     */
    suspend fun listModels(context: ProviderContext): Result<List<ProviderModelInfo>>

    /**
     * Streams (or, when [ChatPayload.stream] is false, resolves in one step)
     * an assistant completion. Typed failures are delivered with
     * `Result.failure` semantics — clients never throw across the boundary.
     */
    fun chatPayload(
        payload: ChatPayload,
        context: ProviderContext
    ): Flow<Result<ChatStreamEvent>>

    /** Runs the step-by-step connection test used by the provider editor. */
    suspend fun testConnection(context: ProviderContext): ConnectionTestResult
}
