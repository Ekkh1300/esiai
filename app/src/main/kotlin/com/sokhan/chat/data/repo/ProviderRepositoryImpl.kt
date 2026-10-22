package com.sokhan.chat.data.repo

import com.sokhan.chat.data.db.SokhanDatabase
import com.sokhan.chat.data.db.DbMappers.toDomain
import com.sokhan.chat.data.db.DbMappers.toEntity
import com.sokhan.chat.data.db.dao.ModelDao
import com.sokhan.chat.data.db.dao.ProviderDao
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.ModelCapabilities
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.ResolvedProvider
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.core.Ids
import com.sokhan.chat.security.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room + SecureStore backed repositories. The API key never enters Room.
 */
class ProviderRepositoryImpl(
    private val dao: ProviderDao,
    private val modelDao: ModelDao,
    private val secureStore: SecureStore,
    private val settings: SettingsRepository
) : ProviderRepository {

    override fun observeAll(): Flow<List<ProviderConfig>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): ProviderConfig? = dao.get(id)?.toDomain()

    /** Fresh decrypt on every use; merged for [%id] key from SecureStore. */
    override suspend fun resolve(id: String): ResolvedProvider? {
        val stored = dao.get(id)?.toDomain() ?: return null
        return ResolvedProvider(config = stored, apiKey = secureStore.read(id))
    }

    override suspend fun resolveDefault(): ResolvedProvider? {
        val id = defaultProviderId() ?: return null
        return resolve(id)
    }

    override suspend fun defaultProviderId(): String? {
        val configured = settings.settings.value.defaultProviderId
        if (configured != null && dao.get(configured) != null) return configured
        return dao.listAll().firstOrNull()?.id
    }

    override suspend fun setDefaultProvider(id: String) {
        settings.update { it.copy(defaultProviderId = id) }
    }

    override suspend fun upsert(
        config: ProviderConfig,
        newApiKey: String?,
        clearApiKey: Boolean
    ) {
        dao.upsert(config.toEntity())
        when {
            !newApiKey.isNullOrBlank() -> secureStore.write(config.id, newApiKey)
            clearApiKey -> secureStore.delete(config.id)
        }
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
        modelDao.deleteForProvider(id)
        secureStore.delete(id)
    }

    override suspend fun duplicate(id: String): String {
        val original = dao.get(id) ?: error("provider missing: $id")
        val newId = Ids.newId()
        val copy = original.copy(
            id = newId,
            name = original.name + " (copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(copy)
        // Copy the secret into a new store slot for the duplicate.
        secureStore.read(id)?.let { secureStore.write(newId, it) }
        // Models are metadata; copy the manual list too.
        val models = modelDao.listForProvider(id).map { it.toDomain() }
        modelDao.upsertAll(
            models.map { model ->
                model.copy(
                    id = "$newId::${model.modelId}",
                    providerId = newId
                ).toEntity()
            }
        )
        return newId
    }

    override suspend fun markValidated(id: String, at: Long) {
        dao.markValidated(id, at)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
        modelDao.deleteAll()
        secureStore.deleteAll()
    }

    override suspend fun count(): Int = dao.count()
}
