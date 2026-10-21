package com.sokhan.chat.data.export

import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.model.ProviderKind
import com.sokhan.chat.domain.model.TokenUsage
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.AssistantSeed
import com.sokhan.chat.domain.repository.ConversationCardSnapshot
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.ResolvedProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fake of [ConversationRepository] for backup round-trip tests. */
private class FakeConversationRepository : ConversationRepository {
    val conversations = LinkedHashMap<String, Conversation>()
    val messages = LinkedHashMap<String, MutableList<Message>>()

    override fun observeCards(archived: Boolean, search: String?): Flow<List<ConversationCardSnapshot>> =
        MutableStateFlow(emptyList())

    override suspend fun get(id: String): Conversation? = conversations[id]

    override suspend fun listAllConversations(): List<Conversation> = conversations.values.toList()

    override suspend fun upsert(conversation: Conversation) {
        conversations[conversation.id] = conversation
    }

    override suspend fun delete(id: String) {
        conversations.remove(id)
        messages.remove(id)
    }

    override suspend fun rename(id: String, title: String) {
        upsert(conversations[id]!!.copy(title = title))
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        upsert(conversations[id]!!.copy(pinned = pinned))
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        upsert(conversations[id]!!.copy(archived = archived))
    }

    override suspend fun setModel(id: String, providerId: String, modelId: String, modelLabel: String) {
        upsert(conversations[id]!!.copy(providerId = providerId, modelId = modelId, modelLabel = modelLabel))
    }

    override suspend fun setSystemPrompt(id: String, prompt: String?) {
        upsert(conversations[id]!!.copy(systemPrompt = prompt))
    }

    override suspend fun clearMessages(id: String) {
        messages[id]?.clear()
    }

    override suspend fun insertMessage(message: Message) {
        // Mirrors Room's OnConflictStrategy.REPLACE: same id wins, no duplicates.
        val list = messages.getOrPut(message.conversationId) { mutableListOf() }
        val index = list.indexOfFirst { it.id == message.id }
        if (index >= 0) list[index] = message else list.add(message)
    }

    override suspend fun insertUserMessageWithAttachments(message: Message, attachmentIds: List<String>) {
        insertMessage(message)
    }

    override suspend fun updateMessageContent(
        messageId: String,
        content: String,
        reasoning: String?,
        updatedAt: Long
    ) {
        replaceMessage(messageId) { it.copy(content = content, reasoning = reasoning, updatedAt = updatedAt) }
    }

    override suspend fun finalizeMessage(
        messageId: String,
        status: MessageStatus,
        content: String,
        reasoning: String?,
        usage: TokenUsage?,
        finishReason: String?,
        errorKind: String?,
        errorDetail: String?,
        updatedAt: Long
    ) {
        replaceMessage(messageId) {
            it.copy(
                status = status,
                content = content,
                reasoning = reasoning,
                usage = usage,
                finishReason = finishReason,
                errorKind = errorKind,
                errorDetail = errorDetail,
                updatedAt = updatedAt
            )
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        messages.values.forEach { it.removeAll { m -> m.id == messageId } }
    }

    override suspend fun deleteMessagesFrom(messageId: String) {
        messages.values.forEach { list ->
            val index = list.indexOfFirst { it.id == messageId }
            if (index >= 0) list.subList(index, list.size).clear()
        }
    }

    override suspend fun deleteAllMessages(conversationId: String) {
        messages[conversationId]?.clear()
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        MutableStateFlow(messages[conversationId]?.toList().orEmpty())

    override suspend fun lastAssistantMessage(conversationId: String): Message? =
        messages[conversationId]?.lastOrNull { it.role == MessageRole.ASSISTANT }

    override suspend fun sweepStreamingMessages() {
        messages.values.forEach { list ->
            list.replaceAll { m ->
                if (m.status == MessageStatus.STREAMING) {
                    m.copy(status = MessageStatus.SUCCESS, content = m.content)
                } else {
                    m
                }
            }
        }
    }

    override fun observeConversation(id: String): Flow<Conversation?> =
        MutableStateFlow(conversations[id])

    override suspend fun deleteAllConversations() {
        conversations.clear()
        messages.clear()
    }

    private fun replaceMessage(messageId: String, transform: (Message) -> Message) {
        messages.values.forEach { list ->
            val index = list.indexOfFirst { it.id == messageId }
            if (index >= 0) list[index] = transform(list[index])
        }
    }
}

private class FakeAssistantRepository : AssistantRepository {
    val assistants = LinkedHashMap<String, Assistant>()

    override fun observeAll(): Flow<List<Assistant>> =
        MutableStateFlow(assistants.values.toList())

    override suspend fun get(id: String): Assistant? = assistants[id]

    override suspend fun upsert(assistant: Assistant): Assistant {
        assistants[assistant.id] = assistant
        return assistant
    }

    override suspend fun delete(id: String) {
        assistants.remove(id)
    }

    override suspend fun clear() {
        assistants.clear()
    }

    override suspend fun count(): Int = assistants.size

    override suspend fun seedIfEmpty(seed: AssistantSeed?) = Unit
}

private class FakeProviderRepository : ProviderRepository {
    val configs = LinkedHashMap<String, ProviderConfig>()
    val keys = LinkedHashMap<String, String>()

    override fun observeAll(): Flow<List<ProviderConfig>> =
        MutableStateFlow(configs.values.toList())

    override suspend fun get(id: String): ProviderConfig? = configs[id]

    override suspend fun resolve(id: String): ResolvedProvider? =
        configs[id]?.let { ResolvedProvider(it, keys[id]) }

    override suspend fun resolveDefault(): ResolvedProvider? = resolve(configs.keys.firstOrNull() ?: "")

    override suspend fun defaultProviderId(): String? = configs.keys.firstOrNull()

    override suspend fun setDefaultProvider(id: String) = Unit

    override suspend fun upsert(
        config: ProviderConfig,
        newApiKey: String?,
        clearApiKey: Boolean
    ) {
        configs[config.id] = config
        when {
            clearApiKey -> keys.remove(config.id)
            !newApiKey.isNullOrBlank() -> keys[config.id] = newApiKey.orEmpty()
        }
    }

    override suspend fun delete(id: String) {
        configs.remove(id)
        keys.remove(id)
    }

    override suspend fun duplicate(id: String): String {
        val source = configs[id] ?: error("missing provider $id")
        val copy = source.copy(id = "${id}-copy", name = "${source.name} copy")
        configs[copy.id] = copy
        keys[id]?.let { keys[copy.id] = it }
        return copy.id
    }

    override suspend fun markValidated(id: String, at: Long) {
        upsert(configs[id]!!, null, false)
    }

    override suspend fun clearAll() {
        configs.clear()
        keys.clear()
    }

    override suspend fun count(): Int = configs.size
}

class BackupServiceTest {

    private val codec = BackupCodec()
    private val conversations = FakeConversationRepository()
    private val assistants = FakeAssistantRepository()
    private val providers = FakeProviderRepository()
    private val service = BackupService(conversations, assistants, providers, codec)

    private suspend fun seed() {
        conversations.upsert(
            Conversation(
                id = "c1",
                title = "Thread One",
                createdAt = 10L,
                updatedAt = 20L,
                systemPrompt = "be brief"
            )
        )
        conversations.insertMessage(
            Message(id = "m1", conversationId = "c1", role = MessageRole.USER, content = "hi", createdAt = 11L)
        )
        assistants.upsert(
            Assistant(id = "a1", name = "Helper", emoji = "🤖", systemPrompt = "help")
        )
        providers.upsert(
            ProviderConfig(
                id = "p1",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                defaultModelId = "gpt-4o-mini"
            ),
            newApiKey = "sk-literal"
        )
    }

    @Test
    fun `export excludes api keys by default`() = runTest {
        seed()
        val payload = service.export(includeApiKeys = false)
        assertTrue(!payload.contains("sk-literal"))
        assertNull(codec.decode(payload)?.providerKeys?.get("p1"))
    }

    @Test
    fun `export includes keys only when asked`() = runTest {
        seed()
        val payload = service.export(includeApiKeys = true)
        assertEquals("sk-literal", codec.decode(payload)?.providerKeys?.get("p1"))
    }

    @Test
    fun `import adds everything and reports counts`() = runTest {
        seed()
        val payload = service.export(includeApiKeys = true)

        conversations.deleteAllConversations()
        assistants.clear()
        providers.clearAll()

        val summary = service.import(payload)
        assertEquals(true, summary != null)
        assertEquals(1, summary!!.conversationsAdded)
        assertEquals(0, summary.conversationsMerged)
        assertEquals(1, summary.assistantsAdded)
        assertEquals(1, summary.providersAdded)
        assertEquals("sk-literal", providers.keys["p1"])
        assertEquals(listOf("hi"), conversations.messages["c1"]?.map { it.content })
    }

    @Test
    fun `importing again merges instead of duplicating`() = runTest {
        seed()
        val payload = service.export(includeApiKeys = false)

        conversations.deleteAllConversations()
        assistants.clear()
        providers.clearAll()

        val first = service.import(payload)
        assertEquals(1, first!!.conversationsAdded)
        val second = service.import(payload)
        assertEquals(1, second!!.conversationsMerged)
        assertEquals(0, second.conversationsAdded)
        assertEquals(1, conversations.conversations.size)
        // Same-id messages replace (Room semantics) instead of duplicating.
        assertEquals(1, conversations.messages["c1"]?.size)
        assertEquals(1, second.assistantsMerged)
        assertEquals(1, second.providersMerged)
    }

    @Test
    fun `import rejects junk payloads`() = runTest {
        assertNull(service.import("garbage"))
    }
}
