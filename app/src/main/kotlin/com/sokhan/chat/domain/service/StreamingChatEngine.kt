package com.sokhan.chat.domain.service

import com.sokhan.chat.core.AppLog
import com.sokhan.chat.core.DispatcherProvider
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.AttachmentKind
import com.sokhan.chat.domain.model.ChatError
import com.sokhan.chat.domain.model.ChatErrorKind
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.SettingsSnapshot
import com.sokhan.chat.domain.model.TokenUsage
import com.sokhan.chat.domain.repository.AttachmentRepository
import com.sokhan.chat.domain.repository.ChatEngine
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.domain.repository.StreamState
import com.sokhan.chat.provider.ChatMessage
import com.sokhan.chat.provider.ChatMessageRole
import com.sokhan.chat.provider.ChatPayload
import com.sokhan.chat.provider.ChatStreamEvent
import com.sokhan.chat.provider.PdfPayload
import com.sokhan.chat.provider.ProviderClientFactory
import com.sokhan.chat.provider.ProviderContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64

/** Everything the engine resolved for a single generation. */
internal data class GenerationTarget(
    val providerConfig: com.sokhan.chat.domain.model.ProviderConfig,
    val apiKey: String?,
    val modelId: String,
    val modelLabel: String?,
    val temperature: Double?,
    val maxTokens: Int?,
    val topP: Double?,
    val streaming: Boolean,
    val includeUsage: Boolean,
    val modelRow: ChatModel?
)

/**
 * One-generation-per-conversation orchestrator.
 *
 * Persistence: streaming text is flushed through Room on a ~250 ms cadence, so
 * messages stay the single source of truth — restarts, background work and
 * multiple screens all observe the same rows.
 */
class StreamingChatEngine(
    private val conversations: ConversationRepository,
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val attachments: AttachmentRepository,
    private val settings: SettingsRepository,
    private val clientFactory: ProviderClientFactory,
    private val appScope: CoroutineScope,
    private val streamThrottleMs: Long = 250L
) : ChatEngine {

    private val jobsLock = Any()
    private val jobs = HashMap<String, Job>()
    private val startMutex = Mutex()

    private val streamActive = MutableStateFlow(StreamState())
    override val streamState: StateFlow<StreamState> = streamActive.asStateFlow()

    // ── ChatEngine ───────────────────────────────────────────────────────

    override suspend fun generate(conversationId: String): ChatError? =
        startGeneration(conversationId, clearTrailingAssistant = false)

    override suspend fun regenerate(conversationId: String): ChatError? =
        startGeneration(conversationId, clearTrailingAssistant = true)

    override suspend fun retry(conversationId: String): ChatError? = regenerate(conversationId)

    override suspend fun editAndResend(
        conversationId: String,
        messageId: String,
        newContent: String
    ): ChatError? {
        // Verify a generation target exists BEFORE destroying trailing
        // messages; otherwise a NoModel failure would lose the reply history.
        val conversation = conversations.get(conversationId) ?: return ChatError.NoModel
        if (resolveTarget(conversation) == null) return ChatError.NoModel
        conversations.updateMessageContent(
            messageId = messageId,
            content = newContent,
            reasoning = null,
            updatedAt = System.currentTimeMillis()
        )
        conversations.deleteMessagesFrom(messageId)
        return startGeneration(conversationId, clearTrailingAssistant = false)
    }

    override fun stop(conversationId: String) {
        val job: Job? = synchronized(jobsLock) { jobs.remove(conversationId) }
        job?.cancel()
        markInactive(conversationId)
    }

    override fun stopAll() {
        val snapshot: Map<String, Job> = synchronized(jobsLock) {
            val copy = jobs.toMap()
            jobs.clear()
            copy
        }
        snapshot.values.forEach { it.cancel() }
        val ids = snapshot.keys
        streamActive.update { it.copy(active = it.active.subtract(ids.toSet())) }
    }

    /**
     * App-restart sweep: STREAMING rows left behind when the process died are
     * finalized as partial replies (or dropped when empty).
     */
    override suspend fun recoverOrphans() {
        conversations.sweepStreamingMessages()
        streamActive.update { it.copy(active = emptySet()) }
        synchronized(jobsLock) { jobs.clear() }
    }

    // ── Core flow ────────────────────────────────────────────────────────

    private suspend fun startGeneration(
        conversationId: String,
        clearTrailingAssistant: Boolean
    ): ChatError? {
        val conversation = conversations.get(conversationId) ?: return ChatError.NoModel
        // Resolve the generation target BEFORE deleting anything: regenerate()
        // clears the last assistant row, and if the target were missing that
        // reply would be lost silently with only an error snackbar back.
        val target = resolveTarget(conversation) ?: return ChatError.NoModel

        startMutex.withLock {
            if (clearTrailingAssistant) {
                conversations.lastAssistantMessage(conversationId)?.let {
                    conversations.deleteMessage(it.id)
                }
            }
            // One in-flight generation per conversation.
            val previous: Job? = synchronized(jobsLock) { jobs.remove(conversationId) }
            previous?.cancel()
            markInactive(conversationId)
        }

        val replyId = Ids.newId()
        // Keep insertion strictly after the latest existing row so the
        // (createdAt, id) ordering always reads replies below the user turn.
        val lastAt = conversations.observeMessages(conversationId).first().lastOrNull()?.createdAt ?: 0L
        val now = maxOf(System.currentTimeMillis(), lastAt + 1)
        conversations.insertMessage(
            Message(
                id = replyId,
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                createdAt = now,
                status = MessageStatus.STREAMING,
                modelId = target.modelId,
                providerId = target.providerConfig.id
            )
        )

        val job = appScope.launch {
            runGeneration(conversationId, conversation, replyId, target)
        }.also { launched ->
            synchronized(jobsLock) { jobs[conversationId] = launched }
            markActive(conversationId)
            launched.invokeOnCompletion {
                synchronized(jobsLock) {
                    if (jobs[conversationId] === launched) jobs.remove(conversationId)
                }
                markInactive(conversationId)
            }
        }
        AppLog.d(TAG, "generation started conv=$conversationId msg=$replyId stream=${target.streaming}")
        return null
    }

    private suspend fun resolveTarget(conversation: Conversation): GenerationTarget? {
        val snapshot: SettingsSnapshot = settings.settings.value
        val providerId = conversation.providerId ?: snapshot.defaultProviderId
        val provider = when {
            providerId != null -> providers.resolve(providerId)
            else -> providers.resolveDefault()
        } ?: return null
        if (provider.config.baseUrl.isBlank()) return null

        val wantedModel = conversation.modelId ?: provider.config.defaultModelId ?: snapshot.defaultModelId
        val modelRow = wantedModel?.let { models.get(provider.config.id, it) }
        val effectiveModel = wantedModel ?: return null
        return GenerationTarget(
            providerConfig = provider.config,
            apiKey = provider.apiKey,
            modelId = effectiveModel,
            modelLabel = modelRow?.displayName ?: modelRow?.modelId ?: effectiveModel,
            temperature = conversation.temperature ?: provider.config.defaultTemperature,
            maxTokens = conversation.maxTokens ?: provider.config.defaultMaxTokens,
            topP = conversation.topP ?: provider.config.defaultTopP,
            streaming = conversation.streaming ?: snapshot.defaultStreaming,
            includeUsage = provider.config.includeUsageInStream,
            modelRow = modelRow
        )
    }

    private suspend fun runGeneration(
        conversationId: String,
        conversation: Conversation,
        replyId: String,
        target: GenerationTarget
    ) {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val snapshot = settings.settings.value

        var usage: TokenUsage? = null
        var finishReason: String? = null
        var lastFlushAt = 0L
        var lastFlushLen = 0

        suspend fun flush(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastFlushAt < streamThrottleMs) return
            lastFlushAt = now
            lastFlushLen = content.length
            conversations.updateMessageContent(
                replyId,
                content.toString(),
                reasoning.toString().ifBlank { null },
                now
            )
        }

        try {
            val payload = buildPayload(conversation, snapshot, target)
            val client = clientFactory.clientFor(target.providerConfig.kind)
            val context = ProviderContext(
                config = target.providerConfig,
                apiKey = target.apiKey
            )

            client.chatPayload(payload, context).collect { result ->
                result.fold(
                    onSuccess = { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> {
                                content.append(event.text)
                                flush()
                            }
                            is ChatStreamEvent.ReasoningDelta -> reasoning.append(event.text)
                            is ChatStreamEvent.Usage -> usage = TokenUsage(
                                event.usage.promptTokens,
                                event.usage.completionTokens,
                                event.usage.totalTokens
                            )
                            is ChatStreamEvent.Finished -> finishReason = event.finishReason
                        }
                    },
                    onFailure = { failure -> throw failure }
                )
            }

            withContext(NonCancellable + Dispatchers.IO) {
                flush(force = true)
                if (content.isEmpty()) throw ChatError.EmptyResponse
                conversations.finalizeMessage(
                    messageId = replyId,
                    status = MessageStatus.SUCCESS,
                    content = content.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    usage = usage,
                    finishReason = finishReason ?: "stop",
                    errorKind = null,
                    errorDetail = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
            AppLog.d(TAG, "generation finished conv=$conversationId chars=${content.length}")
        } catch (cancelledEx: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                if (content.isEmpty()) {
                    conversations.deleteMessage(replyId)
                } else {
                    conversations.finalizeMessage(
                        messageId = replyId,
                        status = MessageStatus.PARTIAL,
                        content = content.toString(),
                        reasoning = reasoning.toString().ifBlank { null },
                        usage = usage,
                        finishReason = "stopped",
                        errorKind = ChatErrorKind.CANCELLED.name,
                        errorDetail = null,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
            throw cancelledEx
        } catch (chatError: ChatError) {
            persistFailure(replyId, content, reasoning, chatError)
        } catch (unexpected: Throwable) {
            AppLog.e(TAG, "unexpected generation failure", unexpected)
            persistFailure(
                replyId,
                content,
                reasoning,
                ChatError.Unknown(unexpected, unexpected.message)
            )
        }
    }

    private suspend fun persistFailure(
        replyId: String,
        content: StringBuilder,
        reasoning: StringBuilder,
        error: ChatError
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            if (content.isEmpty()) {
                // Nothing to show as partial — replace the empty row with failure metadata.
                conversations.finalizeMessage(
                    messageId = replyId,
                    status = MessageStatus.ERROR,
                    content = "",
                    reasoning = null,
                    usage = null,
                    finishReason = null,
                    errorKind = ChatErrorKind.of(error).name,
                    errorDetail = error.message,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                conversations.finalizeMessage(
                    messageId = replyId,
                    status = MessageStatus.PARTIAL,
                    content = content.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    usage = null,
                    finishReason = null,
                    errorKind = ChatErrorKind.of(error).name,
                    errorDetail = error.message,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    // ── Payload building ────────────────────────────────────────────────

    private suspend fun buildPayload(
        conversation: Conversation,
        snapshot: SettingsSnapshot,
        target: GenerationTarget
    ): ChatPayload {
        val systemPrompt = conversation.systemPrompt
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.defaultSystemPrompt

        val rows = conversations.observeMessages(conversation.id).first()

        val messages = ArrayList<ChatMessage>(expectedCount(rows.size, systemPrompt))
        if (!systemPrompt.isNullOrBlank()) {
            messages += ChatMessage(role = ChatMessageRole.SYSTEM, text = systemPrompt)
        }

        rows.forEach { row ->
            when (row.role) {
                MessageRole.USER -> {
                    if (row.content.isNotBlank() || hasAttachments(row.id)) {
                        val (text, images2, pdfs) = withUserAttachments(row)
                        messages += ChatMessage(
                            role = ChatMessageRole.USER,
                            text = text,
                            images = images2,
                            pdfs = pdfs
                        )
                    }
                }
                MessageRole.ASSISTANT -> {
                    if (row.status != MessageStatus.SUCCESS || row.content.isBlank()) return@forEach
                    messages += ChatMessage(role = ChatMessageRole.ASSISTANT, text = row.content)
                }
                else -> Unit // SYSTEM/TOOL rows are ignored (system prompt handled above)
            }
        }

        checkCapabilities(messages, target)

        return ChatPayload(
            model = target.modelId,
            messages = messages,
            temperature = target.temperature,
            maxTokens = target.maxTokens,
            topP = target.topP,
            stream = target.streaming,
            includeUsage = target.includeUsage
        )
    }

    private fun expectedCount(rows: Int, systemPrompt: String?): Int = rows + 2

    private suspend fun hasAttachments(messageId: String): Boolean =
        attachments.byMessage(messageId).isNotEmpty()

    /**
     * Converts stored attachments to request parts:
     *  - IMAGE  → base64 data URL (`image_url`)
     *  - PDF    → `file` part (OpenAI-style)
     *  - TEXT   → appended into the text body in a fenced block
     */
    private suspend fun withUserAttachments(
        row: Message
    ): Triple<String?, List<String>, List<PdfPayload>> {
        val items = attachments.byMessage(row.id)
        if (items.isEmpty()) return Triple(row.content, emptyList(), emptyList())
        val images = ArrayList<String>(2)
        val pdfs = ArrayList<PdfPayload>(1)
        var text = row.content
        return withContext(Dispatchers.IO) {
            items.forEach { attachment ->
                val file = attachments.resolveFile(attachment)
                    ?: return@forEach // deleted on disk → silently skipped
                try {
                    when (attachment.kind) {
                        AttachmentKind.IMAGE -> {
                            val bytes = file.readBytes()
                            val mime = attachment.mimeType ?: "image/jpeg"
                            images += "data:$mime;base64," +
                                Base64.getEncoder().encodeToString(bytes)
                        }
                        AttachmentKind.PDF -> {
                            val bytes = file.readBytes()
                            pdfs += PdfPayload(
                                name = attachment.name,
                                dataUrl = "data:application/pdf;base64," +
                                    Base64.getEncoder().encodeToString(bytes)
                            )
                        }
                        AttachmentKind.TEXT_FILE -> {
                            val snippet = file.readText()
                                .take(MAX_ATTACH_TEXT_CHARS)
                            text += "\n\n--- attached file: ${attachment.name} ---\n$snippet"
                        }
                    }
                } catch (ioError: Exception) {
                    AppLog.w(TAG, "attachment unreadable: ${attachment.name}")
                }
            }
            Triple(text, images, pdfs)
        }
    }

    private fun checkCapabilities(
        messages: List<ChatMessage>,
        target: GenerationTarget
    ) {
        val vision = target.modelRow?.capabilities?.vision ?: target.providerConfig.capabilities.vision
        val pdf = target.modelRow?.capabilities?.pdfFiles ?: target.providerConfig.capabilities.pdfFiles
        val hasImages = messages.any { it.images.isNotEmpty() }
        val hasPdfs = messages.any { it.pdfs.isNotEmpty() }
        if (hasImages && !vision) {
            throw ChatError.UnsupportedInput(target.modelId, "vision")
        }
        if (hasPdfs && !pdf) {
            throw ChatError.UnsupportedInput(target.modelId, "pdf")
        }
    }

    private fun markActive(conversationId: String) = streamActive.update {
        it.copy(active = it.active + conversationId)
    }

    private fun markInactive(conversationId: String) = streamActive.update {
        it.copy(active = it.active - conversationId)
    }

    private companion object {
        const val TAG = "Engine"
        const val MAX_ATTACH_TEXT_CHARS = 6000
    }
}
