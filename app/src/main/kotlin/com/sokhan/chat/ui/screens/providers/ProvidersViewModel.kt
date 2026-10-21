package com.sokhan.chat.ui.screens.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.model.ProviderKind
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProvidersViewModel(
    private val providers: ProviderRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val all: StateFlow<List<ProviderConfig>> = providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDefault(id: String) {
        viewModelScope.launch { providers.setDefaultProvider(id) }
    }

    fun duplicate(id: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { providers.duplicate(id) }.getOrNull())
        }
    }

    fun delete(provider: ProviderConfig) {
        viewModelScope.launch {
            providers.delete(provider.id)
            // If the deleted provider was the configured default, drop the setting.
            if (settings.settings.value.defaultProviderId == provider.id) {
                settings.update { it.copy(defaultProviderId = null, defaultModelId = null) }
            }
        }
    }

    fun newProviderDraftName(): String = ""
}
