package com.sokhan.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.ChatErrorKind

enum class ConfirmationStyle { NEUTRAL, DESTRUCTIVE }

/** Small section heading used across editor screens. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 6.dp)
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String = stringResource(R.string.common_confirm),
    dismissLabel: String = stringResource(R.string.common_cancel),
    style: ConfirmationStyle = ConfirmationStyle.DESTRUCTIVE,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (style == ConfirmationStyle.DESTRUCTIVE) {
                    androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.textButtonColors()
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(18.dp)
                    .size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

/** Three-dot "the model is typing" indicator. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { dot ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
            ) {}
        }
    }
}

/**
 * Pure (non-composable) resource mapping for [ChatErrorKind], so snackbar
 * hosts and launchers can resolve error text outside of composition.
 */
fun errorResFor(kind: ChatErrorKind): Int = when (kind) {
    ChatErrorKind.AUTH -> R.string.error_auth
    ChatErrorKind.RATE_LIMIT -> R.string.error_rate_limit
    ChatErrorKind.MODEL_NOT_FOUND -> R.string.error_model_not_found
    ChatErrorKind.NOT_FOUND -> R.string.error_not_found
    ChatErrorKind.BAD_REQUEST -> R.string.error_malformed
    ChatErrorKind.SERVER -> R.string.error_server_generic
    ChatErrorKind.TIMEOUT -> R.string.error_timeout
    ChatErrorKind.UNREACHABLE -> R.string.error_unreachable
    ChatErrorKind.OFFLINE -> R.string.error_offline
    ChatErrorKind.MALFORMED -> R.string.error_malformed
    ChatErrorKind.EMPTY -> R.string.error_empty_response
    ChatErrorKind.CAPABILITY -> R.string.error_capability_vision
    ChatErrorKind.CANCELLED -> R.string.error_cancelled
    ChatErrorKind.NO_PROVIDER -> R.string.chat_need_provider
    ChatErrorKind.NO_MODEL -> R.string.chat_need_model
    ChatErrorKind.INVALID_CONFIG -> R.string.error_invalid_url
    ChatErrorKind.UNKNOWN -> R.string.error_unknown
}

/**
 * Maps persisted error kinds onto localised, user-friendly messages. Provider
 * details are shown separately in a subdued tone by the caller.
 */
@Composable
fun errorTextFor(kind: String?, modelLabel: String? = null): String =
    stringResource(
        errorResFor(ChatErrorKind.safeValueOf(kind)),
        modelLabel.orEmpty()
    )
