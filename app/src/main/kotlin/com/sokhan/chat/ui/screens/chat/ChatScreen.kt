package com.sokhan.chat.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.ChatErrorKind
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.model.MessageRole
import com.sokhan.chat.domain.model.MessageStatus
import com.sokhan.chat.ui.ShareUtils
import com.sokhan.chat.ui.components.ChatMessageActions
import com.sokhan.chat.ui.components.ChatMessageBubble
import com.sokhan.chat.ui.components.ChatComposer
import com.sokhan.chat.ui.components.EmptyState
import com.sokhan.chat.ui.components.ModelPickerSheet
import com.sokhan.chat.ui.components.errorResFor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenProviderEditor: (String?) -> Unit,
    viewModel: ChatViewModel = koinViewModel(
        parameters = { parametersOf(conversationId) }
    )
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val attachmentsByMessage by viewModel.attachmentsByMessage.collectAsStateWithLifecycle()
    val filesByAttachment by viewModel.filesByAttachment.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val drafts by viewModel.draftAttachments.collectAsStateWithLifecycle()
    val editing by viewModel.editingMessage.collectAsStateWithLifecycle()
    val startupError by viewModel.startupError.collectAsStateWithLifecycle()
    val attachmentProblem by viewModel.attachmentProblem.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val providers by viewModel.providersList.collectAsStateWithLifecycle()
    val sheetProviderId by viewModel.sheetProviderId.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listScope = rememberCoroutineScope()

    var modelSheetVisible by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Message?>(null) }
    var clearDialog by remember { mutableStateOf(false) }
    var composerText by rememberSaveable(conversationId) { mutableStateOf("") }

    val streaming = streamState.isStreaming(conversationId)
    val lastAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }

    // The list is rendered bottom-up (reverseLayout = true), so item 0 has to
    // be the NEWEST message. The domain list arrives oldest-first, therefore
    // it is reversed here — otherwise the conversation reads upside down.
    val orderedMessages = remember(messages) { messages.asReversed() }

    val showJumpDown by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // Startup failures → snackbar with a localised, friendly message.
    LaunchedEffect(startupError) {
        val error = startupError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            // Extra format args are ignored by strings without placeholders.
            context.getString(errorResFor(ChatErrorKind.of(error)), conversation?.modelLabel)
        )
        viewModel.acknowledgeStartupError()
    }

    // Attachment failures (too large / unreadable) → snackbar.
    LaunchedEffect(attachmentProblem) {
        when (attachmentProblem) {
            null -> return@LaunchedEffect
            "FILE_TOO_LARGE" -> snackbarHostState.showSnackbar(
                context.getString(R.string.attach_size_too_large_hint)
            )
            else -> snackbarHostState.showSnackbar(
                context.getString(R.string.attach_image_failed_read)
            )
        }
        viewModel.acknowledgeAttachmentProblem()
    }

    // Auto-scroll: in a reversed list the newest message is index 0.
    val newestMessage = messages.lastOrNull()
    LaunchedEffect(messages.size, newestMessage?.id, newestMessage?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Load the text of the message being edited.
    LaunchedEffect(editing?.id) {
        editing?.let { message -> composerText = message.content }
    }

    // Opening the picker pins it to the conversation's current provider.
    LaunchedEffect(modelSheetVisible) {
        if (modelSheetVisible) {
            conversation?.providerId?.let { viewModel.pickProvider(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = conversation?.title
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.chats_draft_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(onClick = { modelSheetVisible = true }) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = stringResource(R.string.chat_pick_model)
                        )
                    }
                    IconButton(onClick = { clearDialog = true }) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.chat_clear)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ChatComposer(
                draftAttachments = drafts,
                resolvedFile = { attachment -> filesByAttachment[attachment.id] },
                text = composerText,
                onTextChange = { composerText = it },
                enabled = true,
                attachmentsEnabled = true,
                streaming = streaming,
                enterSends = settings.enterSends,
                editingMessage = editing,
                onPickUri = { uri, name -> viewModel.addAttachment(uri, name) },
                onRemoveDraft = { viewModel.removeDraft(it) },
                onSend = { text ->
                    composerText = ""
                    viewModel.send(text)
                },
                onStop = { viewModel.stop() },
                onEditSave = { text ->
                    composerText = ""
                    viewModel.send(text)
                },
                onEditCancel = { viewModel.cancelEdit() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                conversation == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                messages.isEmpty() && availableModels.isEmpty() && providers.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Radar,
                        title = stringResource(R.string.chat_need_provider),
                        subtitle = stringResource(R.string.chats_needs_provider_hint),
                        action = {
                            TextButton(onClick = { onOpenProviderEditor(null) }) {
                                Text(stringResource(R.string.chats_setup_provider))
                            }
                        }
                    )
                }
                messages.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.SmartToy,
                        title = stringResource(R.string.chat_need_model),
                        subtitle = stringResource(R.string.chats_needs_provider_hint),
                        action = {
                            TextButton(onClick = { modelSheetVisible = true }) {
                                Text(stringResource(R.string.chat_pick_model))
                            }
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        reverseLayout = true
                    ) {
                        items(orderedMessages, key = { it.id }) { message ->
                            val isStreaming = streaming &&
                                message.id == lastAssistant?.id &&
                                message.status == MessageStatus.STREAMING
                            ChatMessageBubble(
                                message = message,
                                modelLabel = conversation?.modelLabel,
                                attachments = attachmentsByMessage[message.id].orEmpty(),
                                resolvedPath = { attachment -> filesByAttachment[attachment.id] },
                                markdownEnabled = settings.markdownEnabled,
                                codeHighlighting = settings.codeHighlighting,
                                isLastAssistant = message.id == lastAssistant?.id,
                                isStreaming = isStreaming,
                                actions = ChatMessageActions(
                                    onCopy = { content ->
                                        clipboard.setText(AnnotatedString(content))
                                    },
                                    onShare = { toShare ->
                                        ShareUtils.shareText(
                                            context,
                                            conversation?.title,
                                            toShare.content
                                        )
                                    },
                                    onEdit = { viewModel.startEdit(it) },
                                    onDelete = { deleteTarget = it },
                                    onRegenerate = { viewModel.regenerate() }
                                )
                            )
                        }
                    }
                    if (showJumpDown) {
                        // Anchored to the trailing edge (right in LTR, left in
                        // RTL) so it never floats over the middle of the
                        // conversation.
                        FilledTonalIconButton(
                            onClick = { listScope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 14.dp)
                                .shadow(6.dp, CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.ArrowDownward,
                                contentDescription = stringResource(R.string.chat_jump_down)
                            )
                        }
                    }
                }
            }
        }
    }

    ModelPickerSheet(
        visible = modelSheetVisible,
        providers = providers,
        models = availableModels,
        selectedProviderId = sheetProviderId ?: conversation?.providerId,
        selectedModelId = conversation?.modelId,
        onProviderChange = { providerId ->
            viewModel.pickProvider(providerId)
        },
        onSelect = { model ->
            viewModel.commitSheetModel(model)
            modelSheetVisible = false
        },
        onDismiss = { modelSheetVisible = false }
    )

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_message)) },
            text = { Text(stringResource(R.string.chat_delete_message_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(target)
                    deleteTarget = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.chat_clear)) },
            text = { Text(stringResource(R.string.chat_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearConversation()
                    clearDialog = false
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
