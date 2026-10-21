package com.sokhan.chat.ui.screens.assistants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.service.AssistantChatStarter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantsViewModel(
    private val assistants: AssistantRepository,
    private val chatStarter: AssistantChatStarter
) : ViewModel() {

    val list: StateFlow<List<Assistant>> = assistants.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun duplicate(source: Assistant) {
        viewModelScope.launch {
            assistants.upsert(source.copy(id = Ids.newId(), name = source.name + " (copy)"))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { assistants.delete(id) }
    }

    fun startChat(assistant: Assistant, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            onStarted(chatStarter.start(assistant))
        }
    }
}
