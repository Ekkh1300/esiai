package com.sokhan.chat.ui.screens.assistants

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.Assistant
import com.sokhan.chat.ui.components.EmptyState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantsScreen(
    onStartChat: (String) -> Unit,
    onEditAssistant: (String) -> Unit,
    onNewAssistant: () -> Unit,
    viewModel: AssistantsViewModel = koinViewModel()
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val deleting = remember { mutableStateOf<Assistant?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistants_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewAssistant) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    stringResource(R.string.assistants_add_fab),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        if (list.isEmpty()) {
            Box(Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Filled.SmartToy,
                    title = stringResource(R.string.assistants_empty_title),
                    subtitle = stringResource(R.string.assistants_empty_subtitle),
                    action = {
                        OutlinedButton(onClick = onNewAssistant) {
                            Text(stringResource(R.string.assistants_add_fab))
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(list, key = { it.id }) { assistant ->
                    AssistantRow(
                        assistant = assistant,
                        onClick = { onEditAssistant(assistant.id) },
                        onStart = { viewModel.startChat(assistant, onStartChat) },
                        onDelete = { deleting.value = assistant }
                    )
                }
            }
        }
    }

    deleting.value?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting.value = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.assistant_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deleting.value = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting.value = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantRow(
    assistant: Assistant,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onDelete
        ),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = assistant.emoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        },
        headlineContent = {
            Text(assistant.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = assistant.systemPrompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            OutlinedButton(onClick = onStart) {
                Text(stringResource(R.string.assistant_start_chat))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
