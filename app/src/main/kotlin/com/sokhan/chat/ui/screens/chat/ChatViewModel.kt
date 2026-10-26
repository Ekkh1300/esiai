package com.sokhan.chat.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.Attachment
import com.sokhan.chat.domain.model.ChatError
import com.sokhan.chat.domain.model.ChatErrorKind
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.model.SettingsSnapshot
import com.sokhan.chat.domain.repository.AttachmentRepository
import com.sokhan.chat.domain.repository.ChatEngine
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.PendingToolAction
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import java.io.File

class ChatViewModel(
    private val conversationId: String,
    private val conversations: ConversationRepository,
    private val engine: ChatEngine,
    private val models: ModelRepository,
    private val providers: ProviderRepository,
    private val attachments: AttachmentRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val conversation: StateFlow<Conversation?> = conversations.observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<Message>> = conversations.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val attachmentsByMessage: StateFlow<Map<String, List<Attachment>>> =
        attachments.observeForConversation(conversationId)
            .map { list -> list.filter { it.messageId != null }.groupBy { it.messageId!! } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val appSettings: StateFlow<SettingsSnapshot> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsSnapshot())

    val streamState = engine.streamState

    /** Tool calls this conversation is waiting to run (agent mode). */
    val pendingToolActions: StateFlow<List<PendingToolAction>> = engine.pendingToolActions
        .map { list -> list.filter { it.conversationId == conversationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Approves or declines the pending tool call shown in the dialog. */
    fun respondToTool(callId: String, approved: Boolean) {
        engine.respondToToolAction(callId, approved)
    }

    /** Drafts attached to the composer, not yet sent. */
    private val drafts = MutableStateFlow<List<Attachment>>(emptyList())
    val draftAttachments: StateFlow<List<Attachment>> = drafts.asStateFlow()

    private val editing = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = editing.asStateFlow()

    /** Transient startup failure surfaced by [generate] entry points. */
    private val startupErrorFlow = MutableStateFlow<ChatError?>(null)
    val startupError: StateFlow<ChatError?> = startupErrorFlow.asStateFlow()

    /** Attachment pipeline failure (FILE_TOO_LARGE / UNREADABLE). */
    private val attachmentFailure = MutableStateFlow<String?>(null)
    val attachmentProblem: StateFlow<String?> = attachmentFailure.asStateFlow()

    /** Models of the currently selected provider, for the picker sheet. */
    private val providerModels = MutableStateFlow<List<ChatModel>>(emptyList())
    val availableModels: StateFlow<List<ChatModel>> = providerModels.asStateFlow()

    val providersList: StateFlow<List<ProviderConfig>> = providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** attachment id → resolved private file (images in bubbles + drafts). */
    val filesByAttachment: StateFlow<Map<String, File>> = combine(
        attachmentsByMessage, drafts
    ) { grouped, drafts ->
        (grouped.values.flatten() + drafts)
            .mapNotNull { attachment ->
                attachments.attachmentFile(attachment)?.let { attachment.id to it }
            }
            .toMap()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    fun acknowledgeStartupError() {
        startupErrorFlow.value = null
    }

    fun acknowledgeAttachmentProblem() {
        attachmentFailure.value = null
    }

    init {
        observeProviderModels()
        // Mark onboarding: first chat opened clears the "no provider" guidance state
        viewModelScope.launch { settings.update { it.copy(onboardingSeen = true) } }
    }

    private fun observeProviderModels() {
        viewModelScope.launch {
            conversation.collect { convo ->
                val providerId = convo?.providerId
                    ?: providers.defaultProviderId()
                if (providerId != null) {
                    providerModels.value = models.anyForProvider(providerId)
                        .filter { !it.hidden }
                } else {
                    providerModels.value = emptyList()
                }
            }
        }
    }

    /**
     * The model sheet lets the user browse another provider's models before
     * committing; remember which provider that sheet is currently showing.
     */
    private val sheetProvider = MutableStateFlow<String?>(null)
    val sheetProviderId: StateFlow<String?> = sheetProvider.asStateFlow()

    fun pickProvider(providerId: String) {
        sheetProvider.value = providerId
        viewModelScope.launch {
            providerModels.value = models.anyForProvider(providerId).filter { !it.hidden }
        }
    }

    // ── Composer plumbing ────────────────────────────────────────────────

    fun addAttachment(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            attachments.createFromContentUri(uri, displayName ?: "attachment")
                .fold(
                    onSuccess = { attachment ->
                        attachmentFailure.value = null
                        drafts.value = drafts.value + attachment
                    },
                    onFailure = { failure ->
                        attachmentFailure.value = failure.message ?: "UNREADABLE"
                    }
                )
        }
    }

    fun removeDraft(attachment: Attachment) {
        viewModelScope.launch {
            attachments.delete(attachment.id)
            drafts.value = drafts.value.filterNot { it.id == attachment.id }
        }
    }

    // ── Sending / streaming ──────────────────────────────────────────────

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && drafts.value.isEmpty()) return
        viewModelScope.launch {
            editing.value?.let { edit ->
                editing.value = null
                startupErrorFlow.value = engine.editAndResend(conversationId, edit.id, trimmed)
                return@launch
            }
            val current = conversation.value
            val message = Message(
                id = Ids.newId(),
                conversationId = conversationId,
                role = MessageRole.USER,
                content = trimmed,
                createdAt = System.currentTimeMillis(),
                status = MessageStatus.SUCCESS
            )
            conversations.insertUserMessageWithAttachments(
                message,
                drafts.value.map { it.id }
            )
            drafts.value = emptyList()
            if (current != null && current.title.isBlank()) {
                conversations.rename(conversationId, trimmed.take(60).ifBlank { "…" })
            }
            startupErrorFlow.value = engine.generate(conversationId)
        }
    }

    fun stop() {
        engine.stop(conversationId)
    }

    fun regenerate() {
        viewModelScope.launch {
            startupErrorFlow.value = engine.regenerate(conversationId)
        }
    }

    fun startEdit(message: Message) {
        editing.value = message
    }

    fun cancelEdit() {
        editing.value = null
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch { conversations.deleteMessage(message.id) }
    }

    fun clearConversation() {
        viewModelScope.launch {
            engine.stop(conversationId)
            conversations.clearMessages(conversationId)
        }
    }

    fun rename(title: String) {
        viewModelScope.launch { conversations.rename(conversationId, title) }
    }

    fun setModel(providerId: String, modelId: String, label: String) {
        viewModelScope.launch {
            conversations.setModel(conversationId, providerId, modelId, label)
            sheetProvider.value = null
        }
    }

    /** Commits the model picked in the sheet (provider = sheet's selection). */
    fun commitSheetModel(model: ChatModel) {
        val providerId = sheetProvider.value
            ?: conversation.value?.providerId
            ?: return
        setModel(providerId, model.modelId, model.displayName ?: model.modelId)
    }
}
