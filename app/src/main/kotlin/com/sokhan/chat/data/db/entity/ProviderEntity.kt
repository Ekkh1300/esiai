package com.sokhan.chat.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted provider configuration. The API key deliberately lives outside
 * Room, in the Keystore-backed secure store (SECURITY.md).
 */
@Entity(
    tableName = "providers",
    indices = [Index(value = ["name"], unique = false)]
)
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.sokhan.chat.domain.model.ProviderKind] name. */
    val kind: String = "OPENAI_COMPATIBLE",
    val baseUrl: String,
    val organization: String? = null,
    val apiVersion: String? = null,
    /** Serialized map of custom headers (Json). */
    val headersJson: String? = null,
    val defaultModelId: String? = null,
    val capTextFiles: Boolean = true,
    val capPdf: Boolean = false,
    val capVision: Boolean = true,
    val capListModels: Boolean = true,
    val defaultTemperature: Double? = null,
    val defaultMaxTokens: Int? = null,
    val defaultTopP: Double? = null,
    val defaultStreaming: Boolean? = null,
    val includeUsageInStream: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastValidatedAt: Long? = null
)
