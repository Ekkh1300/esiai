package com.sokhan.chat.domain.model

/** A reusable persona stored and copied into conversations on creation. */
data class Assistant(
    val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    /** null ⇒ use app default provider/model. */
    val providerId: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val streaming: Boolean? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** The four personas Sokhan seeds on first run (all user-editable/deletable). */
object BundledAssistants {
    const val GENERAL_KEY = "sokhan.builtin.general"
    const val CODER_KEY = "sokhan.builtin.coder"
    const val TRANSLATOR_KEY = "sokhan.builtin.translator"
    const val RESEARCH_KEY = "sokhan.builtin.research"
    const val GENERAL_EMOJI = "💬"
    const val CODER_EMOJI = "🧑‍💻"
    const val TRANSLATOR_EMOJI = "🌐"
    const val RESEARCH_EMOJI = "🔍"
}
