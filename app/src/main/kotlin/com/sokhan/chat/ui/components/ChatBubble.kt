package com.sokhan.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
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
import com.sokhan.chat.domain.agent.ToolCall
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    if (message.role == MessageRole.TOOL) {
        // The agent acted: one quiet activity row instead of a chat bubble.
        ToolActivityRow(message = message)
        return
    }

    val isUser = message.role == MessageRole.USER
    val toolCalls = remember(message.id, message.toolCalls) { parseToolCalls(message.toolCalls) }
    var actionsOpen by remember(message.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            // Model identity: a small tinted chip next to the model name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier.padding(5.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = modelLabel ?: message.modelId ?: "AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (toolCalls.isNotEmpty()) {
                ToolCallRows(calls = toolCalls)
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        val bubbleShape = RoundedCornerShape(
            topStart = if (isUser) 20.dp else 6.dp,
            topEnd = if (isUser) 6.dp else 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )

        if (isUser) {
            // Your own voice: one solid, confident colour.
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = bubbleShape,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(bubbleShape)
                    .clickable { actionsOpen = !actionsOpen }
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BubbleContent(
                        message = message,
                        modelLabel = modelLabel,
                        markdownEnabled = markdownEnabled,
                        codeHighlighting = codeHighlighting,
                        isStreaming = isStreaming,
                        attachments = attachments,
                        resolvedPath = resolvedPath,
                        actions = actions,
                        isUser = true
                    )
                }
            }
        } else {
            // A tool-only turn (the model acted instead of writing) shows
            // its activity rows above and no empty card below them.
            val hasBody = message.content.isNotBlank() ||
                !message.reasoning.isNullOrBlank() ||
                attachments.isNotEmpty() ||
                isStreaming ||
                message.status == MessageStatus.ERROR
            if (hasBody) {
                // The assistant answers as a card: a paper surface with a
                // hairline. The model chip above it carries the identity, so the
                // card itself stays quiet.
                Surface(
                    color = if (message.status == MessageStatus.ERROR) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (message.status == MessageStatus.ERROR) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = bubbleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .clip(bubbleShape)
                        .clickable { actionsOpen = !actionsOpen }
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        BubbleContent(
                            message = message,
                            modelLabel = modelLabel,
                            markdownEnabled = markdownEnabled,
                            codeHighlighting = codeHighlighting,
                            isStreaming = isStreaming,
                            attachments = attachments,
                            resolvedPath = resolvedPath,
                            actions = actions,
                            isUser = false
                        )
                    }
                }
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

/** The inside of a bubble â€” shared by the filled user bubble and the assistant card. */
@Composable
private fun BubbleContent(
    message: Message,
    modelLabel: String?,
    markdownEnabled: Boolean,
    codeHighlighting: Boolean,
    isStreaming: Boolean,
    attachments: List<Attachment>,
    resolvedPath: (Attachment) -> File?,
    actions: ChatMessageActions,
    isUser: Boolean
) {
    if (!isUser && !message.reasoning.isNullOrBlank()) {
        ReasoningBlock(message.reasoning)
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(10.dp))
        AttachmentStrip(attachments, resolvedPath)
    }

    if (!isUser && message.usage != null && message.status == MessageStatus.SUCCESS) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = usageLine(message),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Small visual buttons, but a full 48dp touch target around each.
        ActionChip(
            icon = Icons.Filled.ContentCopy,
            description = stringResource(R.string.common_copy),
            onClick = { actions.onCopy(content) }
        )
        ActionChip(
            icon = Icons.Filled.Share,
            description = stringResource(R.string.common_share),
            onClick = { actions.onShare(message) }
        )
        if (isUser) {
            ActionChip(
                icon = Icons.Filled.Edit,
                description = stringResource(R.string.chat_edit_message),
                onClick = { actions.onEdit(message) }
            )
        } else {
            ActionChip(
                icon = Icons.Filled.Refresh,
                description = stringResource(
                    if (isError) R.string.error_hint_retry else R.string.chat_regenerate
                ),
                onClick = actions.onRegenerate
            )
        }
        ActionChip(
            icon = Icons.Filled.Delete,
            description = stringResource(R.string.chat_delete_message),
            onClick = { actions.onDelete(message) },
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/** A quiet icon button with a 48dp touch target and an 18dp glyph. */
@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                // The thinking notes read as a quiet aside: indented, muted and
                // separated from the answer by a hairline.
                Row(Modifier.padding(top = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .heightIn(min = 18.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(1.dp)
                            )
                    )
                    Text(
                        text = reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 14,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
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
    usage.promptTokens?.let { parts.add("â†“$it") }
    usage.completionTokens?.let { parts.add("â†‘$it") }
    usage.totalTokens?.let { parts.add("~$it") }
    return parts.joinToString("  Â·  ")
}

// ── Agent mode ──────────────────────────────────────────────────────────

/** Parses the tool calls an assistant message recorded (agent mode). */
private fun parseToolCalls(stored: String?): List<ToolCall> =
    stored?.let { json ->
        runCatching { Json.decodeFromString<List<ToolCall>>(json) }.getOrNull()
    }.orEmpty()

/** Localised display name of a registered tool; raw id when unknown. */
@Composable
fun agentToolLabel(toolName: String?): String = when (toolName) {
    "search_conversations" -> stringResource(R.string.agent_tool_search_conversations)
    "read_conversation" -> stringResource(R.string.agent_tool_read_conversation)
    "list_assistants" -> stringResource(R.string.agent_tool_list_assistants)
    "list_providers" -> stringResource(R.string.agent_tool_list_providers)
    "create_conversation" -> stringResource(R.string.agent_tool_create_conversation)
    "rename_conversation" -> stringResource(R.string.agent_tool_rename_conversation)
    "delete_conversation" -> stringResource(R.string.agent_tool_delete_conversation)
    "send_message" -> stringResource(R.string.agent_tool_send_message)
    "set_language" -> stringResource(R.string.agent_tool_set_language)
    "set_theme" -> stringResource(R.string.agent_tool_set_theme)
    "set_dynamic_colors" -> stringResource(R.string.agent_tool_set_dynamic_colors)
    "export_backup" -> stringResource(R.string.agent_tool_export_backup)
    else -> toolName ?: "?"
}

/** What the model did before answering: one quiet row per tool call. */
@Composable
private fun ToolCallRows(calls: List<ToolCall>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        calls.forEach { call ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Text(
                    text = agentToolLabel(call.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** One executed tool: label, status glyph and a peek at its result. */
@Composable
private fun ToolActivityRow(message: Message) {
    val declined = message.errorKind == "DECLINED"
    val failed = message.errorKind == "FAILED"
    val rowShape = RoundedCornerShape(14.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = rowShape,
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(rowShape)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        declined -> MaterialTheme.colorScheme.surfaceContainerHighest
                        failed -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                        tint = when {
                            declined -> MaterialTheme.colorScheme.onSurfaceVariant
                            failed -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }
                Text(
                    text = agentToolLabel(message.toolName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = when {
                        declined -> Icons.Filled.Cancel
                        failed -> Icons.Filled.ErrorOutline
                        else -> Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        declined -> MaterialTheme.colorScheme.onSurfaceVariant
                        failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
            }
            if (declined) {
                Text(
                    text = stringResource(R.string.agent_row_declined),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
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
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone)) + " Â· " + time
    }
}

