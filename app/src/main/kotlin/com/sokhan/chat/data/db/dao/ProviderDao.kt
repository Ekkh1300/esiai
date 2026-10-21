package com.sokhan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sokhan.chat.data.db.entity.ModelEntity
import com.sokhan.chat.data.db.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE ASC")
    suspend fun listAll(): List<ProviderEntity>

    @Query("SELECT COUNT(*) FROM providers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM providers")
    suspend fun deleteAll()

    @Query("UPDATE providers SET lastValidatedAt = :at WHERE id = :id")
    suspend fun markValidated(id: String, at: Long)
}

@Dao
interface ModelDao {

    @Query("SELECT * FROM models WHERE providerId = :providerId AND hidden = 0 ORDER BY modelId COLLATE NOCASE ASC")
    fun observeVisible(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY modelId COLLATE NOCASE ASC")
    fun observeAll(providerId: String): Flow<List<ModelEntity>>

    @Query(
        """
        SELECT models.* FROM models
        INNER JOIN providers ON providers.id = models.providerId
        WHERE models.providerId = :providerId
          AND models.hidden = 0
          AND (:heuristic = 0 OR (
              models.modelId NOT LIKE '%embedding%'
              AND models.modelId NOT LIKE '%whisper%'
              AND models.modelId NOT LIKE '%tts%'
              AND models.modelId NOT LIKE '%moderation%'
              AND models.modelId NOT LIKE '%davinci%'
          ))
        ORDER BY models.modelId COLLATE NOCASE ASC
        """
    )
    fun observeVisibleFiltered(providerId: String, heuristic: Boolean): Flow<List<ModelEntity>>

    @Query(
        """
        SELECT models.* FROM models
        INNER JOIN providers ON providers.id = models.providerId
        WHERE models.providerId = :providerId
          AND models.capVision = 1
        ORDER BY models.modelId COLLATE NOCASE ASC
        """
    )
    fun observeVisionCapable(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId AND modelId = :modelId LIMIT 1")
    suspend fun get(providerId: String, modelId: String): ModelEntity?

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY modelId COLLATE NOCASE ASC")
    suspend fun listForProvider(providerId: String): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ModelEntity>)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        UPDATE models
        SET capVision = :capVision, capTools = :capTools, capStreaming = :capStreaming,
            capTextFiles = :capTextFiles, capPdf = :capPdf, capStructured = :capStructured,
            capReasoning = :capReasoning, contextLength = :contextLength, displayName = :displayName
        WHERE id = :id
        """
    )
    suspend fun updateCapabilities(
        id: String,
        capVision: Boolean,
        capTools: Boolean,
        capStreaming: Boolean,
        capTextFiles: Boolean,
        capPdf: Boolean,
        capStructured: Boolean,
        capReasoning: Boolean,
        contextLength: Int?,
        displayName: String?
    )

    @Query(
        """
        UPDATE models
        SET hidden = :hidden
        WHERE providerId = :providerId AND modelId = :modelId
        """
    )
    suspend fun setHidden(providerId: String, modelId: String, hidden: Boolean)

    @Query("DELETE FROM models WHERE providerId = :providerId AND userDefined = 1")
    suspend fun deleteManualFor(providerId: String)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: String)

    @Query("DELETE FROM models")
    suspend fun deleteAll()
}
