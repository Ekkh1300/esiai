package com.sokhan.chat.data.export

import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders one conversation as a portable Markdown document (used for
 * share/save of chat threads). Mirrors the flat "Username: content" pattern
 * popularised by clients like Open WebUI while adding reasoning/use lines.
 */
object ExportFormatter {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun toMarkdown(conversation: Conversation, messages: List<Message>): String {
        val builder = StringBuilder()
        builder.append("# ").append(conversation.title.ifBlank { "Conversation" }).append('\n')
        builder.append("> Exported from Sokhan · ").append(now())
        builder.append(" · ").append(messages.size).append(" messages\n\n")

        conversation.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            builder.append("**System prompt**\n\n")
            builder.append("```text\n")
            builder.append(prompt.trimIndent())
            builder.append("\n```\n\n")
        }

        messages.forEach { message ->
            val author = when (message.role) {
                MessageRole.SYSTEM -> "System"
                MessageRole.USER -> "You"
                MessageRole.ASSISTANT -> message.modelId ?: "Assistant"
                MessageRole.TOOL -> "Tool"
            }
            builder.append("### ").append(author)
            builder.append(" · ").append(stamp(Instant.ofEpochMilli(message.createdAt)))
            builder.append("\n\n")
            message.reasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
                builder.append("<details><summary>Reasoning</summary>\n\n")
                builder.append("```\n").append(reasoning).append("\n```\n")
                builder.append("</details>\n\n")
            }
            builder.append(message.content.takeIf { it.isNotBlank() }
                ?: message.errorDetail ?: "_empty_")
            message.usage?.totalTokens?.let { usage ->
                builder.append("\n\n*~").append(usage).append(" tokens*")
            }
            builder.append("\n\n---\n\n")
        }
        return builder.toString()
    }

    fun fileNameFor(conversation: Conversation): String {
        val clean = conversation.title.trim()
            .replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF \\-]"), "")
            .replace(Regex(" +"), "-")
            .take(40)
            .ifBlank { "conversation" }
        return "sokhan-$clean.md"
    }

    private fun stamp(at: Instant): String = stamp.format(at.atZone(ZoneOffset.UTC))

    private fun now(): String = stamp(Instant.now())
}
