package com.sokhan.chat.ui.screens.assistants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.R
import com.sokhan.chat.ui.components.SectionTitle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Reasonable emoji palette for assistant icons. */
private val EMOJI_PALETTE = listOf(
    "💬", "🤖", "🧠", "✍️", "📚", "🧑‍💻", "🌍", "🎯", "🧾", "🔍", "🪄", "⚙️",
    "❤️", "📝", "🎓", "🧭", "🚀", "🧪", "🧩", "🛠️"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantEditorScreen(
    assistantId: String?,
    onBack: () -> Unit,
    onStartChat: (String) -> Unit,
    viewModel: AssistantEditorViewModel = koinViewModel(
        parameters = { parametersOf(assistantId) }
    )
) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    val providers by viewModel.providersList.collectAsStateWithLifecycle()
    val modelRows by viewModel.modelRows.collectAsStateWithLifecycle()

    var deleteDialog by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.assistant_editor_title)) },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.name.isNotBlank() && state.systemPrompt.isNotBlank()
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { value ->
                    viewModel.update { it.copy(name = value, nameError = false) }
                },
                label = { Text(stringResource(R.string.assistant_name_hint)) },
                isError = state.nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle(stringResource(R.string.assistant_pick_emoji))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EMOJI_PALETTE.filter { it.isNotBlank() }.forEach { emoji ->
                    FilterChip(
                        selected = state.emoji == emoji,
                        onClick = { viewModel.update { it.copy(emoji = emoji) } },
                        label = { Text(emoji) }
                    )
                }
            }

            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = { value -> viewModel.update { it.copy(systemPrompt = value) } },
                label = { Text(stringResource(R.string.assistant_prompt_hint)) },
                supportingText = { Text(stringResource(R.string.assistant_prompt_helper)) },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle(stringResource(R.string.assistant_provider))
            ProviderRow(
                label = stringResource(R.string.assistant_any_provider),
                selected = state.providerId == null,
                onClick = { viewModel.onProviderSelected(null) }
            )
            providers.forEach { provider ->
                ProviderRow(
                    label = provider.name,
                    selected = state.providerId == provider.id,
                    onClick = { viewModel.onProviderSelected(provider.id) }
                )
            }

            if (state.providerId != null) {
                SectionTitle(stringResource(R.string.assistant_model))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    modelRows.forEach { model ->
                        ProviderRow(
                            label = model.displayName ?: model.modelId,
                            selected = state.modelId == model.modelId,
                            onClick = {
                                viewModel.update { it.copy(modelId = model.modelId) }
                            }
                        )
                    }
                    if (modelRows.isEmpty()) {
                        Text(
                            text = stringResource(R.string.provider_no_models_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.assistant_temperature))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.Slider(
                    value = state.temperature.toFloat(),
                    onValueChange = { value ->
                        viewModel.update { it.copy(temperature = value.toDouble()) }
                    },
                    valueRange = 0f..2f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "%.1f".format(state.temperature),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = state.streaming,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(streaming = checked) }
                    }
                )
                Text(
                    text = stringResource(R.string.provider_streaming),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            // Save / start chat actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        viewModel.save()
                    },
                    enabled = state.name.isNotBlank() && state.systemPrompt.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_save))
                }
                Button(
                    onClick = {
                        viewModel.saveAndStartChat(onStartChat)
                    },
                    enabled = state.name.isNotBlank() && state.systemPrompt.isNotBlank()
                ) {
                    Text(stringResource(R.string.assistant_start_chat))
                }
            }

            TextButton(
                onClick = { deleteDialog = true },
                enabled = state.assistantId != null
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(
                    text = stringResource(R.string.common_delete),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            if (state.saved) {
                Text(
                    text = stringResource(R.string.assistant_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.assistant_delete_confirm, state.name)) },
            confirmButton = {
                TextButton(onClick = {
                    state.assistantId?.let { id -> viewModel.delete(id) }
                    deleteDialog = false
                    onBack()
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProviderRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp)
    )
}
