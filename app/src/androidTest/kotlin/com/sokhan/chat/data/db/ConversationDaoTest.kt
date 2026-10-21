package com.sokhan.chat.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sokhan.chat.data.db.entity.ConversationEntity
import com.sokhan.chat.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDaoTest {

    private lateinit var db: SokhanDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SokhanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun conversation(
        id: String,
        title: String = "Chat $id",
        pinned: Boolean = false,
        archived: Boolean = false,
        updatedAt: Long
    ) = ConversationEntity(
        id = id,
        title = title,
        createdAt = 1_000L,
        updatedAt = updatedAt,
        pinned = pinned,
        archived = archived
    )

    @Test
    fun observeCards_ordersPinnedFirstThenMostRecent() = runBlocking {
        val dao = db.conversationDao()
        dao.upsert(conversation("a", updatedAt = 300L))
        dao.upsert(conversation("b", updatedAt = 500L))
        dao.upsert(conversation("c", pinned = true, updatedAt = 100L))

        val cards = dao.observeCards(archivedOnly = false, search = null).first()

        assertEquals(listOf("c", "b", "a"), cards.map { it.id })
    }

    @Test
    fun observeCards_joinsPreviewAndMessageCount() = runBlocking {
        val dao = db.conversationDao()
        val messages = db.messageDao()
        dao.upsert(conversation("c1", updatedAt = 100L))
        messages.upsert(
            MessageEntity(id = "m1", conversationId = "c1", role = "USER", content = "first", createdAt = 10L)
        )
        messages.upsert(
            MessageEntity(id = "m2", conversationId = "c1", role = "ASSISTANT", content = "second", createdAt = 20L)
        )

        val card = dao.observeCards(archivedOnly = false, search = null).first().single()

        assertEquals("second", card.previewText)
        assertEquals(2, card.messageCount)
        assertEquals(20L, card.lastMessageAt)
    }

    @Test
    fun observeCards_searchFiltersByTitleSubstring() = runBlocking {
        val dao = db.conversationDao()
        dao.upsert(conversation("a", title = "Kotlin chat", updatedAt = 300L))
        dao.upsert(conversation("b", title = "Persian chat", updatedAt = 400L))

        val hits = dao.observeCards(archivedOnly = false, search = "Kotlin").first()

        assertEquals(listOf("a"), hits.map { it.id })
    }

    @Test
    fun observeCards_separatesArchivedBucket() = runBlocking {
        val dao = db.conversationDao()
        dao.upsert(conversation("live", updatedAt = 300L))
        dao.upsert(conversation("arch", archived = true, updatedAt = 400L))

        assertEquals(listOf("live"), dao.observeCards(false, null).first().map { it.id })
        assertEquals(listOf("arch"), dao.observeCards(true, null).first().map { it.id })
    }

    @Test
    fun rename_updatesTitleAndTimestamp() = runBlocking {
        val dao = db.conversationDao()
        dao.upsert(conversation("c1", title = "Old", updatedAt = 1L))

        dao.rename("c1", "New title", now = 99L)

        val entity = dao.get("c1")!!
        assertEquals("New title", entity.title)
        assertEquals(99L, entity.updatedAt)
    }

    @Test
    fun setModel_persistsProviderModelAndLabel() = runBlocking {
        val dao = db.conversationDao()
        dao.upsert(conversation("c1", updatedAt = 1L))

        dao.setModel("c1", "p1", "gpt-test", "GPT Test", now = 9L)

        val entity = dao.get("c1")!!
        assertEquals("p1", entity.providerId)
        assertEquals("gpt-test", entity.modelId)
        assertEquals("GPT Test", entity.modelLabel)
        assertEquals(9L, entity.updatedAt)
    }

    @Test
    fun delete_cascadesToMessages() = runBlocking {
        val dao = db.conversationDao()
        val messages = db.messageDao()
        dao.upsert(conversation("c1", updatedAt = 1L))
        messages.upsert(
            MessageEntity(id = "m1", conversationId = "c1", role = "USER", content = "hi", createdAt = 2L)
        )

        dao.delete("c1")

        assertEquals(0, messages.count("c1"))
        assertNull(dao.get("c1"))
    }

    @Test
    fun count_reflectsRows() = runBlocking {
        val dao = db.conversationDao()
        assertEquals(0, dao.count())
        dao.upsertAll(listOf(conversation("a", updatedAt = 1L), conversation("b", updatedAt = 2L)))
        assertEquals(2, dao.count())
        assertTrue(dao.allIds().containsAll(listOf("a", "b")))
    }
}
