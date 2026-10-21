package com.sokhan.chat.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sokhan.chat.domain.model.SettingsSnapshot
import com.sokhan.chat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

private object SettingsDataStoreHolder

/**
 * Preferences DataStore implementation of [SettingsRepository].
 */
class SettingsStore(
    context: Context,
    scope: CoroutineScope
) : SettingsRepository {

    private val appContext = context.applicationContext

    private val dataStore: DataStore<Preferences> by lazy {
        with(SettingsDataStoreHolder) {
            appContext.settingsPreferences
        }
    }

    override val settings: StateFlow<SettingsSnapshot> = dataStore.data
        .map { it.toSnapshot() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsSnapshot()
        )

    override suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        val current = dataStore.data.first().toSnapshot()
        val next = transform(current)
        dataStore.edit { prefs ->
            with(next) {
                prefs[KEY_LANGUAGE] = language
                prefs[KEY_THEME] = themeMode
                prefs[KEY_DYNAMIC_COLORS] = dynamicColors
                defaultProviderId?.let { prefs[KEY_DEFAULT_PROVIDER] = it } ?: prefs.remove(KEY_DEFAULT_PROVIDER)
                defaultModelId?.let { prefs[KEY_DEFAULT_MODEL] = it } ?: prefs.remove(KEY_DEFAULT_MODEL)
                defaultSystemPrompt?.let { prefs[KEY_DEFAULT_SYSTEM_PROMPT] = it } ?: prefs.remove(KEY_DEFAULT_SYSTEM_PROMPT)
                prefs[KEY_DEFAULT_STREAMING] = defaultStreaming
                defaultTemperature?.let { prefs[KEY_DEFAULT_TEMPERATURE] = it } ?: prefs.remove(KEY_DEFAULT_TEMPERATURE)
                defaultMaxTokens?.let { prefs[KEY_DEFAULT_MAX_TOKENS] = it } ?: prefs.remove(KEY_DEFAULT_MAX_TOKENS)
                prefs[KEY_MARKDOWN] = markdownEnabled
                prefs[KEY_HIGHLIGHTING] = codeHighlighting
                prefs[KEY_AUTO_SCROLL] = autoScroll
                prefs[KEY_ENTER_SENDS] = enterSends
                prefs[KEY_ONBOARDING] = onboardingSeen
            }
        }
    }

    override suspend fun setLanguage(languageKey: String) {
        update { it.copy(language = languageKey) }
    }

    override suspend fun setThemeMode(value: String) {
        update { it.copy(themeMode = value) }
    }

    override suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun Preferences.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
        language = this[KEY_LANGUAGE] ?: "system",
        themeMode = this[KEY_THEME] ?: "system",
        dynamicColors = this[KEY_DYNAMIC_COLORS] ?: true,
        defaultProviderId = this[KEY_DEFAULT_PROVIDER],
        defaultModelId = this[KEY_DEFAULT_MODEL],
        defaultSystemPrompt = this[KEY_DEFAULT_SYSTEM_PROMPT],
        defaultStreaming = this[KEY_DEFAULT_STREAMING] ?: true,
        defaultTemperature = this[KEY_DEFAULT_TEMPERATURE],
        defaultMaxTokens = this[KEY_DEFAULT_MAX_TOKENS],
        markdownEnabled = this[KEY_MARKDOWN] ?: true,
        codeHighlighting = this[KEY_HIGHLIGHTING] ?: true,
        autoScroll = this[KEY_AUTO_SCROLL] ?: true,
        enterSends = this[KEY_ENTER_SENDS] ?: false,
        onboardingSeen = this[KEY_ONBOARDING] ?: false
    )

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val KEY_DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
        val KEY_DEFAULT_SYSTEM_PROMPT = stringPreferencesKey("default_system_prompt")
        val KEY_DEFAULT_STREAMING = booleanPreferencesKey("default_streaming")
        val KEY_DEFAULT_TEMPERATURE = doublePreferencesKey("default_temperature")
        val KEY_DEFAULT_MAX_TOKENS = intPreferencesKey("default_max_tokens")
        val KEY_MARKDOWN = booleanPreferencesKey("markdown_enabled")
        val KEY_HIGHLIGHTING = booleanPreferencesKey("code_highlighting")
        val KEY_AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val KEY_ENTER_SENDS = booleanPreferencesKey("enter_sends")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_seen")

        private val Context.settingsPreferences: DataStore<Preferences>
            by preferencesDataStore(name = "sokhan_settings")
    }
}
