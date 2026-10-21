package com.sokhan.chat.domain.repository

import android.net.Uri
import com.sokhan.chat.domain.model.Attachment
import com.sokhan.chat.domain.model.ChatError
import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.domain.model.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-conversation streaming state surfaced by the [ChatEngine]; the UI leans
 * on it to render "thinking" rows and stop/generate affordances.
 */
data class StreamState(val active: Set<String> = emptySet()) {
    fun isStreaming(conversationId: String): Boolean = active.contains(conversationId)
}

/**
 * Chat orchestration: builds request payloads, drives the provider client,
 * persists messages and manages one running stream per conversation.
 */
interface ChatEngine {
    val streamState: StateFlow<StreamState>

    /** Finalizes STREAMING rows after a process death; call once at startup. */
    suspend fun recoverOrphans()

    /** Starts generating an assistant reply; returns null on success or a typed failure. */
    suspend fun generate(conversationId: String): ChatError?

    /** Stops the running generation for a conversation, keeping any partial reply. */
    fun stop(conversationId: String)

    /** Removes the trailing assistant message and streams a fresh reply. */
    suspend fun regenerate(conversationId: String): ChatError?

    /** Rewrites a user message, drops everything after it, re-streams. */
    suspend fun editAndResend(conversationId: String, messageId: String, newContent: String): ChatError?

    /** Re-streams the last assistant reply for a conversation. */
    suspend fun retry(conversationId: String): ChatError?

    /** Stops every active stream (used on "clear all data"). */
    fun stopAll()
}

/** List-screen projection for the conversation rows. */
data class ConversationCardSnapshot(
    val conversation: Conversation,
    val previewText: String?,
    val messageCount: Int,
    val lastMessageAt: Long?
)

interface ConversationRepository {
    fun observeCards(archived: Boolean, search: String?): Flow<List<ConversationCardSnapshot>>

    suspend fun get(id: String): Conversation?
    suspend fun listAllConversations(): List<Conversation>
    suspend fun upsert(conversation: Conversation)
    suspend fun delete(id: String)
    suspend fun rename(id: String, title: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun setModel(id: String, providerId: String, modelId: String, modelLabel: String)
    suspend fun setSystemPrompt(id: String, prompt: String?)
    suspend fun clearMessages(id: String)

    /** Message persistence (used by the engine and screens). */
    suspend fun insertMessage(message: Message)
    suspend fun insertUserMessageWithAttachments(message: Message, attachmentIds: List<String>)

    /**
     * Atomic batch upsert of a whole message list (backup import). Implemented
     * inside a database transaction; messages keep their attachment relations.
     */
    suspend fun upsertMessages(conversationId: String, messages: List<Message>)
    suspend fun updateMessageContent(
        messageId: String,
        content: String,
        reasoning: String?,
        updatedAt: Long
    )
    suspend fun finalizeMessage(
        messageId: String,
        status: MessageStatus,
        content: String,
        reasoning: String?,
        usage: TokenUsage?,
        finishReason: String?,
        errorKind: String?,
        errorDetail: String?,
        updatedAt: Long
    )
    suspend fun deleteMessage(messageId: String)
    suspend fun deleteMessagesFrom(messageId: String)
    suspend fun deleteAllMessages(conversationId: String)
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun lastAssistantMessage(conversationId: String): Message?

    /** Finalizes STREAMING rows left behind by a process death (app restart). */
    suspend fun sweepStreamingMessages()

    /** Live single-conversation observation for the chat screen. */
    fun observeConversation(id: String): Flow<Conversation?>

    /** Removes every conversation row (used by "clear all data"). */
    suspend fun deleteAllConversations()
}

interface AttachmentRepository {
    fun observeByMessage(messageId: String): Flow<List<Attachment>>
    fun observeForConversation(conversationId: String): Flow<List<Attachment>>
    suspend fun assign(messageId: String, attachmentIds: List<String>)
    suspend fun byId(attachmentId: String): Attachment?
    suspend fun byMessage(messageId: String): List<Attachment>

    /** Copies the picked content into app-private storage as a pending attachment. */
    suspend fun createFromContentUri(uri: Uri, sourceName: String): Result<Attachment>
    suspend fun delete(attachmentId: String)
    suspend fun resolveFile(attachment: Attachment): java.io.File?

    /**
     * Direct, non-suspense file resolution (filesDir + relativePath) — used
     * by UI to hand image models their File without extra coroutines.
     */
    fun attachmentFile(attachment: Attachment): java.io.File?
}
