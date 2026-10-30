package com.sokhan.chat.domain.service

import com.sokhan.chat.domain.model.ChatError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine falls back from native `tools` to the text protocol — and then to
 * plain chat — only when a failure looks like "this endpoint cannot take a
 * tools array". Getting this predicate wrong either hides real errors or
 * throws away a perfectly good agent turn.
 */
class ToolFallbackPolicyTest {

    private fun rejected(error: ChatError) =
        isToolRejectionFor(error, hasContent = false, hasToolCalls = false)

    @Test
    fun `an unsupported tools payload is a tool rejection`() {
        // OpenAI / llama.cpp style
        assertTrue(rejected(ChatError.BadRequest("tools is not supported")))
        // OpenRouter: "No endpoints found that support tool use."
        assertTrue(rejected(ChatError.NotFound("No endpoints found that support tool use.")))
        // Gateways that swallow the field and answer with an empty stream
        assertTrue(rejected(ChatError.Malformed("bad json")))
        assertTrue(rejected(ChatError.EmptyResponse))
    }

    @Test
    fun `auth rate limit and transport failures are not tool rejections`() {
        assertFalse(rejected(ChatError.Auth("bad key")))
        assertFalse(rejected(ChatError.RateLimit("slow down")))
        assertFalse(rejected(ChatError.Server(500, "boom")))
        assertFalse(rejected(ChatError.Timeout("timeout")))
        assertFalse(rejected(ChatError.Unreachable("dns")))
    }

    @Test
    fun `a turn that already produced output is never retried`() {
        // Retrying would duplicate the text the user already saw.
        assertFalse(
            isToolRejectionFor(
                ChatError.BadRequest("tools"),
                hasContent = true,
                hasToolCalls = false
            )
        )
        // A tool call already recovered from the stream is real progress.
        assertFalse(
            isToolRejectionFor(
                ChatError.EmptyResponse,
                hasContent = false,
                hasToolCalls = true
            )
        )
    }
}
