package com.sokhan.chat.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.repository.ChatEngine
import com.sokhan.chat.domain.repository.ConversationCardSnapshot
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatsViewModel(
    private val conversations: ConversationRepository,
    private val providers: ProviderRepository,
    private val settings: SettingsRepository,
    private val engine: ChatEngine
) : ViewModel() {

    private val search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = search.asStateFlow()

    private val archivedOnly = MutableStateFlow(false)

    val showArchived: StateFlow<Boolean> = archivedOnly.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<ConversationCardSnapshot>> = combine(
        search, archivedOnly
    ) { query, archived -> query to archived }
        .flatMapLatest { (query, archived) ->
            conversations.observeCards(
                archived = archived,
                search = query.ifBlank { null }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val hasAnyProvider: StateFlow<Boolean> = providers.observeAll()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val streamState = engine.streamState

    fun setSearch(value: String) {
        search.value = value
    }

    fun setShowArchived(value: Boolean) {
        archivedOnly.value = value
    }

    /**
     * Creates an empty conversation row and reports the id back so the UI can
     * navigate. Title stays blank until the first message ("draft title").
     */
    fun newConversation(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val snapshot = settings.settings.value
            val providerId = snapshot.defaultProviderId ?: providers.defaultProviderId()
            val modelId = snapshot.defaultModelId
                ?: providerId?.let { providers.resolve(it)?.config?.defaultModelId }
            val now = System.currentTimeMillis()
            val conversation = Conversation(
                id = Ids.newId(),
                title = "",
                createdAt = now,
                updatedAt = now,
                providerId = providerId,
                modelId = modelId,
                modelLabel = null
            )
            conversations.upsert(conversation)
            onCreated(conversation.id)
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { conversations.rename(id, title) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            engine.stop(id)
            conversations.delete(id)
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { conversations.setPinned(id, pinned) }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch { conversations.setArchived(id, archived) }
    }
}
