package com.sokhan.chat.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One known model of a provider (fetched or defined manually by the user).
 */
@Entity(
    tableName = "models",
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "modelId"], unique = true)
    ]
)
data class ModelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    /** External id, e.g. "gpt-4o-mini". */
    val modelId: String,
    val displayName: String? = null,
    val contextLength: Int? = null,
    val userDefined: Boolean = false,
    val hidden: Boolean = false,
    val capVision: Boolean = false,
    val capTools: Boolean = false,
    val capStreaming: Boolean = true,
    val capTextFiles: Boolean = false,
    val capPdf: Boolean = false,
    val capStructured: Boolean = false,
    val capReasoning: Boolean = false,
    val createdAt: Long = 0L
)
