package com.sokhan.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.Attachment
import com.sokhan.chat.domain.model.AttachmentKind
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Callbacks a chat row can trigger. */
data class ChatMessageActions(
    val onCopy: (String) -> Unit,
    val onShare: (Message) -> Unit,
    val onEdit: (Message) -> Unit,
    val onDelete: (Message) -> Unit,
    val onRegenerate: () -> Unit
)

@Composable
fun ChatMessageBubble(
    message: Message,
    attachments: List<Attachment>,
    resolvedPath: (Attachment) -> File?,
    modelLabel: String? = null,
    markdownEnabled: Boolean,
    codeHighlighting: Boolean,
    isLastAssistant: Boolean,
    isStreaming: Boolean,
    actions: ChatMessageActions
) {
    val isUser = message.role == MessageRole.USER
    var actionsOpen by remember(message.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = modelLabel ?: message.modelId ?: "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        val bubbleColor = when {
            isUser -> MaterialTheme.colorScheme.primaryContainer
            message.status == MessageStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val bubbleShape = RoundedCornerShape(
            topStart = if (isUser) 18.dp else 4.dp,
            topEnd = if (isUser) 4.dp else 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        )

        Column(
            modifier = Modifier
                // Wrap the content (so the bubble hugs its text and the
                // column alignment decides which side it sits on) but never
                // grow wider than a comfortable reading column.
                .widthIn(max = 340.dp)
                .clip(bubbleShape)
                .clickable { actionsOpen = !actionsOpen }
                .background(bubbleColor)
                .then(
                    if (isUser) {
                        Modifier
                    } else {
                        // A hairline keeps the assistant bubble readable on
                        // top of a busy background.
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = bubbleShape
                        )
                    }
                )
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            if (!isUser && !message.reasoning.isNullOrBlank()) {
                ReasoningBlock(message.reasoning)
                Spacer(Modifier.height(6.dp))
            }

            when {
                message.status == MessageStatus.ERROR && message.content.isBlank() ->
                    ErrorInline(
                        kind = message.errorKind,
                        detail = message.errorDetail,
                        modelLabel = modelLabel,
                        onRetry = actions.onRegenerate
                    )
                isStreaming && message.content.isBlank() ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypingDots()
                        Text(
                            text = stringResource(R.string.chat_generating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                markdownEnabled && !isUser && message.content.isNotBlank() -> {
                    MarkdownText(
                        markdown = message.content,
                        codeHighlighting = codeHighlighting
                    )
                    if (isStreaming) StreamingCursor()
                }
                else -> {
                    Text(
                        text = message.content.ifEmpty { " " },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isStreaming) StreamingCursor()
                }
            }

            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AttachmentStrip(attachments, resolvedPath)
            }

            if (!isUser && message.usage != null && message.status == MessageStatus.SUCCESS) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = usageLine(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Timestamp and the action buttons share one compact row, placed on
        // the same side as the bubble (mirrored automatically in RTL).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = momentLabel(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(visible = actionsOpen || (isLastAssistant && !isUser)) {
                MessageActionsRow(
                    isUser = isUser,
                    message = message,
                    content = message.content,
                    actions = actions,
                    isError = message.status == MessageStatus.ERROR
                )
            }
        }
    }
}

@Composable
private fun MessageActionsRow(
    isUser: Boolean,
    message: Message,
    content: String,
    actions: ChatMessageActions,
    isError: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        IconButton(
            onClick = { actions.onCopy(content) },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.common_copy),
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { actions.onShare(message) },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.common_share),
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isUser) {
            IconButton(
                onClick = { actions.onEdit(message) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.chat_edit_message),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            IconButton(
                onClick = actions.onRegenerate,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(
                        if (isError) R.string.error_hint_retry else R.string.chat_regenerate
                    ),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { actions.onDelete(message) },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.chat_delete_message),
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String) {
    var expanded by rememberSaveable("reasoning-$reasoning") { mutableStateOf(false) }
    val label = if (expanded) {
        stringResource(R.string.chat_reasoning_hide)
    } else {
        stringResource(R.string.chat_reasoning_show)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (expanded) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 14,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun ErrorInline(
    kind: String?,
    detail: String?,
    modelLabel: String?,
    onRetry: () -> Unit
) {
    Column {
        Text(
            text = errorTextFor(kind, modelLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

@Composable
private fun AttachmentStrip(attachments: List<Attachment>, resolvedPath: (Attachment) -> File?) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEach { attachment ->
            when (attachment.kind) {
                AttachmentKind.IMAGE -> {
                    val file = resolvedPath(attachment) ?: return@forEach
                    AsyncImage(
                        model = file,
                        contentDescription = attachment.name,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                AttachmentKind.PDF -> FileChip(
                    icon = Icons.Filled.PictureAsPdf,
                    name = attachment.name
                )
                AttachmentKind.TEXT_FILE -> FileChip(
                    icon = Icons.Filled.Description,
                    name = attachment.name
                )
            }
        }
    }
}

@Composable
private fun FileChip(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun StreamingCursor() {
    Surface(
        shape = RoundedCornerShape(1.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 4.dp)
            .size(width = 9.dp, height = 17.dp)
    ) {}
}

private fun usageLine(message: Message): String {
    val usage = message.usage ?: return ""
    val parts = mutableListOf<String>()
    usage.promptTokens?.let { parts.add("↓$it") }
    usage.completionTokens?.let { parts.add("↑$it") }
    usage.totalTokens?.let { parts.add("~$it") }
    return parts.joinToString("  ·  ")
}

/** HH:mm for today/yesterday; otherwise date (locale aware). */
@Composable
fun momentLabel(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    return when (date) {
        LocalDate.now() -> time
        LocalDate.now().minusDays(1) -> stringResource(R.string.time_yesterday) + " " + time
        else -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone)) + " · " + time
    }
}
