package com.sokhan.chat.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.Attachment
import com.sokhan.chat.domain.model.AttachmentKind
import com.sokhan.chat.domain.model.Message
import java.io.File
import androidx.compose.foundation.layout.height

/**
 * Input bar with draft attachment strip, attach menu (gallery / camera /
 * file), send-or-stop affordance and an inline "editing" state for
 * corrected user messages.
 */
@Composable
fun ChatComposer(
    draftAttachments: List<Attachment>,
    resolvedFile: (Attachment) -> File?,
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    attachmentsEnabled: Boolean,
    streaming: Boolean,
    enterSends: Boolean,
    editingMessage: Message?,
    onPickUri: (Uri, String?) -> Unit,
    onRemoveDraft: (Attachment) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onEditSave: (String) -> Unit,
    onEditCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var attachMenuOpen by rememberSaveable { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        picked?.let { onPickUri(it, null) }
    }
    val cameraStageFile = remember {
        File(context.cacheDir, "camera/sokhan-staged.jpg").apply { parentFile?.mkdirs() }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            onPickUri(cameraStageFileCameraUri(context, cameraStageFile), "snapshot.jpg")
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        picked?.let { onPickUri(it, null) }
    }

    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            // Attachment drafts
            if (draftAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    draftAttachments.forEach { attachment ->
                        ComposerAttachmentChip(
                            attachment = attachment,
                            file = resolvedFile(attachment),
                            onRemove = { onRemoveDraft(attachment) }
                        )
                    }
                }
            }

            if (editingMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.chat_edit_message_with_hint),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                        )
                        IconButton(onClick = onEditCancel) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (attachmentsEnabled) {
                    Box {
                        FilledIconButton(
                            onClick = { attachMenuOpen = true },
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Filled.PostAdd,
                                contentDescription = stringResource(R.string.a11y_attach_menu)
                            )
                        }
                        AttachMenu(
                            expanded = attachMenuOpen,
                            onDismiss = { attachMenuOpen = false },
                            onGallery = {
                                attachMenuOpen = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCamera = {
                                attachMenuOpen = false
                                cameraLauncher.launch(cameraStageFileCameraUri(context, cameraStageFile))
                            },
                            onFile = {
                                attachMenuOpen = false
                                fileLauncher.launch(
                                    arrayOf("text/*", "application/pdf", "text/plain", "application/json")
                                )
                            }
                        )
                    }
                }

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 180.dp)
                        .onPreviewKeyEvent { event ->
                            if (enterSends && enabled &&
                                event.key == androidx.compose.ui.input.key.Key.Enter &&
                                event.type == androidx.compose.ui.input.key.KeyEventType.KeyUp
                            ) {
                                onSend(text)
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 6,
                    enabled = enabled,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                FilledIconButton(
                    onClick = {
                        when {
                            streaming -> onStop()
                            editingMessage != null -> onEditSave(text)
                            else -> onSend(text)
                        }
                    },
                    enabled = enabled && (streaming || (text.isNotBlank() || draftAttachments.isNotEmpty())),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (streaming) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                ) {
                    if (streaming) {
                        Icon(
                            Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.chat_stop)
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send_describe)
                        )
                    }
                }
            }
        }
    }
}

private fun cameraStageFileCameraUri(context: android.content.Context, file: File): Uri =
    androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )

@Composable
private fun ComposerAttachmentChip(
    attachment: Attachment,
    file: File?,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (attachment.kind) {
                AttachmentKind.IMAGE -> AsyncImage(
                    model = file,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                AttachmentKind.PDF -> Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 10.dp)
                )
                AttachmentKind.TEXT_FILE -> Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.a11y_delete_attachment),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.attach_image)) },
            onClick = onGallery,
            leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) }
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.attach_camera)) },
            onClick = onCamera,
            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) }
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.attach_file)) },
            onClick = onFile,
            leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) }
        )
    }
}
