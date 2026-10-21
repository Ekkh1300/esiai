package com.sokhan.chat.data.db

import com.sokhan.chat.data.db.entity.AssistantEntity
import com.sokhan.chat.data.db.entity.AttachmentEntity
import com.sokhan.chat.data.db.entity.ConversationEntity
import com.sokhan.chat.data.db.dao.ConversationCardRow
import com.sokhan.chat.data.db.entity.MessageEntity
import com.sokhan.chat.data.db.entity.ModelEntity
import com.sokhan.chat.data.db.entity.ProviderEntity
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.Attachment
import com.sokhan.chat.domain.model.AttachmentKind
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.repository.ConversationCardSnapshot
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.ModelCapabilities
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.ProviderCapabilities
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.model.ProviderKind
import com.sokhan.chat.domain.model.TokenUsage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val headersJsonParser = Json { ignoreUnknownKeys = true }

/**
 * Entity ⇄ domain mappers. Enums are persisted as their stable names.
 */
object DbMappers {

    // ── Provider ────────────────────────────────────────────────────────

    fun ProviderEntity.toDomain(): ProviderConfig = ProviderConfig(
        id = id,
        name = name,
        kind = runCatching { ProviderKind.valueOf(kind) }.getOrDefault(ProviderKind.OPENAI_COMPATIBLE),
        baseUrl = baseUrl,
        organization = organization,
        apiVersion = apiVersion,
        headers = parseHeaders(headersJson),
        defaultModelId = defaultModelId,
        capabilities = ProviderCapabilities(
            textFiles = capTextFiles,
            pdfFiles = capPdf,
            vision = capVision,
            listModels = capListModels
        ),
        defaultTemperature = defaultTemperature,
        defaultMaxTokens = defaultMaxTokens,
        defaultTopP = defaultTopP,
        defaultStreaming = defaultStreaming,
        includeUsageInStream = includeUsageInStream,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastValidatedAt = lastValidatedAt
    )

    fun ProviderConfig.toEntity(): ProviderEntity = ProviderEntity(
        id = id,
        name = name,
        kind = kind.name,
        baseUrl = baseUrl.trim(),
        organization = organization,
        apiVersion = apiVersion,
        headersJson = encodeHeaders(headers),
        defaultModelId = defaultModelId,
        capTextFiles = capabilities.textFiles,
        capPdf = capabilities.pdfFiles,
        capVision = capabilities.vision,
        capListModels = capabilities.listModels,
        defaultTemperature = defaultTemperature,
        defaultMaxTokens = defaultMaxTokens,
        defaultTopP = defaultTopP,
        defaultStreaming = defaultStreaming,
        includeUsageInStream = includeUsageInStream,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastValidatedAt = lastValidatedAt
    )

    private fun parseHeaders(raw: String?): Map<String, String> = raw?.let {
        runCatching {
            headersJsonParser.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                it
            )
        }.getOrNull()
    }.orEmpty()

    private fun encodeHeaders(headers: Map<String, String>): String? =
        if (headers.isEmpty()) null else headersJsonParser.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            headers
        )

    // ── Models ──────────────────────────────────────────────────────────

    fun ModelEntity.toDomain(): ChatModel = ChatModel(
        id = id,
        providerId = providerId,
        modelId = modelId,
        displayName = displayName,
        contextLength = contextLength,
        userDefined = userDefined,
        hidden = hidden,
        capabilities = ModelCapabilities(
            vision = capVision,
            tools = capTools,
            streaming = capStreaming,
            textFiles = capTextFiles,
            pdfFiles = capPdf,
            structured = capStructured,
            reasoning = capReasoning
        ),
        createdAt = createdAt
    )

    fun ChatModel.toEntity(): ModelEntity = ModelEntity(
        id = id,
        providerId = providerId,
        modelId = modelId,
        displayName = displayName,
        contextLength = contextLength,
        userDefined = userDefined,
        hidden = hidden,
        capVision = capabilities.vision,
        capTools = capabilities.tools,
        capStreaming = capabilities.streaming,
        capTextFiles = capabilities.textFiles,
        capPdf = capabilities.pdfFiles,
        capStructured = capabilities.structured,
        capReasoning = capabilities.reasoning,
        createdAt = createdAt
    )

    fun newModelEntityId(providerId: String, modelId: String): String = "$providerId::$modelId"

    // ── Conversation ────────────────────────────────────────────────────

    fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned,
        archived = archived,
        systemPrompt = systemPrompt,
        providerId = providerId,
        modelId = modelId,
        modelLabel = modelLabel,
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        streaming = streaming,
        assistantId = assistantId
    )

    fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned,
        archived = archived,
        systemPrompt = systemPrompt,
        providerId = providerId,
        modelId = modelId,
        modelLabel = modelLabel,
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        streaming = streaming,
        assistantId = assistantId
    )

    fun ConversationCardRow.toDomain(): ConversationCardSnapshot = ConversationCardSnapshot(
        conversation = Conversation(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            pinned = pinned,
            archived = archived,
            systemPrompt = systemPrompt,
            providerId = providerId,
            modelId = modelId,
            modelLabel = modelLabel,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP,
            streaming = streaming,
            assistantId = assistantId
        ),
        previewText = previewText,
        messageCount = messageCount,
        lastMessageAt = lastMessageAt
    )

    // ── Message ─────────────────────────────────────────────────────────

    fun MessageEntity.toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
        content = content,
        reasoning = reasoning,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SUCCESS),
        modelId = modelId,
        providerId = providerId,
        usage = usagePromptTokens?.let { prompt ->
            TokenUsage(prompt, usageCompletionTokens, usageTotalTokens)
        },
        finishReason = finishReason,
        errorKind = errorKind,
        errorDetail = errorDetail
    )

    fun Message.toEntity(): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name,
        content = content,
        reasoning = reasoning,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status.name,
        modelId = modelId,
        providerId = providerId,
        usagePromptTokens = usage?.promptTokens,
        usageCompletionTokens = usage?.completionTokens,
        usageTotalTokens = usage?.totalTokens,
        finishReason = finishReason,
        errorKind = errorKind,
        errorDetail = errorDetail
    )

    // ── Attachment ──────────────────────────────────────────────────────

    fun AttachmentEntity.toDomain(): Attachment = Attachment(
        id = id,
        messageId = messageId,
        kind = runCatching { AttachmentKind.valueOf(kind) }.getOrDefault(AttachmentKind.IMAGE),
        name = name,
        mimeType = mimeType,
        relativePath = relativePath,
        sizeBytes = sizeBytes,
        createdAt = createdAt
    )

    fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
        id = id,
        messageId = messageId,
        kind = kind.name,
        name = name,
        mimeType = mimeType,
        relativePath = relativePath.orEmpty(),
        sizeBytes = sizeBytes,
        createdAt = createdAt
    )

    // ── Assistant ───────────────────────────────────────────────────────

    fun AssistantEntity.toDomain(): Assistant = Assistant(
        id = id,
        name = name,
        emoji = emoji,
        systemPrompt = systemPrompt,
        providerId = providerId,
        modelId = modelId,
        modelLabel = modelLabel,
        temperature = temperature,
        maxTokens = maxTokens,
        streaming = streaming,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Assistant.toEntity(): AssistantEntity = AssistantEntity(
        id = id,
        name = name,
        emoji = emoji,
        systemPrompt = systemPrompt,
        providerId = providerId,
        modelId = modelId,
        modelLabel = modelLabel,
        temperature = temperature,
        maxTokens = maxTokens,
        streaming = streaming,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
