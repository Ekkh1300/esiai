package com.sokhan.chat.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path for agent mode: a v1 database must migrate to v2 with the
 * new tool columns, and every existing row has to keep reading as plain chat.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SokhanDatabase::class.java
    )

    @Test
    fun migrate1To2AddsToolColumnsWithoutTouchingRows() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO conversations (id, title, createdAt, updatedAt, pinned, archived) " +
                    "VALUES ('c1', 'Old chat', 1000, 1000, 0, 0)"
            )
            execSQL(
                "INSERT INTO messages (id, conversationId, role, content, createdAt, status) " +
                    "VALUES ('m1', 'c1', 'USER', 'hello', 1000, 'SUCCESS')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            SokhanDatabase.MIGRATION_1_2
        )

        db.query("SELECT content, toolCalls, toolCallId, toolName FROM messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("hello", cursor.getString(0))
            // New columns exist and default to NULL: plain chat stays plain.
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
        }
        db.close()
    }

    @Test
    fun migrate1To2KeepsTheToolColumnsQueryable() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO conversations (id, title, createdAt, updatedAt, pinned, archived) " +
                    "VALUES ('c1', 'Old chat', 1000, 1000, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            SokhanDatabase.MIGRATION_1_2
        )
        // The v2 shape must accept a tool row (agent mode on a fresh install).
        db.execSQL(
            "INSERT INTO messages (id, conversationId, role, content, createdAt, status, " +
                "toolCallId, toolName) " +
                "VALUES ('m2', 'c1', 'TOOL', 'ok', 1, 'SUCCESS', 'c9', 'set_theme')"
        )
        db.query("SELECT toolName FROM messages WHERE toolCallId = 'c9'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("set_theme", cursor.getString(0))
        }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
