package com.sokhan.chat.data.export

import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.TokenUsage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatterTest {

    private fun conversation(title: String = "Sokhan chat") = Conversation(
        id = "c1",
        title = title,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        systemPrompt = "You are concise."
    )

    @Test
    fun `markdown contains the title and role headers`() {
        val markdown = ExportFormatter.toMarkdown(
            conversation(),
            listOf(
                Message(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "What is Kotlin?",
                    createdAt = 1L
                ),
                Message(
                    id = "m2",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "A language for the JVM.",
                    modelId = "gpt-test",
                    createdAt = 2L,
                    status = MessageStatus.SUCCESS
                )
            )
        )
        assertTrue(markdown.startsWith("# Sokhan chat"))
        assertTrue(markdown.contains("### You"))
        assertTrue(markdown.contains("### gpt-test"))
        assertTrue(markdown.contains("What is Kotlin?"))
        assertTrue(markdown.contains("A language for the JVM."))
        assertTrue(markdown.contains("Exported from Sokhan"))
        assertTrue(markdown.contains("2 messages"))
    }

    @Test
    fun `assistant messages fall back to the Assistant name when no model`() {
        val markdown = ExportFormatter.toMarkdown(
            conversation(),
            listOf(
                Message(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "hello",
                    createdAt = 1L
                )
            )
        )
        assertTrue(markdown.contains("### Assistant"))
    }

    @Test
    fun `blank conversation titles fall back to a generic label`() {
        val markdown = ExportFormatter.toMarkdown(
            conversation(title = ""),
            listOf(
                Message(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "hi",
                    createdAt = 1L
                )
            )
        )
        assertTrue(markdown.startsWith("# Conversation"))
    }

    @Test
    fun `system prompt is rendered as a fenced block`() {
        val markdown = ExportFormatter.toMarkdown(conversation(), emptyList())
        assertTrue(markdown.contains("`" + "``text"))
        assertTrue(markdown.contains("You are concise."))
    }

    @Test
    fun `reasoning content is embedded in a details block`() {
        val markdown = ExportFormatter.toMarkdown(
            conversation(),
            listOf(
                Message(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "answer",
                    reasoning = "chain of thought",
                    createdAt = 1L
                )
            )
        )
        assertTrue(markdown.contains("<details><summary>Reasoning</summary>"))
        assertTrue(markdown.contains("chain of thought"))
    }

    @Test
    fun `empty messages render the empty marker instead of crashing`() {
        val markdown = ExportFormatter.toMarkdown(
            conversation(),
            listOf(
                Message(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "",
                    createdAt = 1L
                )
            )
        )
        assertTrue(markdown.contains("_empty_"))
        assertFalse(markdown.contains("null"))
    }

    @Test
    fun `usage totals are annotated when known`() {
        val markdown = ExportFormatter.toMarkdown(
            conversation(),
            listOf(
                Message(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "answer",
                    usage = TokenUsage(3, 5, 8),
                    createdAt = 1L
                )
            )
        )
        assertTrue(markdown.contains("~8 tokens"))
    }
}
