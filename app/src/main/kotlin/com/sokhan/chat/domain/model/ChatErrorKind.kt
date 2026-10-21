package com.sokhan.chat.domain.model

/**
 * Stable, persistence-friendly labels for [ChatError] cases. Stored alongside
 * provider details on failed messages so the UI can show localised help text.
 */
enum class ChatErrorKind {
    AUTH,
    RATE_LIMIT,
    MODEL_NOT_FOUND,
    NOT_FOUND,
    BAD_REQUEST,
    SERVER,
    TIMEOUT,
    UNREACHABLE,
    OFFLINE,
    MALFORMED,
    EMPTY,
    CAPABILITY,
    CANCELLED,
    NO_PROVIDER,
    NO_MODEL,
    INVALID_CONFIG,
    UNKNOWN;

    companion object {
        fun of(error: ChatError?): ChatErrorKind = when (error) {
            is ChatError.Auth -> AUTH
            is ChatError.RateLimit -> RATE_LIMIT
            is ChatError.ModelNotFound -> MODEL_NOT_FOUND
            is ChatError.NotFound -> NOT_FOUND
            is ChatError.BadRequest -> BAD_REQUEST
            is ChatError.Server -> SERVER
            is ChatError.Timeout -> TIMEOUT
            is ChatError.Unreachable -> UNREACHABLE
            ChatError.Offline -> OFFLINE
            is ChatError.Malformed -> MALFORMED
            ChatError.EmptyResponse -> EMPTY
            is ChatError.UnsupportedInput -> CAPABILITY
            ChatError.Cancelled -> CANCELLED
            is ChatError.InvalidConfiguration -> INVALID_CONFIG
            ChatError.NoProvider -> NO_PROVIDER
            ChatError.NoModel -> NO_MODEL
            is ChatError.Unknown -> UNKNOWN
            null -> UNKNOWN
        }

        fun safeValueOf(name: String?): ChatErrorKind =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}
