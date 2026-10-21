package com.sokhan.chat.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.provider.ConnectionTestResult
import com.sokhan.chat.ui.components.SectionTitle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditorScreen(
    providerId: String?,
    onBack: () -> Unit,
    viewModel: ProviderEditorViewModel = koinViewModel(
        parameters = { parametersOf(providerId) }
    )
) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    val modelRows by viewModel.modelRows.collectAsStateWithLifecycle()
    val testing by viewModel.isTesting.collectAsStateWithLifecycle()
    val testResult by viewModel.test.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var addModelDialog by rememberSaveable { mutableStateOf(false) }

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
                        stringResource(
                            if (providerId == null) {
                                R.string.provider_editor_new
                            } else {
                                R.string.provider_editor_title
                            }
                        )
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save(onSaved = {
                                onBack()
                            })
                        },
                        enabled = state.name.isNotBlank() && state.baseUrl.isNotBlank()
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
            // ── Quick presets ──────────────────────────────────────────
            if (providerId == null) {
                SectionTitle(stringResource(R.string.provider_preset_title))
                Text(
                    text = stringResource(R.string.provider_preset_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PresetChips(
                    onPick = viewModel::applyPreset
                )
                HorizontalDivider()
            }

            // ── Basics ─────────────────────────────────────────────────
            OutlinedTextField(
                value = state.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value, nameError = false) } },
                label = { Text(stringResource(R.string.provider_name_hint)) },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.provider_error_name)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { value -> viewModel.update { it.copy(baseUrl = value, urlError = false) } },
                label = { Text(stringResource(R.string.provider_base_url_hint)) },
                supportingText = { Text(stringResource(R.string.provider_base_url_helper)) },
                isError = state.urlError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { value -> viewModel.update { it.copy(apiKey = value) } },
                label = {
                    Text(
                        if (state.hasStoredKey) {
                            stringResource(R.string.provider_api_key_keep)
                        } else {
                            stringResource(R.string.provider_api_key_hint)
                        }
                    )
                },
                supportingText = { Text(stringResource(R.string.provider_api_key_saved)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.organization,
                onValueChange = { value -> viewModel.update { it.copy(organization = value) } },
                label = { Text(stringResource(R.string.provider_org_hint)) },
                supportingText = { Text(stringResource(R.string.provider_org_helper)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.apiVersion,
                onValueChange = { value -> viewModel.update { it.copy(apiVersion = value) } },
                label = { Text(stringResource(R.string.provider_api_version_hint)) },
                supportingText = { Text(stringResource(R.string.provider_api_version_helper)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Test connection ────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = !testing
                ) {
                    Text(
                        text = if (testing) {
                            stringResource(R.string.provider_testing)
                        } else {
                            stringResource(R.string.provider_test)
                        }
                    )
                }
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .height(24.dp)
                            .width(24.dp)
                    )
                }
            }

            testResult?.let { result ->
                TestResultCard(result)
            }

            HorizontalDivider()

            // ── Models ─────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.models_title))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.refreshModels() },
                    enabled = !refreshing
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(
                        text = stringResource(
                            if (refreshing) R.string.provider_testing else R.string.provider_refresh_models
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { addModelDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.models_add_manual),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            if (modelRows.isEmpty()) {
                Text(
                    text = stringResource(R.string.provider_no_models_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                modelRows.forEach { model ->
                    ModelRow(
                        model = model,
                        isDefault = model.modelId == state.defaultModelId,
                        onSelectDefault = { viewModel.setDefaultModelRow(model.modelId) },
                        onCapsToggle = { caps ->
                            viewModel.updateModel(model.copy(capabilities = caps))
                        },
                        onDelete = { viewModel.deleteManualModel(model) }
                    )
                }
                Text(
                    text = stringResource(R.string.models_can_edit_caps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ── Parameters ─────────────────────────────────────────────
            SectionTitle(stringResource(R.string.provider_param_temperature))
            Text(
                text = stringResource(R.string.provider_params_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.temperatureText,
                    onValueChange = { value -> viewModel.update { it.copy(temperatureText = value) } },
                    label = { Text(stringResource(R.string.provider_param_temperature)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.maxTokensText,
                    onValueChange = { value -> viewModel.update { it.copy(maxTokensText = value) } },
                    label = { Text(stringResource(R.string.provider_param_max_tokens)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.topPText,
                    onValueChange = { value -> viewModel.update { it.copy(topPText = value) } },
                    label = { Text(stringResource(R.string.provider_param_top_p)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = state.includeUsage,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(includeUsage = checked) }
                    }
                )
                Text(stringResource(R.string.provider_include_usage))
            }

            HorizontalDivider()

            // ── Custom headers ─────────────────────────────────────────
            SectionTitle(stringResource(R.string.provider_headers_title))
            Text(
                text = stringResource(R.string.provider_headers_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.headers.forEachIndexed { index, pair ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = pair.first,
                        onValueChange = { newKey ->
                            viewModel.update { current ->
                                current.copy(
                                    headers = current.headers.mapIndexed { i, p ->
                                        if (i == index) newKey to p.second else p
                                    }
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.provider_header_key)) },
                        singleLine = true,
                        modifier = Modifier.weight(0.42f)
                    )
                    OutlinedTextField(
                        value = pair.second,
                        onValueChange = { newValue ->
                            viewModel.update { current ->
                                current.copy(
                                    headers = current.headers.mapIndexed { i, p ->
                                        if (i == index) p.first to newValue else p
                                    }
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.provider_header_value)) },
                        singleLine = true,
                        modifier = Modifier.weight(0.58f)
                    )
                    IconButton(
                        onClick = {
                            viewModel.update { current ->
                                current.copy(
                                    headers = current.headers.filterIndexed { i, _ -> i != index }
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_remove)
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    viewModel.update { it.copy(headers = it.headers + ("Header" to "")) }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.common_add),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            HorizontalDivider()

            // ── Capabilities (provider-wide fallbacks) ─────────────────
            SectionTitle(stringResource(R.string.provider_capacity_title))
            Text(
                text = stringResource(R.string.provider_capacity_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.caps.vision,
                    onClick = { viewModel.update { it.copy(caps = it.caps.copy(vision = !it.caps.vision)) } },
                    label = { Text(stringResource(R.string.provider_cap_vision)) }
                )
                FilterChip(
                    selected = state.caps.pdfFiles,
                    onClick = { viewModel.update { it.copy(caps = it.caps.copy(pdfFiles = !it.caps.pdfFiles)) } },
                    label = { Text(stringResource(R.string.provider_cap_pdf)) }
                )
                FilterChip(
                    selected = state.caps.textFiles,
                    onClick = { viewModel.update { it.copy(caps = it.caps.copy(textFiles = !it.caps.textFiles)) } },
                    label = { Text(stringResource(R.string.provider_cap_text_files)) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Save-state hint: brief banner after save.
    Box(Modifier.fillMaxSize()) {
        if (state.saved) {
            Text(
                text = stringResource(R.string.provider_saved),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    if (addModelDialog) {
        AddModelDialog(
            onDismiss = { addModelDialog = false },
            onAdd = { id, name, context ->
                viewModel.addManualModel(id, name, context)
                addModelDialog = false
            }
        )
    }
}

@Composable
private fun PresetChips(onPick: (name: String, url: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.sokhan.chat.provider.ProviderPresets.ALL.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    AssistChip(
                        onClick = { onPick(preset.name, preset.baseUrl) },
                        label = { Text(preset.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TestResultCard(result: ConnectionTestResult) {
    val ok = result.failure == null && result.steps.all { it.passed }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(
                if (ok) R.string.provider_test_ok else R.string.provider_test_fail
            ),
            style = MaterialTheme.typography.titleSmall,
            color = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        result.steps.forEach { step ->
            val label = when (step.stepKey) {
                com.sokhan.chat.provider.openai.OpenAiCompatibleClient.KEY_REACH ->
                    stringResource(R.string.provider_test_step_base)
                com.sokhan.chat.provider.openai.OpenAiCompatibleClient.KEY_AUTH ->
                    stringResource(R.string.provider_test_step_auth)
                else -> stringResource(
                    R.string.provider_test_step_models,
                    step.detail?.toIntOrNull() ?: 0
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.passed) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                step.detail?.takeIf { detail -> detail.toIntOrNull() == null && detail.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail.take(80),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ChatModel,
    isDefault: Boolean,
    onSelectDefault: () -> Unit,
    onCapsToggle: (com.sokhan.chat.domain.model.ModelCapabilities) -> Unit,
    onDelete: () -> Unit
) {
    val caps = model.capabilities
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDefault) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(16.dp)
                        )
                    }
                    Text(
                        text = model.displayName ?: model.modelId,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = if (isDefault) 4.dp else 0.dp)
                    )
                }
                Text(
                    text = model.modelId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isDefault) {
                OutlinedButton(onClick = onSelectDefault) {
                    Text(stringResource(R.string.provider_default_model))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.models_delete_manual)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = caps.vision,
                onClick = { onCapsToggle(caps.copy(vision = !caps.vision)) },
                label = { Text(stringResource(R.string.models_vision)) }
            )
            FilterChip(
                selected = caps.pdfFiles,
                onClick = { onCapsToggle(caps.copy(pdfFiles = !caps.pdfFiles)) },
                label = { Text(stringResource(R.string.models_pdf)) }
            )
            FilterChip(
                selected = caps.textFiles,
                onClick = { onCapsToggle(caps.copy(textFiles = !caps.textFiles)) },
                label = { Text(stringResource(R.string.provider_cap_text_files)) }
            )
        }
    }
}

@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int?) -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var contextText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.models_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.models_manual_id)) },
                    supportingText = { Text(stringResource(R.string.models_manual_id_helper)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.models_manual_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contextText,
                    onValueChange = { contextText = it },
                    label = { Text(stringResource(R.string.models_manual_context)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(id.trim(), name.trim(), contextText.toIntOrNull()) },
                enabled = id.isNotBlank()
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
