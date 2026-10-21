package com.sokhan.chat.data.export

import kotlinx.serialization.json.Json

/**
 * Encode/decode of Sokhan backups. Kept separate from the repositories so the
 * codec is easy to unit test in isolation.
 */
class BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(bundle: BackupBundle): String =
        json.encodeToString(BackupBundle.serializer(), bundle)

    /** @return null when the payload is not a valid Sokhan backup. */
    fun decode(payload: String): BackupBundle? {
        val trimmed = payload.trimStart('\uFEFF').trim()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching {
            json.decodeFromString(BackupBundle.serializer(), trimmed)
        }.getOrNull() ?: return null
        if (parsed.schemaVersion > BACKUP_SCHEMA_VERSION) return null
        return parsed
    }
}
