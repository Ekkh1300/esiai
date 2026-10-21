package com.sokhan.chat.core

/**
 * Central logger.
 *
 * Hard rule: never log message contents, API keys or URLs with keys.
 * Debug builds use `println`; release builds are silent (no third-party
 * crash reporters are included with Sokhan).
 */
object AppLog {
    private val debug: Boolean = BuildFlags.DEBUG

    fun d(tag: String, message: String) {
        if (debug) println("[SOKHAN/$tag] $message")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (debug) println("[SOKHAN/$tag] WARN: $message${throwable?.let { " ($it)" } ?: ""}")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (debug) println("[SOKHAN/$tag] ERROR: $message${throwable?.let { " ($it)" } ?: ""}")
    }
}
