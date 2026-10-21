package com.sokhan.chat.provider.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Best-effort extraction of a human-readable provider error description from
 * any of the common body shapes:
 *  - {"error":{"message":"…","code":"…"}}
 *  - {"error":"…"}
 *  - {"detail":"…"}   (FastAPI-style gateways)
 *  - {"message":"…"}
 *  - arbitrary-ish bodies
 */
internal fun parseErrorDetail(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val parsed: JsonElement = runCatching { Json.parseToJsonElement(body) }.getOrNull()
        ?: return body.take(180)
    val root = parsed as? JsonObject ?: return body.take(180)
    val errorValue = root["error"]
    val detail: String? = when {
        errorValue is JsonPrimitive -> errorValue.content
        errorValue is JsonObject ->
            (errorValue["message"] as? JsonPrimitive)?.content
                ?.takeIf { it.isNotBlank() }
                ?: (errorValue["code"] as? JsonPrimitive)?.content
        else -> (root["detail"] as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() }
            ?: (root["message"] as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() }
    }
    return detail ?: body.take(180)
}
