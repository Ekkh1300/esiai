package com.sokhan.chat.ui.screens.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.ui.components.EmptyState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    viewModel: ProvidersViewModel = koinViewModel()
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val menuFor = remember { mutableStateOf<ProviderConfig?>(null) }
    val deleting = remember { mutableStateOf<ProviderConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.providers_add_fab),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        if (all.isEmpty()) {
            Column(Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Filled.Cloud,
                    title = stringResource(R.string.providers_empty_title),
                    subtitle = stringResource(R.string.providers_empty_subtitle),
                    action = {
                        OutlinedButton(onClick = onNew) {
                            Text(stringResource(R.string.providers_add_fab))
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
                items(all, key = { it.id }) { provider ->
                    ListItem(
                        modifier = Modifier.clickable { onEdit(provider.id) },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = provider.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menuFor.value = provider }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.common_more)
                                    )
                                }
                                ProviderMenu(
                                    provider = provider,
                                    isDefault = all.firstOrNull()?.id == provider.id,
                                    expanded = menuFor.value?.id == provider.id,
                                    onDismiss = { menuFor.value = null },
                                    onDuplicate = {
                                        viewModel.duplicate(provider.id, onDone = {})
                                        menuFor.value = null
                                    },
                                    onDelete = {
                                        deleting.value = provider
                                        menuFor.value = null
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    deleting.value?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting.value = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.provider_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
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

@Composable
private fun ProviderMenu(
    provider: ProviderConfig,
    isDefault: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    viewModel: ProvidersViewModel = koinViewModel()
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!isDefault) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.provider_set_active)) },
                onClick = {
                    viewModel.setDefault(provider.id)
                    onDismiss()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.provider_active)) },
                onClick = onDismiss
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.provider_duplicate)) },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = onDuplicate
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = onDelete
        )
    }
}
