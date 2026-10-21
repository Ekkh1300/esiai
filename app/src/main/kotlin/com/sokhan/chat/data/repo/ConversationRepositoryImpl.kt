package com.sokhan.chat.data.repo

import androidx.room.withTransaction
import com.sokhan.chat.data.db.DbMappers.toDomain
import com.sokhan.chat.data.db.DbMappers.toEntity
import com.sokhan.chat.data.db.SokhanDatabase
import com.sokhan.chat.data.db.dao.AttachmentDao
import com.sokhan.chat.data.db.dao.ConversationDao
import com.sokhan.chat.data.db.dao.MessageDao
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.TokenUsage
import com.sokhan.chat.domain.repository.ConversationCardSnapshot
import com.sokhan.chat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepositoryImpl(
    private val db: SokhanDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao
) : ConversationRepository {

    override fun observeCards(archived: Boolean, search: String?): Flow<List<ConversationCardSnapshot>> {
        val normalized = search?.trim().takeUnless { it.isNullOrEmpty() }
        return conversationDao.observeCards(archived, normalized).map { rows ->
            rows.map { it.toDomain() }
        }
    }

    override suspend fun get(id: String): Conversation? = conversationDao.get(id)?.toDomain()

    override suspend fun listAllConversations(): List<Conversation> =
        conversationDao.listAll().map { it.toDomain() }

    override suspend fun upsert(conversation: Conversation) {
        conversationDao.upsert(conversation.toEntity())
    }

    override suspend fun delete(id: String) {
        // Signal cascades wipe messages + attachments they referenced.
        conversationDao.delete(id)
    }

    override suspend fun deleteAllConversations() {
        db.withTransaction {
            attachmentDao.deleteAll()
            messageDao.deleteAll()
            conversationDao.deleteAll()
        }
    }

    override suspend fun rename(id: String, title: String) {
        conversationDao.rename(id, title.trim(), System.currentTimeMillis())
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        conversationDao.setPinned(id, pinned)
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        conversationDao.setArchived(id, archived)
    }

    override suspend fun setModel(id: String, providerId: String, modelId: String, modelLabel: String) {
        conversationDao.setModel(id, providerId, modelId, modelLabel, System.currentTimeMillis())
    }

    override suspend fun setSystemPrompt(id: String, prompt: String?) {
        conversationDao.setSystemPrompt(id, prompt)
    }

    override suspend fun clearMessages(id: String) {
        // Attachment rows cascade with their message.
        messageDao.deleteForConversation(id)
        conversationDao.touch(id, System.currentTimeMillis())
    }

    // ── Messages ────────────────────────────────────────────────────────

    override suspend fun insertMessage(message: Message) {
        db.withTransaction {
            messageDao.upsert(message.toEntity())
            conversationDao.touch(message.conversationId, System.currentTimeMillis())
        }
    }

    override suspend fun upsertMessages(conversationId: String, messages: List<Message>) {
        db.withTransaction {
            messages.forEach { messageDao.upsert(it.toEntity()) }
            conversationDao.touch(conversationId, System.currentTimeMillis())
        }
    }

    override suspend fun insertUserMessageWithAttachments(
        message: Message,
        attachmentIds: List<String>
    ) {
        db.withTransaction {
            messageDao.upsert(message.toEntity())
            if (attachmentIds.isNotEmpty()) {
                attachmentDao.assignToMessage(attachmentIds, message.id)
            }
            conversationDao.touch(message.conversationId, message.createdAt)
        }
    }

    override suspend fun updateMessageContent(
        messageId: String,
        content: String,
        reasoning: String?,
        updatedAt: Long
    ) {
        messageDao.updateContent(messageId, content, reasoning, updatedAt)
        messageDao.get(messageId)?.let { conversationDao.touch(it.conversationId, updatedAt) }
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
        messageDao.finalize(
            id = messageId,
            status = status.name,
            content = content,
            reasoning = reasoning,
            usagePrompt = usage?.promptTokens,
            usageCompletion = usage?.completionTokens,
            usageTotal = usage?.totalTokens,
            finishReason = finishReason,
            errorKind = errorKind,
            errorDetail = errorDetail,
            updatedAt = updatedAt
        )
        messageDao.get(messageId)?.let { conversationDao.touch(it.conversationId, updatedAt) }
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.delete(messageId)
    }

    override suspend fun deleteMessagesFrom(messageId: String) {
        val anchor = messageDao.get(messageId) ?: return
        messageDao.deleteFrom(anchor.conversationId, anchor.createdAt, anchor.id)
        conversationDao.touch(anchor.conversationId, System.currentTimeMillis())
    }

    override suspend fun deleteAllMessages(conversationId: String) {
        clearMessages(conversationId)
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observe(conversationId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun lastAssistantMessage(conversationId: String): Message? =
        messageDao.lastAssistant(conversationId)?.toDomain()

    override suspend fun sweepStreamingMessages() {
        val orphans = messageDao.allStreaming()
        orphans.forEach { row ->
            if (row.content.isBlank()) {
                messageDao.delete(row.id)
            } else {
                messageDao.finalize(
                    id = row.id,
                    status = MessageStatus.PARTIAL.name,
                    content = row.content,
                    reasoning = row.reasoning,
                    usagePrompt = row.usagePromptTokens,
                    usageCompletion = row.usageCompletionTokens,
                    usageTotal = row.usageTotalTokens,
                    finishReason = "stopped",
                    errorKind = null,
                    errorDetail = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    override fun observeConversation(id: String): Flow<Conversation?> =
        conversationDao.observe(id).map { it?.toDomain() }
}
