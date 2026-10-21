package com.sokhan.chat.ui.screens.assistants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.service.AssistantChatStarter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

data class AssistantEditorState(
    val assistantId: String? = null,
    val name: String = "",
    val emoji: String = "💬",
    val systemPrompt: String = "",
    val providerId: String? = null,
    val modelId: String? = null,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val streaming: Boolean = true,
    val nameError: Boolean = false,
    val saved: Boolean = false
)

class AssistantEditorViewModel(
    private val assistantId: String?,
    private val assistants: AssistantRepository,
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val chatStarter: AssistantChatStarter
) : ViewModel() {

    private val state = MutableStateFlow(AssistantEditorState())
    val editor: StateFlow<AssistantEditorState> = state.asStateFlow()

    val providersList: StateFlow<List<ProviderConfig>> = providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val modelList = MutableStateFlow<List<ChatModel>>(emptyList())
    val modelRows: StateFlow<List<ChatModel>> = modelList.asStateFlow()

    init {
        viewModelScope.launch {
            if (assistantId == null) {
                val providerId = providers.defaultProviderId()
                state.value = AssistantEditorState(providerId = providerId)
                refreshModels(providerId)
            } else {
                assistants.get(assistantId)?.let { assistant ->
                    state.value = AssistantEditorState(
                        assistantId = assistant.id,
                        name = assistant.name,
                        emoji = assistant.emoji,
                        systemPrompt = assistant.systemPrompt,
                        providerId = assistant.providerId ?: providers.defaultProviderId(),
                        modelId = assistant.modelId,
                        temperature = assistant.temperature ?: 0.7,
                        maxTokens = assistant.maxTokens,
                        streaming = assistant.streaming ?: true
                    )
                    refreshModels(state.value.providerId)
                }
            }
        }
    }

    fun update(transform: (AssistantEditorState) -> AssistantEditorState) {
        state.value = transform(state.value)
    }

    fun onProviderSelected(providerId: String?) {
        state.value = state.value.copy(providerId = providerId, modelId = null)
        viewModelScope.launch { refreshModels(providerId) }
    }

    private suspend fun refreshModels(providerId: String?) {
        modelList.value = providerId?.let { models.anyForProvider(it) }.orEmpty()
    }

    fun save() {
        viewModelScope.launch { saveInternal() }
    }

    fun saveAndStartChat(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val assistant = saveInternal()
            if (assistant != null) {
                onStarted(chatStarter.start(assistant))
            }
        }
    }

    fun delete(assistantId: String) {
        viewModelScope.launch { assistants.delete(assistantId) }
    }

    private suspend fun saveInternal(): Assistant? {
        val current = state.value
        if (current.name.isBlank() || current.systemPrompt.isBlank()) {
            state.value = current.copy(nameError = true, saved = false)
            return null
        }
        val now = System.currentTimeMillis()
        val existing = current.assistantId?.let { assistants.get(it) }
        val assistant = Assistant(
            id = current.assistantId ?: Ids.newId(),
            name = current.name.trim(),
            emoji = current.emoji.ifBlank { "💬" },
            systemPrompt = current.systemPrompt.trim(),
            providerId = current.providerId,
            modelId = current.modelId,
            modelLabel = null,
            temperature = min(2.0, max(0.0, current.temperature)),
            maxTokens = current.maxTokens,
            streaming = current.streaming,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        assistants.upsert(assistant)
        state.value = current.copy(
            assistantId = assistant.id,
            nameError = false,
            saved = true
        )
        return assistant
    }
}
