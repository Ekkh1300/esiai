package com.sokhan.chat.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.R
import com.sokhan.chat.data.export.ImportSummary
import com.sokhan.chat.domain.model.ThemeMode
import com.sokhan.chat.ui.components.SectionTitle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenProviders: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var includeKeys by rememberSaveable { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var promptEditorOpen by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && pendingJson != null) {
            writeToUri(context, uri, pendingJson!!)
            scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_export_done)) }
        }
        pendingJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val payload = uri?.let { readString(context, it) }
        if (payload != null) {
            scope.launch {
                val summary: ImportSummary? = viewModel.importJson(payload)
                val message = if (summary != null) {
                    context.getString(
                        R.string.settings_import_done,
                        summary.conversationsAdded,
                        summary.conversationsMerged,
                        summary.assistantsAdded,
                        summary.assistantsMerged,
                        summary.providersAdded,
                        summary.providersMerged
                    )
                } else {
                    context.getString(R.string.settings_import_failed)
                }
                snackbar.showSnackbar(message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── General ────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_general))

            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
            )
            LanguageSegment(
                selected = snapshot.language,
                onSelected = { viewModel.setLanguage(it) }
            )
            Text(
                text = stringResource(R.string.settings_language_change_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            ThemeSegment(
                selected = snapshot.themeMode,
                onSelected = { viewModel.setTheme(it) }
            )

            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_hint),
                checked = snapshot.dynamicColors,
                onChange = { viewModel.setDynamicColors(it) }
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // ── Chat defaults ──────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_defaults))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProviders() }
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.settings_check_models))
                    Text(
                        text = snapshot.defaultProviderId ?: stringResource(R.string.common_unset),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.settings_default_streaming),
                subtitle = null,
                checked = snapshot.defaultStreaming,
                onChange = { checked -> viewModel.update { it.copy(defaultStreaming = checked) } }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { promptEditorOpen = true }
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_default_system_prompt))
                    Text(
                        text = snapshot.defaultSystemPrompt?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_default_system_prompt_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                IconButton(onClick = { promptEditorOpen = true }) {
                    Icon(
                        Icons.Filled.EditNote,
                        contentDescription = stringResource(R.string.common_edit)
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // ── Chat display ───────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_chat_display))
            SwitchRow(
                title = stringResource(R.string.settings_markdown),
                subtitle = stringResource(R.string.settings_markdown_hint),
                checked = snapshot.markdownEnabled,
                onChange = { checked -> viewModel.update { it.copy(markdownEnabled = checked) } }
            )
            SwitchRow(
                title = stringResource(R.string.settings_code_highlighting),
                subtitle = stringResource(R.string.settings_code_highlighting_hint),
                checked = snapshot.codeHighlighting,
                onChange = { checked -> viewModel.update { it.copy(codeHighlighting = checked) } }
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_scroll),
                subtitle = null,
                checked = snapshot.autoScroll,
                onChange = { checked -> viewModel.update { it.copy(autoScroll = checked) } }
            )
            SwitchRow(
                title = stringResource(R.string.settings_enter_sends),
                subtitle = stringResource(R.string.settings_enter_sends_hint),
                checked = snapshot.enterSends,
                onChange = { checked -> viewModel.update { it.copy(enterSends = checked) } }
            )
            SwitchRow(
                title = stringResource(R.string.settings_agent_mode),
                subtitle = stringResource(R.string.settings_agent_mode_hint),
                checked = snapshot.agentMode,
                onChange = { checked -> viewModel.update { it.copy(agentMode = checked) } }
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // ── Data & privacy ─────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_data))
            Text(
                text = stringResource(R.string.settings_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeKeys, onCheckedChange = { includeKeys = it })
                Text(
                    text = stringResource(R.string.settings_export_include_keys),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            ActionRow(
                icon = Icons.Filled.Backup,
                title = stringResource(R.string.settings_export_data)
            ) {
                scope.launch {
                    pendingJson = viewModel.exportJson(includeKeys)
                    exportLauncher.launch("esi-agent-backup.json")
                }
            }
            ActionRow(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_import_data)
            ) {
                importLauncher.launch(arrayOf("application/json", "text/*"))
            }
            Text(
                text = stringResource(R.string.settings_export_import_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionRow(
                icon = Icons.Filled.DeleteSweep,
                title = stringResource(R.string.settings_clear_data)
            ) {
                clearDialog = true
            }
            Text(
                text = stringResource(R.string.settings_clear_data_warn),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            // ── About ──────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_about))
            ActionRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about_body),
                clickable = true
            ) {
                onOpenAbout()
            }
            ActionRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_licenses),
                clickable = true
            ) {
                onOpenLicenses()
            }
            Text(
                text = stringResource(
                    R.string.settings_version,
                    com.sokhan.chat.core.BuildFlags.VERSION_NAME
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData { }
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

    if (promptEditorOpen) {
        var promptValue by remember(promptEditorOpen) {
            mutableStateOf(snapshot.defaultSystemPrompt.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { promptEditorOpen = false },
            title = { Text(stringResource(R.string.settings_default_system_prompt)) },
            text = {
                OutlinedTextField(
                    value = promptValue,
                    onValueChange = { promptValue = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { current ->
                        current.copy(
                            defaultSystemPrompt = promptValue.trim()
                                .takeIf { value -> value.isNotBlank() }
                        )
                    }
                    promptEditorOpen = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { promptEditorOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun LanguageSegment(selected: String, onSelected: (String) -> Unit) {
    val options = listOf(
        "system" to stringResource(R.string.settings_language_system),
        "en" to "English",
        "fa" to "فارسی"
    )
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, (key, label) ->
            SegmentedButton(
                selected = selected == key,
                onClick = { onSelected(key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ThemeSegment(selected: String, onSelected: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode.name,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
        // Switch trailing
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    clickable: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        // Icon in a tinted tile: gives the long settings list a scannable
        // rhythm instead of a column of naked glyphs.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

private fun writeToUri(context: Context, uri: Uri, payload: String) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(payload.toByteArray(Charsets.UTF_8))
        }
    }
}

private fun readString(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes().toString(Charsets.UTF_8)
    }
}.getOrNull()


