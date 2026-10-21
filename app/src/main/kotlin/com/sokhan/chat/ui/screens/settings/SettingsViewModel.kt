package com.sokhan.chat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.data.export.BackupService
import com.sokhan.chat.data.export.ExportFormatter
import com.sokhan.chat.domain.model.SettingsSnapshot
import com.sokhan.chat.domain.model.ThemeMode
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.ChatEngine
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupService,
    private val conversations: ConversationRepository,
    private val assistants: AssistantRepository,
    private val providers: ProviderRepository,
    private val engine: ChatEngine
) : ViewModel() {

    val snapshot: StateFlow<SettingsSnapshot> = settings.settings

    fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun setLanguage(key: String) {
        viewModelScope.launch { settings.setLanguage(key) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode.name) }
    }

    fun setDynamicColors(enabled: Boolean) {
        update { it.copy(dynamicColors = enabled) }
    }

    /** Exports the backup JSON; called from a file-creation launcher. */
    suspend fun exportJson(includeKeys: Boolean): String = backup.export(includeKeys)

    /** Imports a backup from the given JSON payload. */
    suspend fun importJson(payload: String) = backup.import(payload)

    /** Markdown export of a single conversation via the launcher flow. */
    fun exportConversationMarkdown(
        conversationId: String,
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val conversation = conversations.get(conversationId) ?: run {
                onReady("")
                return@launch
            }
            val messages = conversations.observeMessages(conversationId).firstOrNull().orEmpty()
            onReady(ExportFormatter.toMarkdown(conversation, messages))
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                engine.stopAll()
                conversations.deleteAllConversations()
                assistants.clear()
                providers.clearAll()
                settings.reset()
            }
            onDone()
        }
    }
}
