package com.sokhan.chat.domain.service

import com.sokhan.chat.core.AppLog
import com.sokhan.chat.core.DispatcherProvider
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.agent.AgentToolHandler
import com.sokhan.chat.domain.agent.AgentToolRegistry
import com.sokhan.chat.domain.agent.TextToolProtocol
import com.sokhan.chat.domain.agent.ToolCall
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
import com.sokhan.chat.domain.repository.PendingToolAction
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
import com.sokhan.chat.provider.ToolCallRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
    private val toolRegistry: AgentToolRegistry,
    private val streamThrottleMs: Long = 250L
) : ChatEngine {

    private val jobsLock = Any()
    private val jobs = HashMap<String, Job>()
    private val startMutex = Mutex()

    private val streamActive = MutableStateFlow(StreamState())
    override val streamState: StateFlow<StreamState> = streamActive.asStateFlow()

    // ── Agent-mode approvals ─────────────────────────────────────────────

    private val approvalLock = Any()
    private val approvalWaiters = HashMap<String, CompletableDeferred<Boolean>>()
    private val pendingActions = MutableStateFlow<List<PendingToolAction>>(emptyList())

    override val pendingToolActions: StateFlow<List<PendingToolAction>> =
        pendingActions.asStateFlow()

    override fun respondToToolAction(callId: String, approved: Boolean) {
        val waiter = synchronized(approvalLock) {
            pendingActions.update { list -> list.filterNot { it.callId == callId } }
            approvalWaiters.remove(callId)
        }
        waiter?.complete(approved)
    }

    /**
     * Suspends until the user answers the confirmation dialog for [call].
     * Cancellation (stop, navigation) drops the pending entry with it.
     */
    private suspend fun requestApproval(
        conversationId: String,
        call: ToolCall,
        handler: AgentToolHandler
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(approvalLock) {
            approvalWaiters[call.id] = deferred
            pendingActions.update { list ->
                list + PendingToolAction(
                    callId = call.id,
                    conversationId = conversationId,
                    toolName = call.name,
                    argumentsSummary = argsSummary(call),
                    risk = handler.risk.name
                )
            }
        }
        try {
            return deferred.await()
        } finally {
            synchronized(approvalLock) {
                approvalWaiters.remove(call.id)
                pendingActions.update { list -> list.filterNot { it.callId == call.id } }
            }
        }
    }

    private fun argsSummary(call: ToolCall): String =
        call.arguments().entries.take(4).joinToString(", ") { (key, value) ->
            "$key=${value.toString().take(60)}"
        }.take(160)

    /**
     * Endpoint+model pairs that rejected a native `tools` request this
     * session: those conversations silently switch to the text tool protocol
     * (see [TextToolProtocol]) instead of failing. Keyed per model so one
     * model that cannot take tools never poisons its siblings.
     */
    private val nativeToolsBroken = HashSet<String>()

    private fun toolsKey(target: GenerationTarget): String =
        "${target.providerConfig.id}::${target.modelId}"

    private fun nativeToolsBrokenFor(target: GenerationTarget): Boolean =
        synchronized(nativeToolsBroken) { nativeToolsBroken.contains(toolsKey(target)) }

    private fun markNativeToolsBroken(target: GenerationTarget) {
        synchronized(nativeToolsBroken) { nativeToolsBroken.add(toolsKey(target)) }
        AppLog.d(TAG, "native tools rejected; text protocol for ${toolsKey(target)}")
    }

    /**
     * How agent mode talks to the model for one generation.
     *
     * NATIVE sends the provider's `tools` array; TEXT asks for a JSON block
     * in the reply text and works with every chat model; OFF is plain chat.
     */
    private enum class ToolMode { NATIVE, TEXT, OFF }

    /**
     * The mode a fresh generation starts in for [target].
     *
     * Always try the provider's native `tools` array first: capability flags
     * in the model row are only a heuristic guess from the id (and are off by
     * default for user-added models), so trusting them silently downgraded
     * perfectly capable models — OpenRouter routes in particular — to the
     * text protocol, where many models never emit a call at all. A real
     * rejection falls back at runtime instead (see [isToolRejection]).
     */
    private fun initialToolMode(
        snapshot: SettingsSnapshot,
        target: GenerationTarget
    ): ToolMode = when {
        !snapshot.agentMode -> ToolMode.OFF
        nativeToolsBrokenFor(target) -> ToolMode.TEXT
        else -> ToolMode.NATIVE
    }

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

        val replyId = spawnAssistantRow(conversationId, target)

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
        val snapshot: SettingsSnapshot = settings.settings.value
        // Agent mode picks how it talks to the model: native `tools` when the
        // model looks capable, the text tool protocol when it is not (or when
        // the endpoint already rejected tools this session), plain chat after
        // both were refused. One silent downgrade per generation.
        var mode = initialToolMode(snapshot, target)
        var downgraded = false
        var turn = 0
        var currentReplyId = replyId

        while (true) {
            val content = StringBuilder()
            val reasoning = StringBuilder()
            var usage: TokenUsage? = null
            var finishReason: String? = null
            var toolCalls: List<ToolCall> = emptyList()
            var rowFinalized = false
            var lastFlushAt = 0L

            // Streaming text is flushed through Room on a ~250 ms cadence, so
            // messages stay the single source of truth.
            suspend fun flush(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastFlushAt < streamThrottleMs) return
                lastFlushAt = now
                conversations.updateMessageContent(
                    currentReplyId,
                    content.toString(),
                    reasoning.toString().ifBlank { null },
                    now
                )
            }

            try {
                val payload = buildPayload(
                    conversation = conversation,
                    snapshot = snapshot,
                    target = target,
                    mode = mode
                )
                val client = clientFactory.clientFor(target.providerConfig.kind)
                val context = ProviderContext(
                    config = target.providerConfig,
                    apiKey = target.apiKey
                )

                val fragments = LinkedHashMap<Int, ToolCallAccumulator>()

                client.chatPayload(payload, context).collect { result ->
                    result.fold(
                        onSuccess = { event ->
                            when (event) {
                                is ChatStreamEvent.Delta -> {
                                    content.append(event.text)
                                    flush()
                                }
                                is ChatStreamEvent.ReasoningDelta -> reasoning.append(event.text)
                                is ChatStreamEvent.ToolCallDelta -> {
                                    val accumulator = fragments.getOrPut(event.index) {
                                        ToolCallAccumulator()
                                    }
                                    event.id?.let { accumulator.id = it }
                                    event.name?.let { accumulator.name = it }
                                    if (event.argumentsDelta.isNotEmpty()) {
                                        accumulator.arguments.append(event.argumentsDelta)
                                    }
                                }
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
                toolCalls = fragments.values.mapNotNull { it.toToolCall() }
                // Safety net: the reply may carry a JSON tool block even when
                // native tools were offered (the endpoint dropped them, or the
                // model ignores them). Only registered names count, so random
                // JSON inside an answer is never mistaken for a call.
                if (mode != ToolMode.OFF && toolCalls.isEmpty()) {
                    TextToolProtocol.parse(content.toString())
                        ?.takeIf { toolRegistry.find(it.call.name) != null }
                        ?.let { parsed ->
                            toolCalls = listOf(parsed.call)
                            content.setLength(0)
                            content.append(parsed.cleanedText)
                        }
                }

                withContext(NonCancellable + Dispatchers.IO) {
                    flush(force = true)
                    // A tool-only turn (the model acted instead of writing)
                    // is a complete response — only a truly empty one fails.
                    if (content.isEmpty() && toolCalls.isEmpty()) throw ChatError.EmptyResponse
                    conversations.finalizeMessage(
                        messageId = currentReplyId,
                        status = MessageStatus.SUCCESS,
                        content = content.toString(),
                        reasoning = reasoning.toString().ifBlank { null },
                        usage = usage,
                        finishReason = finishReason
                            ?: if (toolCalls.isEmpty()) "stop" else "tool_calls",
                        errorKind = null,
                        errorDetail = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    rowFinalized = true
                    if (toolCalls.isNotEmpty()) {
                        conversations.attachToolCalls(
                            currentReplyId,
                            Json.encodeToString(toolCalls)
                        )
                    }
                }
                AppLog.d(
                    TAG,
                    "turn finished conv=$conversationId chars=${content.length} tools=${toolCalls.size}"
                )

                if (toolCalls.isEmpty()) return

                // ── Agent round: run what the model asked for ────────────
                if (mode == ToolMode.OFF || turn >= MAX_AGENT_TURNS) {
                    // The calls stay recorded so the UI can show them, and
                    // buildPayload answers them synthetically if we stop here.
                    AppLog.d(TAG, "agent stops before running tools turn=$turn")
                    return
                }
                for (call in toolCalls) {
                    val handler = toolRegistry.find(call.name)
                    val approved = handler != null &&
                        (!toolRegistry.needsConfirmation(handler) ||
                            requestApproval(conversationId, call, handler))
                    var outcome: String? = null
                    val output = when {
                        handler == null -> {
                            outcome = TOOL_FAILED
                            "Unknown tool \"${call.name}\"."
                        }
                        !approved -> {
                            outcome = TOOL_DECLINED
                            "The user declined this action."
                        }
                        else -> try {
                            handler.run(call.arguments())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            AppLog.e(TAG, "tool ${call.name} failed", failure)
                            outcome = TOOL_FAILED
                            "The tool failed: " +
                                (failure.message ?: failure::class.java.simpleName)
                        }
                    }
                    insertToolResult(conversationId, call, output, outcome)
                }
                turn++
                currentReplyId = spawnAssistantRow(conversationId, target)
            } catch (cancelledEx: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (!rowFinalized) {
                        if (content.isEmpty()) {
                            conversations.deleteMessage(currentReplyId)
                        } else {
                            conversations.finalizeMessage(
                                messageId = currentReplyId,
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
                }
                throw cancelledEx
            } catch (chatError: ChatError) {
                if (mode != ToolMode.OFF && !downgraded &&
                    isToolRejection(chatError, content.isNotEmpty(), toolCalls.isNotEmpty())
                ) {
                    downgraded = true
                    when (mode) {
                        ToolMode.NATIVE -> {
                            // The endpoint refuses the `tools` array — retry the
                            // same turn through the text protocol.
                            markNativeToolsBroken(target)
                            mode = if (snapshot.agentMode) ToolMode.TEXT else ToolMode.OFF
                        }
                        // Even the text instructions were rejected: plain chat.
                        else -> mode = ToolMode.OFF
                    }
                    continue
                }
                persistFailure(currentReplyId, content, reasoning, chatError)
                return
            } catch (unexpected: Throwable) {
                AppLog.e(TAG, "unexpected generation failure", unexpected)
                persistFailure(
                    currentReplyId,
                    content,
                    reasoning,
                    ChatError.Unknown(unexpected, unexpected.message)
                )
                return
            }
        }
    }

    /**
     * True when a failure smells like "this request shape is wrong for this
     * endpoint" and retrying in a simpler tool mode can still save the turn.
     *
     * Providers signal "I cannot take `tools`" in several different ways:
     *  - `400` / `422` — OpenAI, llama.cpp and friends reject the payload;
     *  - `404` — OpenRouter answers "No endpoints found that support tool use";
     *  - `200` with an empty stream — some gateways silently drop the array.
     */
    private fun isToolRejection(
        error: ChatError,
        hasContent: Boolean,
        hasToolCalls: Boolean
    ): Boolean = isToolRejectionFor(error, hasContent, hasToolCalls)

    /** Reassembles indexed SSE fragments into one tool call. */
    private class ToolCallAccumulator(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun toToolCall(): ToolCall? {
            val toolName = name ?: return null
            val callId = id ?: "call_${toolName.hashCode()}_${arguments.length}"
            return ToolCall(id = callId, name = toolName, argumentsJson = arguments.toString())
        }
    }

    /** Inserts the empty STREAMING assistant row a turn streams into. */
    private suspend fun spawnAssistantRow(
        conversationId: String,
        target: GenerationTarget
    ): String {
        val newReplyId = Ids.newId()
        // Keep insertion strictly after the latest existing row so the
        // (createdAt, id) ordering always reads replies below the user turn.
        val lastAt = conversations.observeMessages(conversationId)
            .first().lastOrNull()?.createdAt ?: 0L
        val now = maxOf(System.currentTimeMillis(), lastAt + 1)
        conversations.insertMessage(
            Message(
                id = newReplyId,
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                createdAt = now,
                status = MessageStatus.STREAMING,
                modelId = target.modelId,
                providerId = target.providerConfig.id
            )
        )
        return newReplyId
    }

    /**
     * Persists one tool result as a TOOL row the next request can replay.
     *
     * @param errorKind null when the tool ran, [TOOL_DECLINED] when the user
     * said no, [TOOL_FAILED] when it broke — the UI renders those as status.
     */
    private suspend fun insertToolResult(
        conversationId: String,
        call: ToolCall,
        output: String,
        errorKind: String? = null
    ) {
        val lastAt = conversations.observeMessages(conversationId)
            .first().lastOrNull()?.createdAt ?: 0L
        val now = maxOf(System.currentTimeMillis(), lastAt + 1)
        conversations.insertMessage(
            Message(
                id = Ids.newId(),
                conversationId = conversationId,
                role = MessageRole.TOOL,
                content = output,
                createdAt = now,
                status = MessageStatus.SUCCESS,
                errorKind = errorKind,
                toolCallId = call.id,
                toolName = call.name
            )
        )
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
        target: GenerationTarget,
        mode: ToolMode
    ): ChatPayload {
        val textProtocol = mode == ToolMode.TEXT
        val agentTools = if (mode == ToolMode.NATIVE) {
            toolRegistry.tools
        } else {
            emptyList()
        }
        val basePrompt = conversation.systemPrompt
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.defaultSystemPrompt
        // One system message only: some endpoints reject a second SYSTEM row,
        // so the protocol rules ride along inside the same prompt — in BOTH
        // modes, so an endpoint that silently drops `tools` still gets told
        // how the model can act, and a model that ignores native tools has a
        // second mechanism to fall back on.
        val systemPrompt: String? = when {
            mode == ToolMode.OFF -> basePrompt
            else -> listOfNotNull(
                basePrompt?.takeIf { it.isNotBlank() },
                TextToolProtocol.instruction(toolRegistry.tools, native = !textProtocol)
            ).joinToString("\n\n")
        }

        val rows = conversations.observeMessages(conversation.id).first()

        val messages = ArrayList<ChatMessage>(expectedCount(rows.size, systemPrompt))
        if (!systemPrompt.isNullOrBlank()) {
            messages += ChatMessage(role = ChatMessageRole.SYSTEM, text = systemPrompt)
        }

        // Tool pairing: every call an assistant message declares must be
        // answered by TOOL messages immediately after it, or the provider
        // rejects the whole request. Calls left unanswered (the user stopped
        // the agent mid-run, or a row was edited away) get a synthetic answer.
        val awaitingResults = LinkedHashSet<String>()

        fun answerUnansweredCalls() {
            awaitingResults.forEach { callId ->
                messages += if (textProtocol) {
                    ChatMessage(
                        role = ChatMessageRole.USER,
                        text = TextToolProtocol.renderResult(null, INTERRUPTED_TOOL_RESULT)
                    )
                } else {
                    ChatMessage(
                        role = ChatMessageRole.TOOL,
                        text = INTERRUPTED_TOOL_RESULT,
                        toolCallId = callId
                    )
                }
            }
            awaitingResults.clear()
        }

        rows.forEach { row ->
            when (row.role) {
                MessageRole.USER -> {
                    answerUnansweredCalls()
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
                    val calls = row.toolCalls?.let { stored ->
                        runCatching { Json.decodeFromString<List<ToolCall>>(stored) }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                    }
                    when {
                        calls != null -> {
                            answerUnansweredCalls()
                            if (textProtocol) {
                                // Replay the calls as the same text blocks the
                                // model wrote, so the endpoint sees no tool role.
                                val block = calls.joinToString("\n") {
                                    TextToolProtocol.renderCall(it)
                                }
                                messages += ChatMessage(
                                    role = ChatMessageRole.ASSISTANT,
                                    text = listOfNotNull(
                                        row.content.ifBlank { null },
                                        block
                                    ).joinToString("\n\n")
                                )
                            } else {
                                messages += ChatMessage(
                                    role = ChatMessageRole.ASSISTANT,
                                    text = row.content.ifBlank { null },
                                    toolCalls = calls.map { call ->
                                        ToolCallRequest(
                                            id = call.id,
                                            name = call.name,
                                            argumentsJson = call.argumentsJson
                                        )
                                    }
                                )
                            }
                            awaitingResults.addAll(calls.map { it.id })
                        }
                        row.status == MessageStatus.SUCCESS && row.content.isNotBlank() -> {
                            answerUnansweredCalls()
                            messages += ChatMessage(
                                role = ChatMessageRole.ASSISTANT,
                                text = row.content
                            )
                        }
                        else -> Unit // streaming / failed / partial rows are not replayed
                    }
                }
                MessageRole.TOOL -> {
                    val callId = row.toolCallId
                    if (callId != null && awaitingResults.remove(callId)) {
                        messages += if (textProtocol) {
                            ChatMessage(
                                role = ChatMessageRole.USER,
                                text = TextToolProtocol.renderResult(row.toolName, row.content)
                            )
                        } else {
                            ChatMessage(
                                role = ChatMessageRole.TOOL,
                                text = row.content,
                                toolCallId = callId
                            )
                        }
                    }
                    // Orphans (their assistant row was deleted) are dropped.
                }
                MessageRole.SYSTEM -> Unit // the prompt above owns this role
            }
        }
        answerUnansweredCalls()

        checkCapabilities(messages, target)

        return ChatPayload(
            model = target.modelId,
            messages = messages,
            temperature = target.temperature,
            maxTokens = target.maxTokens,
            topP = target.topP,
            stream = target.streaming,
            includeUsage = target.includeUsage,
            tools = agentTools
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

        /** Tool-executing rounds allowed after one user message. */
        const val MAX_AGENT_TURNS = 6

        /** Answered for calls whose execution the user interrupted. */
        const val INTERRUPTED_TOOL_RESULT = "The action was interrupted before it completed."

        /** [Message.errorKind] markers for TOOL rows. */
        const val TOOL_DECLINED = "DECLINED"
        const val TOOL_FAILED = "FAILED"
    }
}

/**
 * True when a failure means "this endpoint cannot take a `tools` array" and
 * the same turn can be retried without one.
 *
 * Providers report it inconsistently:
 *  - `400` / `422` — OpenAI, llama.cpp and most compatible servers reject the
 *    payload outright;
 *  - `404` — OpenRouter answers "No endpoints found that support tool use"
 *    for models whose routes do not implement tool calling;
 *  - `200` with an empty stream — some gateways silently drop the field.
 *
 * A turn that already produced text or a tool call is never retried: the user
 * has seen part of an answer and repeating it would duplicate output.
 */
internal fun isToolRejectionFor(
    error: ChatError,
    hasContent: Boolean,
    hasToolCalls: Boolean
): Boolean {
    if (hasContent || hasToolCalls) return false
    return error is ChatError.BadRequest ||
        error is ChatError.NotFound ||
        error is ChatError.Malformed ||
        error is ChatError.EmptyResponse
}
