package com.sokhan.chat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatErrorKindTest {

    @Test
    fun `of maps every concrete error to its persisted kind`() {
        assertEquals(ChatErrorKind.AUTH, ChatErrorKind.of(ChatError.Auth("bad key")))
        assertEquals(ChatErrorKind.RATE_LIMIT, ChatErrorKind.of(ChatError.RateLimit()))
        assertEquals(ChatErrorKind.MODEL_NOT_FOUND, ChatErrorKind.of(ChatError.ModelNotFound("m1")))
        assertEquals(ChatErrorKind.NOT_FOUND, ChatErrorKind.of(ChatError.NotFound()))
        assertEquals(ChatErrorKind.BAD_REQUEST, ChatErrorKind.of(ChatError.BadRequest()))
        assertEquals(ChatErrorKind.SERVER, ChatErrorKind.of(ChatError.Server(500)))
        assertEquals(ChatErrorKind.TIMEOUT, ChatErrorKind.of(ChatError.Timeout()))
        assertEquals(ChatErrorKind.UNREACHABLE, ChatErrorKind.of(ChatError.Unreachable()))
        assertEquals(ChatErrorKind.OFFLINE, ChatErrorKind.of(ChatError.Offline))
        assertEquals(ChatErrorKind.MALFORMED, ChatErrorKind.of(ChatError.Malformed()))
        assertEquals(ChatErrorKind.EMPTY, ChatErrorKind.of(ChatError.EmptyResponse))
        assertEquals(
            ChatErrorKind.CAPABILITY,
            ChatErrorKind.of(ChatError.UnsupportedInput("m1", "vision"))
        )
        assertEquals(ChatErrorKind.CANCELLED, ChatErrorKind.of(ChatError.Cancelled))
        assertEquals(
            ChatErrorKind.INVALID_CONFIG,
            ChatErrorKind.of(ChatError.InvalidConfiguration("url"))
        )
        assertEquals(ChatErrorKind.NO_PROVIDER, ChatErrorKind.of(ChatError.NoProvider))
        assertEquals(ChatErrorKind.NO_MODEL, ChatErrorKind.of(ChatError.NoModel))
        assertEquals(ChatErrorKind.UNKNOWN, ChatErrorKind.of(ChatError.Unknown(Exception("x"))))
        assertEquals(ChatErrorKind.UNKNOWN, ChatErrorKind.of(null))
    }

    @Test
    fun `safeValueOf falls back to UNKNOWN for unknown or null names`() {
        assertEquals(ChatErrorKind.UNKNOWN, ChatErrorKind.safeValueOf(null))
        assertEquals(ChatErrorKind.UNKNOWN, ChatErrorKind.safeValueOf("GARBLER"))
        assertEquals(ChatErrorKind.AUTH, ChatErrorKind.safeValueOf("AUTH"))
    }

    @Test
    fun `kinds round-trip through their stable name`() {
        ChatErrorKind.entries.forEach { kind ->
            assertEquals(kind, ChatErrorKind.safeValueOf(kind.name))
        }
    }
}
