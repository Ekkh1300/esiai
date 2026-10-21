package com.sokhan.chat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
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
}
