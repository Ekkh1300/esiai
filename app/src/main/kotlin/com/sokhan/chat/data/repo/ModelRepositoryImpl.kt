package com.sokhan.chat.data.repo

import com.sokhan.chat.data.db.DbMappers.newModelEntityId
import com.sokhan.chat.data.db.DbMappers.toEntity
import com.sokhan.chat.data.db.dao.ModelDao
import com.sokhan.chat.data.db.DbMappers.toDomain
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelRepositoryImpl(
    private val dao: ModelDao
) : ModelRepository {

    override fun observeForProvider(providerId: String): Flow<List<ChatModel>> =
        dao.observeAll(providerId).map { list -> list.map { it.toDomain() } }

    override fun observeVisibleForProvider(providerId: String): Flow<List<ChatModel>> =
        dao.observeVisible(providerId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(model: ChatModel) {
        dao.upsert(model.copy(id = model.id).toEntity())
    }

    override suspend fun upsertAll(models: List<ChatModel>) {
        dao.upsertAll(models.map { it.toEntity() })
    }

    override suspend fun delete(model: ChatModel) {
        dao.delete(model.id)
    }

    override suspend fun setHidden(providerId: String, modelId: String, hidden: Boolean) {
        dao.setHidden(providerId, modelId, hidden)
    }

    override suspend fun updateCapabilities(model: ChatModel) {
        val entity = model.toEntity()
        dao.updateCapabilities(
            id = entity.id,
            capVision = entity.capVision,
            capTools = entity.capTools,
            capStreaming = entity.capStreaming,
            capTextFiles = entity.capTextFiles,
            capPdf = entity.capPdf,
            capStructured = entity.capStructured,
            capReasoning = entity.capReasoning,
            contextLength = entity.contextLength,
            displayName = entity.displayName
        )
    }

    override suspend fun get(providerId: String, modelId: String): ChatModel? =
        dao.get(providerId, modelId)?.toDomain()

    override suspend fun anyForProvider(providerId: String): List<ChatModel> =
        dao.listForProvider(providerId).map { it.toDomain() }

    override suspend fun clearForProvider(providerId: String) {
        dao.deleteForProvider(providerId)
    }

    companion object {
        fun modelEntityId(providerId: String, modelId: String): String =
            newModelEntityId(providerId, modelId)
    }
}
