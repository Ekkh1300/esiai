package com.sokhan.chat.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "createdAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** [com.sokhan.chat.domain.model.MessageRole] name. */
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val createdAt: Long,
    val updatedAt: Long? = null,
    /** [com.sokhan.chat.domain.model.MessageStatus] name. */
    val status: String = "SUCCESS",
    val modelId: String? = null,
    val providerId: String? = null,
    val usagePromptTokens: Int? = null,
    val usageCompletionTokens: Int? = null,
    val usageTotalTokens: Int? = null,
    val finishReason: String? = null,
    val errorKind: String? = null,
    val errorDetail: String? = null,
    /** JSON array of tool calls made by an assistant message (agent mode). */
    val toolCalls: String? = null,
    /** For `role = "TOOL"` rows: the call id this result answers. */
    val toolCallId: String? = null,
    /** For `role = "TOOL"` rows: the tool that produced this result. */
    val toolName: String? = null
)
