package com.sokhan.chat.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    indices = [Index("messageId")],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    /** null while pending (draft attached to the composer, not sent yet). */
    val messageId: String? = null,
    /** [com.sokhan.chat.domain.model.AttachmentKind] name. */
    val kind: String,
    val name: String,
    val mimeType: String? = null,
    /** Path relative to the app files dir. */
    val relativePath: String,
    val sizeBytes: Long = 0L,
    val createdAt: Long = 0L
)
