package com.sokhan.chat.domain.service

import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ProviderRepository

/**
 * Creates a conversation preconfigured with an assistant's persona. Shared by
 * the assistants list ("Start chat") and the assistant editor so behavior is
 * identical in both entry points.
 */
class AssistantChatStarter(
    private val conversations: ConversationRepository,
    private val providers: ProviderRepository
) {
    suspend fun start(assistant: Assistant): String {
        val providerId = assistant.providerId ?: providers.defaultProviderId()
        val modelId = assistant.modelId ?: providerId?.let { id ->
            providers.resolve(id)?.config?.defaultModelId
        }
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = Ids.newId(),
            title = "",
            createdAt = now,
            updatedAt = now,
            systemPrompt = assistant.systemPrompt,
            providerId = providerId,
            modelId = modelId,
            temperature = assistant.temperature,
            maxTokens = assistant.maxTokens,
            streaming = assistant.streaming,
            assistantId = assistant.id
        )
        conversations.upsert(conversation)
        return conversation.id
    }
}
