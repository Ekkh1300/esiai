package com.sokhan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sokhan.chat.data.db.entity.AssistantEntity
import com.sokhan.chat.data.db.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantDao {

    @Query("SELECT * FROM assistants ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AssistantEntity>>

    @Query("SELECT * FROM assistants WHERE id = :id")
    suspend fun get(id: String): AssistantEntity?

    @Query("SELECT COUNT(*) FROM assistants")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AssistantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AssistantEntity>)

    @Query("DELETE FROM assistants WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM assistants")
    suspend fun deleteAll()
}

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE messageId = :messageId order by createdAt ASC")
    fun observeByMessage(messageId: String): Flow<List<AttachmentEntity>>

    @Query(
        """
        SELECT attachments.* FROM attachments
        INNER JOIN messages ON messages.id = attachments.messageId
        WHERE messages.conversationId = :conversationId
        ORDER BY attachments.createdAt ASC
        """
    )
    fun observeForConversation(conversationId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY createdAt ASC")
    suspend fun byMessage(messageId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AttachmentEntity>)

    @Query("UPDATE attachments SET messageId = :messageId WHERE id IN (:ids)")
    suspend fun assignToMessage(ids: List<String>, messageId: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}
