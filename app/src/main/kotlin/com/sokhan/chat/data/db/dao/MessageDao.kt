package com.sokhan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sokhan.chat.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'ASSISTANT' " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1"
    )
    suspend fun lastAssistant(conversationId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE status = 'STREAMING'")
    suspend fun allStreaming(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun count(conversationId: String): Int

    /** True upsert (no delete-then-insert) so FK rows (attachments) survive updates. */
    @Upsert
    suspend fun upsert(entity: MessageEntity)

    /** True upsert (no delete-then-insert) so FK rows (attachments) survive updates. */
    @Upsert
    suspend fun upsertAll(entities: List<MessageEntity>)

    @Query(
        """
        UPDATE messages
        SET content = :content,
            reasoning = :reasoning,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateContent(id: String, content: String, reasoning: String?, updatedAt: Long)

    @Query(
        """
        UPDATE messages
        SET content = :content,
            reasoning = :reasoning,
            status = :status,
            usagePromptTokens = :usagePrompt,
            usageCompletionTokens = :usageCompletion,
            usageTotalTokens = :usageTotal,
            finishReason = :finishReason,
            errorKind = :errorKind,
            errorDetail = :errorDetail,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun finalize(
        id: String,
        status: String,
        content: String,
        reasoning: String?,
        usagePrompt: Int?,
        usageCompletion: Int?,
        usageTotal: Int?,
        finishReason: String?,
        errorKind: String?,
        errorDetail: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    /** Removes every message AFTER [messageId] (edit / regenerate flows). */
    @Query(
        """
        DELETE FROM messages
        WHERE conversationId = :conversationId
          AND ((createdAt > :anchorCreatedAt) OR (createdAt = :anchorCreatedAt AND id > :anchorId))
        """
    )
    suspend fun deleteFrom(conversationId: String, anchorCreatedAt: Long, anchorId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
