package com.sokhan.chat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sokhan.chat.data.db.dao.AssistantDao
import com.sokhan.chat.data.db.dao.AttachmentDao
import com.sokhan.chat.data.db.dao.ConversationDao
import com.sokhan.chat.data.db.dao.MessageDao
import com.sokhan.chat.data.db.dao.ModelDao
import com.sokhan.chat.data.db.dao.ProviderDao
import com.sokhan.chat.data.db.entity.AssistantEntity
import com.sokhan.chat.data.db.entity.AttachmentEntity
import com.sokhan.chat.data.db.entity.ConversationEntity
import com.sokhan.chat.data.db.entity.MessageEntity
import com.sokhan.chat.data.db.entity.ModelEntity
import com.sokhan.chat.data.db.entity.ProviderEntity

@Database(
    version = 2,
    exportSchema = true,
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        AssistantEntity::class
    ]
)
abstract class SokhanDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun assistantDao(): AssistantDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        /**
         * v1 → v2 (agent mode): the tool calls an assistant message made and
         * the id of the call a TOOL message answers. Nullable, no backfill —
         * every existing row simply reads as "plain chat".
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCalls TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolName TEXT")
            }
        }
    }
}
