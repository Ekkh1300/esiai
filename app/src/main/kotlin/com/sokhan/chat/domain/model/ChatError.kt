package com.sokhan.chat.domain.model

/**
 * Friendly, localisable error taxonomy for every provider-side failure mode.
 * UI layers map each case to a resource string; stack traces are never shown.
 */
sealed class ChatError(message: String? = null) : Exception(message) {

    /** API key rejected (401/403). */
    class Auth(val detail: String? = null) : ChatError(detail)

    /** Rate limit / quota (429). */
    class RateLimit(val detail: String? = null) : ChatError(detail)

    /** The requested model does not exist on this provider. */
    class ModelNotFound(val model: String, val detail: String? = null) : ChatError(detail)

    /** HTTP 404 on chat endpoint — usually wrong base URL. */
    class NotFound(val detail: String? = null) : ChatError(detail)

    /** HTTP 4xx other than auth/notfound. */
    class BadRequest(val detail: String? = null) : ChatError(detail)

    /** HTTP 5xx. */
    class Server(val code: Int, val detail: String? = null) : ChatError(detail)

    /** Read/connect timeout. */
    class Timeout(val detail: String? = null) : ChatError(detail)

    /** DNS / TLS / connection refused — the host could not be contacted. */
    class Unreachable(val detail: String? = null) : ChatError(detail)

    /** No active network connectivity. */
    object Offline : ChatError(null)

    /** API returned a body we could not parse. */
    class Malformed(val detail: String? = null) : ChatError(detail)

    /** Provider streamed headers/frames but produced no content. */
    object EmptyResponse : ChatError(null)

    /** Requested capability not supported by the selected model/provider. */
    class UnsupportedInput(val model: String, val kind: String) : ChatError(kind)

    /** User stopped generation. */
    object Cancelled : ChatError(null)

    /** Saved provider config is broken (e.g. invalid base URL). */
    class InvalidConfiguration(val detail: String? = null) : ChatError(detail)

    /** Internal missing provider — should not happen in normal UI flow. */
    object NoProvider : ChatError(null)

    object NoModel : ChatError(null)

    /** Anything else. */
    class Unknown(val original: Throwable? = null, detail: String? = null) : ChatError(detail)
}
