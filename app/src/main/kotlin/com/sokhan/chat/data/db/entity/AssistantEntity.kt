package com.sokhan.chat.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "assistants", indices = [Index("updatedAt")])
data class AssistantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val streaming: Boolean? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
