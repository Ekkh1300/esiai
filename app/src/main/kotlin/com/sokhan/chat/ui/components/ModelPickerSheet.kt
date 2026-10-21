package com.sokhan.chat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.ProviderConfig

/**
 * Bottom-sheet model picker: pick a provider, then one of its models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    visible: Boolean,
    providers: List<ProviderConfig>,
    models: List<ChatModel>,
    selectedProviderId: String?,
    selectedModelId: String?,
    onProviderChange: (String) -> Unit,
    onSelect: (ChatModel) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_pick_model),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Provider selection row
            LazyColumn(modifier = Modifier.weight(0.35f, fill = false)) {
                items(providers, key = { it.id }) { provider ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProviderChange(provider.id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = if (provider.id == selectedProviderId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (provider.id == selectedProviderId) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                        )
                        if (provider.id == selectedProviderId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.padding(6.dp))

            // Model rows for the selected provider
            if (models.isEmpty()) {
                Text(
                    text = stringResource(R.string.models_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(models, key = { it.id }) { model ->
                        val selected = model.modelId == selectedModelId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model) }
                                .padding(horizontal = 28.dp, vertical = 10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName ?: model.modelId,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    maxLines = 1
                                )
                                CapabilityBadges(model)
                            }
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun CapabilityBadges(model: ChatModel) {
    val badges = remember(model.capabilities) {
        buildList {
            if (model.capabilities.vision) add("👁")
            if (model.capabilities.pdfFiles) add("PDF")
            if (model.capabilities.textFiles) add("TXT")
            if (model.capabilities.reasoning) add("🧠")
        }
    }
    if (badges.isNotEmpty()) {
        Text(
            text = badges.joinToString("  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
