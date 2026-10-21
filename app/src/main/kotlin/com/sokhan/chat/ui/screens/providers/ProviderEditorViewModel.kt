package com.sokhan.chat.ui.screens.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.ChatModel
import com.sokhan.chat.domain.model.ProviderCapabilities
import com.sokhan.chat.domain.model.ProviderConfig
import com.sokhan.chat.domain.model.ProviderKind
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.provider.ConnectionTestResult
import com.sokhan.chat.provider.ModelHeuristics
import com.sokhan.chat.provider.ProviderClientFactory
import com.sokhan.chat.provider.ProviderContext
import com.sokhan.chat.provider.ProviderModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Editable state of one provider. The api key field mirrors what the user
 * typed; an existing stored key is NEVER echoed back (only masked).
 */
data class ProviderEditorState(
    val providerId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    val organization: String = "",
    val apiVersion: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val defaultModelId: String = "",
    val includeUsage: Boolean = false,
    val temperatureText: String = "",
    val maxTokensText: String = "",
    val topPText: String = "",
    val caps: ProviderCapabilities = ProviderCapabilities(),
    val nameError: Boolean = false,
    val urlError: Boolean = false,
    val saved: Boolean = false
)

class ProviderEditorViewModel(
    private val providerId: String?,
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val settings: SettingsRepository,
    private val clientFactory: ProviderClientFactory
) : ViewModel() {

    private val state = MutableStateFlow(ProviderEditorState())
    val editor: StateFlow<ProviderEditorState> = state.asStateFlow()

    private val modelList = MutableStateFlow<List<ChatModel>>(emptyList())
    val modelRows: StateFlow<List<ChatModel>> = modelList.asStateFlow()

    private val testing = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = testing.asStateFlow()

    private val testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val test: StateFlow<ConnectionTestResult?> = testResult.asStateFlow()

    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            if (providerId == null) {
                state.value = ProviderEditorState(
                    baseUrl = "",
                    caps = ProviderCapabilities()
                )
            } else {
                load(providerId)
            }
        }
    }

    private suspend fun load(id: String) {
        val config = providers.get(id) ?: return
        modelList.value = models.anyForProvider(id)
        state.value = ProviderEditorState(
            providerId = config.id,
            name = config.name,
            baseUrl = config.baseUrl,
            hasStoredKey = runCatching {
                providers.resolve(id)?.apiKey != null
            }.getOrDefault(false),
            organization = config.organization.orEmpty(),
            apiVersion = config.apiVersion.orEmpty(),
            headers = config.headers.map { (k, v) -> k to v },
            defaultModelId = config.defaultModelId.orEmpty(),
            includeUsage = config.includeUsageInStream,
            temperatureText = config.defaultTemperature?.toString().orEmpty(),
            maxTokensText = config.defaultMaxTokens?.toString().orEmpty(),
            topPText = config.defaultTopP?.toString().orEmpty(),
            caps = config.capabilities
        )
    }

    // ── field updates ──────────────────────────────────────────────────

    fun update(transform: (ProviderEditorState) -> ProviderEditorState) {
        state.value = transform(state.value)
    }

    fun applyPreset(name: String, url: String) {
        state.value = state.value.copy(
            name = state.value.name.ifBlank { name },
            baseUrl = url
        )
    }

    // ── save ───────────────────────────────────────────────────────────

    fun save(clearKey: Boolean = false, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val current = state.value
            val nameOk = current.name.isNotBlank()
            val urlOk = current.baseUrl.startsWith("http://") || current.baseUrl.startsWith("https://")
            if (!nameOk || !urlOk) {
                state.value = current.copy(nameError = !nameOk, urlError = !urlOk, saved = false)
                return@launch
            }
            val id = current.providerId ?: Ids.newId()
            val config = ProviderConfig(
                id = id,
                name = current.name.trim(),
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = current.baseUrl.trim().trimEnd('/'),
                organization = current.organization.trim().takeIf { it.isNotBlank() },
                apiVersion = current.apiVersion.trim().takeIf { it.isNotBlank() },
                headers = current.headers
                    .filter { (k, _) -> k.isNotBlank() }
                    .associate { it.first.trim() to it.second.trim() },
                defaultModelId = current.defaultModelId.trim().takeIf { it.isNotBlank() },
                capabilities = current.caps,
                defaultTemperature = current.temperatureText.toDoubleOrNull(),
                defaultMaxTokens = current.maxTokensText.toIntOrNull(),
                defaultTopP = current.topPText.toDoubleOrNull(),
                includeUsageInStream = current.includeUsage,
                createdAt = current.providerId?.let { providers.get(it)?.createdAt }
                    ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            providers.upsert(
                config = config,
                newApiKey = current.apiKey.takeIf { it.isNotBlank() },
                clearApiKey = clearKey && current.apiKey.isBlank()
            )
            if (current.providerId == null) {
                // First provider becomes the default so first chat "just works".
                val defaultId = settings.settings.value.defaultProviderId
                    ?: providers.defaultProviderId()
                if (defaultId == null || defaultId == config.id) {
                    settings.update {
                        it.copy(
                            defaultProviderId = config.id,
                            defaultModelId = config.defaultModelId ?: it.defaultModelId
                        )
                    }
                }
            }
            state.value = current.copy(
                providerId = id,
                hasStoredKey = current.apiKey.isNotBlank() || runCatching {
                    providers.resolve(id)?.apiKey != null
                }.getOrDefault(current.hasStoredKey),
                nameError = false,
                urlError = false,
                saved = true
            )
            onSaved(id)
        }
    }

    // ── test / refresh ─────────────────────────────────────────────────

    fun testConnection() {
        val current = state.value
        val key = current.apiKey.takeIf { it.isNotBlank() }
        if (key == null && current.providerId == null) return
        viewModelScope.launch {
            testing.value = true
            testResult.value = null
            val config = draftConfig(current)
            val storedKey = current.providerId?.let { providers.resolve(it)?.apiKey }
            val resolvedKey = key ?: storedKey
            val client = clientFactory.clientFor(config.kind)
            testResult.value = client.testConnection(ProviderContext(config, resolvedKey))
            testing.value = false
        }
    }

    fun refreshModels() {
        val current = state.value
        val target = current.providerId ?: return saveThenRefresh()
        viewModelScope.launch {
            refreshing.value = true
            val storedKey = current.apiKey.takeIf { it.isNotBlank() }
                ?: providers.resolve(target)?.apiKey
            val config = draftConfig(current)
            val client = clientFactory.clientFor(config.kind)
            client.listModels(ProviderContext(config, storedKey))
                .fold(
                    onSuccess = { list ->
                        storeFetchedModels(target, list)
                        modelList.value = models.anyForProvider(target)
                        refreshing.value = false
                    },
                    onFailure = {
                        refreshing.value = false
                    }
                )
        }
    }

    private suspend fun storeFetchedModels(targetId: String, list: List<ProviderModelInfo>) {
        val now = System.currentTimeMillis()
        models.upsertAll(
            list.map { info ->
                val caps = ModelHeuristics.infer(info.id)
                ChatModel(
                    id = "$targetId::${info.id}",
                    providerId = targetId,
                    modelId = info.id,
                    displayName = null,
                    capabilities = caps,
                    createdAt = now
                )
            }
        )
    }

    private fun saveThenRefresh() {
        save(onSaved = { refreshModels() })
    }

    /** Adds a manually defined model. */
    fun addManualModel(modelId: String, displayName: String, contextLength: Int?) {
        val id = state.value.providerId ?: return
        viewModelScope.launch {
            models.upsert(
                ChatModel(
                    id = "$id::${modelId.trim()}",
                    providerId = id,
                    modelId = modelId.trim(),
                    displayName = displayName.takeIf { it.isNotBlank() },
                    contextLength = contextLength,
                    userDefined = true
                )
            )
            modelList.value = models.anyForProvider(id)
        }
    }

    fun updateModel(model: ChatModel) {
        viewModelScope.launch {
            models.updateCapabilities(model)
            state.value.providerId?.let { modelList.value = models.anyForProvider(it) }
        }
    }

    fun deleteManualModel(model: ChatModel) {
        viewModelScope.launch {
            models.delete(model)
            state.value.providerId?.let { modelList.value = models.anyForProvider(it) }
        }
    }

    fun setDefaultModelRow(modelId: String) {
        update { it.copy(defaultModelId = modelId) }
    }

    private fun draftConfig(current: ProviderEditorState): ProviderConfig =
        ProviderConfig(
            id = current.providerId ?: Ids.newId(),
            name = current.name.ifBlank { "draft" },
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = current.baseUrl.trim().trimEnd('/'),
            organization = current.organization.trim().takeIf { it.isNotBlank() },
            apiVersion = current.apiVersion.trim().takeIf { it.isNotBlank() },
            headers = current.headers.filter { (k, _) -> k.isNotBlank() }
                .associate { it.first.trim() to it.second.trim() },
            capabilities = current.caps
        )
}
