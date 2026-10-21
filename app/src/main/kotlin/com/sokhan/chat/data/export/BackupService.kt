package com.sokhan.chat.data.export

import com.sokhan.chat.core.AppLog
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.first

/**
 * Builds and restores [BackupBundle]s from the repositories.
 * The secure store is consulted only when the user asks to include keys.
 */
class BackupService(
    private val conversations: ConversationRepository,
    private val assistants: AssistantRepository,
    private val providers: ProviderRepository,
    private val codec: BackupCodec
) {

    suspend fun export(includeApiKeys: Boolean): String {
        val bundle = BackupBundle(
            exportedAt = System.currentTimeMillis(),
            conversations = conversations.listAllConversations().map { convo ->
                BackupConversation(
                    id = convo.id,
                    title = convo.title,
                    createdAt = convo.createdAt,
                    updatedAt = convo.updatedAt,
                    pinned = convo.pinned,
                    archived = convo.archived,
                    systemPrompt = convo.systemPrompt,
                    providerId = convo.providerId,
                    modelId = convo.modelId,
                    modelLabel = convo.modelLabel,
                    temperature = convo.temperature,
                    maxTokens = convo.maxTokens,
                    topP = convo.topP,
                    streaming = convo.streaming,
                    assistantId = convo.assistantId,
                    messages = observeMessagesSnapshot(convo.id).map { it.toBackup() }
                )
            },
            assistants = assistants.observeAll().first().map { it.toBackup() },
            providers = providers.observeAll().first().map { it.toBackup() },
            providerKeys = if (includeApiKeys) {
                providers.observeAll().first().mapNotNull { provider ->
                    providers.resolve(provider.id)?.apiKey?.let { key -> provider.id to key }
                }.toMap()
            } else {
                emptyMap()
            }
        )
        return codec.encode(bundle)
    }

    /** @return summary or null when the payload is not a valid backup. */
    suspend fun import(payload: String): ImportSummary? {
        val bundle = codec.decode(payload) ?: return null
        var conversationsAdded = 0
        var conversationsMerged = 0
        var assistantsAdded = 0
        var assistantsMerged = 0
        var providersAdded = 0
        var providersMerged = 0

        val existingConversations = conversations.listAllConversations().map { it.id }.toSet()

        bundle.conversations.forEach { backup ->
            val conversation = backup.toDomain()
            conversations.upsert(conversation)
            // Single transactional batch; upsert (not REPLACE) keeps this
            // idempotent and preserves locally-referenced attachment rows,
            // which backup payloads deliberately do not contain.
            conversations.upsertMessages(conversation.id, backup.messages.map { it.toDomain(conversation.id) })
            if (existingConversations.contains(backup.id)) conversationsMerged++ else conversationsAdded++
        }

        val existingAssistants = assistants.observeAll().first().map { it.id }.toSet()
        bundle.assistants.forEach { backup ->
            val assistant = backup.toDomain()
            assistants.upsert(assistant)
            if (existingAssistants.contains(backup.id)) assistantsMerged++ else assistantsAdded++
        }

        val existingProviders = providers.observeAll().first().map { it.id }.toSet()
        bundle.providers.forEach { backup ->
            val config = backup.toDomain()
            providers.upsert(config)
            val key = bundle.providerKeys[backup.id]
            if (!key.isNullOrBlank()) providers.upsert(config, newApiKey = key)
            if (existingProviders.contains(backup.id)) providersMerged++ else providersAdded++
        }

        val summary = ImportSummary(
            conversationsAdded = conversationsAdded,
            conversationsMerged = conversationsMerged,
            assistantsAdded = assistantsAdded,
            assistantsMerged = assistantsMerged,
            providersAdded = providersAdded,
            providersMerged = providersMerged
        )
        AppLog.d("Backup", "import done: ${summary.describe()}")
        return summary
    }

    private suspend fun observeMessagesSnapshot(conversationId: String): List<Message> =
        conversations.observeMessages(conversationId).first()

    // ── converters ──────────────────────────────────────────────────────

    private fun Conversation.toBackup() = BackupConversation(
        id = id, title = title, createdAt = createdAt, updatedAt = updatedAt,
        pinned = pinned, archived = archived, systemPrompt = systemPrompt,
        providerId = providerId, modelId = modelId, modelLabel = modelLabel,
        temperature = temperature, maxTokens = maxTokens, topP = topP,
        streaming = streaming, assistantId = assistantId
    )

    private fun BackupConversation.toDomain(): Conversation = Conversation(
        id = id, title = title, createdAt = createdAt, updatedAt = updatedAt,
        pinned = pinned, archived = archived, systemPrompt = systemPrompt,
        providerId = providerId, modelId = modelId, modelLabel = modelLabel,
        temperature = temperature, maxTokens = maxTokens, topP = topP,
        streaming = streaming, assistantId = assistantId
    )

    private fun Message.toBackup() = BackupMessage(
        id = id, role = role.name, content = content, reasoning = reasoning,
        createdAt = createdAt, status = status.name, modelId = modelId,
        providerId = providerId,
        usagePromptTokens = usage?.promptTokens,
        usageCompletionTokens = usage?.completionTokens,
        usageTotalTokens = usage?.totalTokens,
        finishReason = finishReason
    )

    private fun BackupMessage.toDomain(conversationId: String): Message = Message(
        id = id,
        conversationId = conversationId,
        role = runCatching {
            com.sokhan.chat.domain.model.MessageRole.valueOf(role)
        }.getOrDefault(com.sokhan.chat.domain.model.MessageRole.USER),
        content = content,
        reasoning = reasoning,
        createdAt = createdAt,
        status = runCatching {
            com.sokhan.chat.domain.model.MessageStatus.valueOf(status ?: "SUCCESS")
        }.getOrDefault(com.sokhan.chat.domain.model.MessageStatus.SUCCESS),
        modelId = modelId,
        providerId = providerId,
        usage = usagePromptTokens?.let {
            com.sokhan.chat.domain.model.TokenUsage(it, usageCompletionTokens, usageTotalTokens)
        },
        finishReason = finishReason
    )

    private fun Assistant.toBackup() = BackupAssistant(
        id = id, name = name, emoji = emoji, systemPrompt = systemPrompt,
        providerId = providerId, modelId = modelId, modelLabel = modelLabel,
        temperature = temperature, maxTokens = maxTokens, streaming = streaming,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun BackupAssistant.toDomain(): Assistant = Assistant(
        id = id, name = name, emoji = emoji, systemPrompt = systemPrompt,
        providerId = providerId, modelId = modelId, modelLabel = modelLabel,
        temperature = temperature, maxTokens = maxTokens, streaming = streaming,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun ProviderConfig.toBackup() = BackupProvider(
        id = id, name = name, kind = kind.name, baseUrl = baseUrl,
        organization = organization, apiVersion = apiVersion, headers = headers,
        defaultModelId = defaultModelId, includeUsageInStream = includeUsageInStream
    )

    private fun BackupProvider.toDomain(): ProviderConfig = ProviderConfig(
        id = id, name = name,
        kind = runCatching {
            com.sokhan.chat.domain.model.ProviderKind.valueOf(kind)
        }.getOrDefault(com.sokhan.chat.domain.model.ProviderKind.OPENAI_COMPATIBLE),
        baseUrl = baseUrl, organization = organization, apiVersion = apiVersion,
        headers = headers, defaultModelId = defaultModelId,
        includeUsageInStream = includeUsageInStream,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
