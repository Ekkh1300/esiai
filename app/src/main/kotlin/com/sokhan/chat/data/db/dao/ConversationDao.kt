package com.sokhan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sokhan.chat.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/** Joined projection for the chat list screen. */
data class ConversationCardRow(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val systemPrompt: String?,
    val providerId: String?,
    val modelId: String?,
    val modelLabel: String?,
    val temperature: Double?,
    val maxTokens: Int?,
    val topP: Double?,
    val streaming: Boolean?,
    val assistantId: String?,
    val previewText: String?,
    val messageCount: Int,
    val lastMessageAt: Long?
)

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT c.*,
            (SELECT m.content FROM messages m WHERE m.conversationId = c.id ORDER BY m.createdAt DESC LIMIT 1) AS previewText,
            (SELECT COUNT(*) FROM messages m2 WHERE m2.conversationId = c.id) AS messageCount,
            (SELECT m3.createdAt FROM messages m3 WHERE m3.conversationId = c.id ORDER BY m3.createdAt DESC LIMIT 1) AS lastMessageAt
        FROM conversations c
        WHERE c.archived = :archivedOnly
            AND (:search IS NULL OR c.title LIKE '%' || :search || '%')
        ORDER BY c.pinned DESC, COALESCE(lastMessageAt, c.updatedAt) DESC
        """
    )
    fun observeCards(archivedOnly: Boolean, search: String?): Flow<List<ConversationCardRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("SELECT * FROM conversations")
    suspend fun listAll(): List<ConversationEntity>

    @Query("SELECT id FROM conversations")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query(
        """
        UPDATE conversations
        SET providerId = :providerId, modelId = :modelId, modelLabel = :modelLabel, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun setModel(id: String, providerId: String, modelId: String, modelLabel: String, now: Long)

    @Query("UPDATE conversations SET systemPrompt = :prompt WHERE id = :id")
    suspend fun setSystemPrompt(id: String, prompt: String?)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)
}
