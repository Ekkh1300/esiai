package com.sokhan.chat.data.repo

import com.sokhan.chat.core.Ids
import com.sokhan.chat.data.db.DbMappers.toDomain
import com.sokhan.chat.data.db.DbMappers.toEntity
import com.sokhan.chat.data.db.dao.AssistantDao
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.BundledAssistants
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.AssistantSeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Personas stored in the assistants table. Seeded once with the four bundled
 * assistants, in the user's current language.
 */
class AssistantRepositoryImpl(
    private val dao: AssistantDao
) : AssistantRepository {

    override fun observeAll(): Flow<List<Assistant>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): Assistant? = dao.get(id)?.toDomain()

    override suspend fun upsert(assistant: Assistant): Assistant {
        val stamped = assistant.copy(updatedAt = System.currentTimeMillis())
        dao.upsert(stamped.toEntity())
        return stamped
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun clear() {
        dao.deleteAll()
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun seedIfEmpty(seed: AssistantSeed?) {
        val current = runCatching { dao.observeAll().first() }.getOrDefault(emptyList())
        if (current.isNotEmpty() || seed == null) return
        val now = System.currentTimeMillis()
        val seeded = listOf(
            bundled(BundledAssistants.GENERAL_KEY, seed.generalName, BundledAssistants.GENERAL_EMOJI, seed.generalPrompt, now),
            bundled(BundledAssistants.CODER_KEY, seed.coderName, BundledAssistants.CODER_EMOJI, seed.coderPrompt, now),
            bundled(BundledAssistants.TRANSLATOR_KEY, seed.translatorName, BundledAssistants.TRANSLATOR_EMOJI, seed.translatorPrompt, now),
            bundled(BundledAssistants.RESEARCH_KEY, seed.researcherName, BundledAssistants.RESEARCH_EMOJI, seed.researcherPrompt, now)
        )
        dao.upsertAll(seeded.map { it.toEntity() })
    }

    private fun bundled(
        id: String,
        name: String,
        emoji: String,
        prompt: String,
        now: Long
    ): Assistant = Assistant(
        id = id,
        name = name,
        emoji = emoji,
        systemPrompt = prompt,
        createdAt = now,
        updatedAt = now
    )
}
