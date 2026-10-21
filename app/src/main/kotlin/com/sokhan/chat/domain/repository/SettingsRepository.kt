package com.sokhan.chat.domain.repository

import com.sokhan.chat.domain.model.SettingsSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Read/write access to application settings. Backed by Preferences DataStore;
 * exposed as a [StateFlow] so theme/language/compose settings apply live.
 */
interface SettingsRepository {
    val settings: StateFlow<SettingsSnapshot>
    suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot)
    suspend fun setLanguage(languageKey: String)
    suspend fun setThemeMode(value: String)

    /** Restores factory defaults (used by "clear all data"). */
    suspend fun reset()
}
