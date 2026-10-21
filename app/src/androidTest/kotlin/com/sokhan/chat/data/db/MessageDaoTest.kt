package com.sokhan.chat.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sokhan.chat.data.db.dao.MessageDao
import com.sokhan.chat.data.db.entity.ConversationEntity
import com.sokhan.chat.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var db: SokhanDatabase
    private lateinit var messages: MessageDao

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SokhanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messages = db.messageDao()
        // Foreign keys require the parent row to exist.
        db.conversationDao().upsert(
            ConversationEntity(id = "c1", title = "Chat", createdAt = 1L, updatedAt = 1L)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun message(
        id: String,
        createdAt: Long,
        role: String = "USER",
        content: String = "text $id",
        status: String = "SUCCESS"
    ) = MessageEntity(
        id = id,
        conversationId = "c1",
        role = role,
        content = content,
        createdAt = createdAt,
        status = status
    )

    @Test
    fun observe_sortsByCreatedAtThenId() = runBlocking {
        messages.upsert(message("z", createdAt = 10L))
        messages.upsert(message("a", createdAt = 10L))
        messages.upsert(message("m", createdAt = 5L))

        val rows = messages.observe("c1").first()

        assertEquals(listOf("m", "a", "z"), rows.map { it.id })
    }

    @Test
    fun lastAssistant_returnsNewestAssistantOnly() = runBlocking {
        messages.upsert(message("u1", createdAt = 1L, role = "USER"))
        messages.upsert(message("a1", createdAt = 2L, role = "ASSISTANT"))
        messages.upsert(message("u2", createdAt = 3L, role = "USER"))
        messages.upsert(message("a2", createdAt = 4L, role = "ASSISTANT"))

        val last = messages.lastAssistant("c1")

        assertEquals("a2", last?.id)
    }

    @Test
    fun lastAssistant_returnsNullWithoutAssistants() = runBlocking {
        messages.upsert(message("u1", createdAt = 1L, role = "USER"))

        assertNull(messages.lastAssistant("c1"))
    }

    @Test
    fun replaceStrategy_upsertsSameIdWithoutDuplicates() = runBlocking {
        messages.upsert(message("m1", createdAt = 1L, content = "draft"))
        messages.upsert(message("m1", createdAt = 1L, content = "draft done"))

        assertEquals(1, messages.count("c1"))
        assertEquals("draft done", messages.get("m1")?.content)
    }

    @Test
    fun updateContent_touchesContentReasoningAndUpdatedAt() = runBlocking {
        messages.upsert(message("m1", createdAt = 1L))

        messages.updateContent("m1", "edited", "reasoning chain", updatedAt = 77L)

        val row = messages.get("m1")!!
        assertEquals("edited", row.content)
        assertEquals("reasoning chain", row.reasoning)
        assertEquals(77L, row.updatedAt)
    }

    @Test
    fun finalize_writesStatusUsageAndErrorFields() = runBlocking {
        messages.upsert(message("m1", createdAt = 1L, status = "STREAMING", content = "partial"))

        messages.finalize(
            id = "m1",
            status = "SUCCESS",
            content = "full answer",
            reasoning = "thought",
            usagePrompt = 11,
            usageCompletion = 22,
            usageTotal = 33,
            finishReason = "stop",
            errorKind = null,
            errorDetail = null,
            updatedAt = 88L
        )

        val row = messages.get("m1")!!
        assertEquals("SUCCESS", row.status)
        assertEquals("full answer", row.content)
        assertEquals(11, row.usagePromptTokens)
        assertEquals(22, row.usageCompletionTokens)
        assertEquals(33, row.usageTotalTokens)
        assertEquals("stop", row.finishReason)
        assertNull(row.errorKind)
    }

    @Test
    fun allStreaming_listsOnlyStreamingRows() = runBlocking {
        messages.upsert(message("m1", createdAt = 1L, status = "STREAMING"))
        messages.upsert(message("m2", createdAt = 2L, status = "SUCCESS"))
        messages.upsert(message("m3", createdAt = 3L, status = "ERROR"))

        val streaming = messages.allStreaming()

        assertEquals(listOf("m1"), streaming.map { it.id })
    }

    @Test
    fun deleteFrom_removesStrictlyAfterAnchorWithIdTieBreak() = runBlocking {
        messages.upsert(message("a", createdAt = 10L))
        messages.upsert(message("b", createdAt = 10L))
        messages.upsert(message("c", createdAt = 10L))
        messages.upsert(message("d", createdAt = 20L))

        // Anchor is the middle row: same-timestamp rows with greater id go too.
        messages.deleteFrom("c1", anchorCreatedAt = 10L, anchorId = "b")

        val remaining = messages.observe("c1").first().map { it.id }
        assertEquals(listOf("a", "b"), remaining)
    }

    @Test
    fun deleteForConversation_clearsOnlyThatConversation() = runBlocking {
        db.conversationDao().upsert(
            ConversationEntity(id = "c2", title = "Other", createdAt = 1L, updatedAt = 1L)
        )
        messages.upsert(message("m1", createdAt = 1L))
        messages.upsert(
            MessageEntity(id = "m2", conversationId = "c2", role = "USER", content = "keep", createdAt = 2L)
        )

        messages.deleteForConversation("c1")

        assertEquals(0, messages.count("c1"))
        assertEquals(1, messages.count("c2"))
        assertNotNull(messages.get("m2"))
    }

    @Test
    fun deleteAll_clearsEverything() = runBlocking {
        messages.upsert(message("m1", createdAt = 1L))

        messages.deleteAll()

        assertEquals(0, messages.count("c1"))
    }
}
