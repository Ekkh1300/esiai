package com.sokhan.chat.ui.screens.chats

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.R
import com.sokhan.chat.domain.repository.ConversationCardSnapshot
import com.sokhan.chat.domain.repository.StreamState
import com.sokhan.chat.ui.components.EmptyState
import com.sokhan.chat.ui.components.TypingDots
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: (String) -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: ChatsViewModel = koinViewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val search by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val hasProvider by viewModel.hasAnyProvider.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()

    var searchFieldVisible by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ConversationCardSnapshot?>(null) }
    var deleting by remember { mutableStateOf<ConversationCardSnapshot?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (showArchived) R.string.chats_archived else R.string.chats_title
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { searchFieldVisible = !searchFieldVisible }) {
                        Icon(
                            if (searchFieldVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.chats_search_hint)
                        )
                    }
                    IconButton(onClick = { viewModel.setShowArchived(!showArchived) }) {
                        Icon(
                            if (showArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = stringResource(R.string.a11y_toggle_archived)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (hasProvider) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.newConversation(onCreated = onNewChat)
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.action_new_chat), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchFieldVisible) {
                // Extra paddings on the left/right; a compact search field.
                OutlinedTextField(
                    value = search,
                    onValueChange = { viewModel.setSearch(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text(stringResource(R.string.chats_search_hint)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.setSearch("")
                                searchFieldVisible = false
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                )
            }

            when {
                !hasProvider -> EmptyState(
                    icon = Icons.Filled.Forum,
                    title = stringResource(R.string.chats_empty_no_provider),
                    subtitle = stringResource(R.string.chats_needs_provider_hint),
                    action = {
                        TextButton(onClick = onOpenProviders) {
                            Text(stringResource(R.string.chats_setup_provider))
                        }
                    }
                )
                cards.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Forum,
                    title = stringResource(R.string.chats_empty_title),
                    subtitle = stringResource(
                        if (showArchived) R.string.chats_empty_archived
                        else R.string.chats_empty_subtitle
                    )
                )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(cards, key = { it.conversation.id }) { card ->
                            ConversationRow(
                                card = card,
                                streaming = streamState.isStreaming(card.conversation.id),
                                onClick = { onOpenChat(card.conversation.id) },
                                onRename = { renaming = card },
                                onDelete = { deleting = card },
                                onPin = {
                                    viewModel.setPinned(
                                        card.conversation.id,
                                        !card.conversation.pinned
                                    )
                                },
                                onArchive = {
                                    viewModel.setArchived(
                                        card.conversation.id,
                                        !card.conversation.archived
                                    )
                                }
                            )
                        }
                    }
            }
        }
    }

    renaming?.let { card ->
        RenameDialog(
            initial = card.conversation.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                viewModel.rename(card.conversation.id, title)
                renaming = null
            }
        )
    }

    deleting?.let { card ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.chats_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(card.conversation.id)
                    deleting = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    card: ConversationCardSnapshot,
    streaming: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val conversation = card.conversation
    val title = conversation.title.ifBlank {
        stringResource(R.string.chat_untitled)
    }

    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { menuOpen = true }
        ),
        leadingContent = {
            if (streaming) {
                TypingDots(modifier = Modifier.size(width = 26.dp, height = 12.dp))
            } else {
                Icon(
                    Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conversation.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = {
            val preview = card.previewText.orEmpty().ifBlank {
                stringResource(R.string.chats_preview_empty)
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        },
        trailingContent = {
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(stringResource(R.string.common_more))
                }
                ChatRowMenu(
                    expanded = menuOpen,
                    pinned = conversation.pinned,
                    archived = conversation.archived,
                    onDismiss = { menuOpen = false },
                    onRename = onRename,
                    onDelete = onDelete,
                    onPin = onPin,
                    onArchive = onArchive
                )
            }
        }
    )
}

@Composable
private fun ChatRowMenu(
    expanded: Boolean,
    pinned: Boolean,
    archived: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chats_rename)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (pinned) R.string.chats_unpin else R.string.chats_pin
                    )
                )
            },
            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
            onClick = { onDismiss(); onPin() }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (archived) R.string.chats_unarchive else R.string.chats_archive
                    )
                )
            },
            leadingIcon = {
                Icon(
                    if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    contentDescription = null
                )
            },
            onClick = { onDismiss(); onArchive() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chats_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
