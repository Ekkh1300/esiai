package com.sokhan.chat.domain.model

/**
 * Supports a single wire protocol kind today (OpenAI-compatible); sealed for
 * future provider families (Anthropic, Gemini, …).
 */
enum class ProviderKind { OPENAI_COMPATIBLE }

/** Provider-level capability switches the user can toggle. */
data class ProviderCapabilities(
    val textFiles: Boolean = true,
    val pdfFiles: Boolean = false,
    val vision: Boolean = true,
    val listModels: Boolean = true
) {
    fun normalized(): ProviderCapabilities = this
}

/**
 * A configured provider. The API key is NEVER stored here — it lives in the
 * [com.sokhan.chat.security.SecureStore], keyed by [id].
 */
data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val organization: String? = null,
    val apiVersion: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val defaultModelId: String? = null,
    val capabilities: ProviderCapabilities = ProviderCapabilities(),
    /** Generation defaults applied to new chats. */
    val defaultTemperature: Double? = null,
    val defaultMaxTokens: Int? = null,
    val defaultTopP: Double? = null,
    val defaultStreaming: Boolean? = null,
    val includeUsageInStream: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastValidatedAt: Long? = null
)

/** One model row fetched from the provider (or added manually). */
data class ModelCapabilities(
    val vision: Boolean = false,
    val tools: Boolean = false,
    val streaming: Boolean = true,
    val textFiles: Boolean = false,
    val pdfFiles: Boolean = false,
    val structured: Boolean = false,
    val reasoning: Boolean = false
)

data class ChatModel(
    val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String? = null,
    val contextLength: Int? = null,
    /** Fetched via API ⇒ provider managed; added by the user ⇒ deletable. */
    val userDefined: Boolean = false,
    /** Hidden models are kept but not offered in pickers. */
    val hidden: Boolean = false,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val createdAt: Long = 0L
)
