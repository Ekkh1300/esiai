package com.sokhan.chat.domain.model

/**
 * Application settings snapshot (immutable). Persisted via Preferences
 * DataStore; the repository exposes [StateFlow]s so changes recompose live.
 */
data class SettingsSnapshot(
    /** "system", "en", "fa" */
    val language: String = "system",
    /** "system", "light", "dark" */
    val themeMode: String = "system",
    val dynamicColors: Boolean = true,
    val defaultProviderId: String? = null,
    val defaultModelId: String? = null,
    val defaultSystemPrompt: String? = null,
    val defaultStreaming: Boolean = true,
    val defaultTemperature: Double? = null,
    val defaultMaxTokens: Int? = null,
    val markdownEnabled: Boolean = true,
    val codeHighlighting: Boolean = true,
    val autoScroll: Boolean = true,
    val enterSends: Boolean = false,
    val onboardingSeen: Boolean = false
)

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(value: String): ThemeMode =
            entries.firstOrNull { it.key == value } ?: SYSTEM
    }
}
