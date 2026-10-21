package com.sokhan.chat.provider

import com.sokhan.chat.domain.model.ProviderKind
import com.sokhan.chat.provider.openai.OpenAiCompatibleClient
import okhttp3.OkHttpClient

/**
 * Concrete registry wiring provider kinds onto their client implementations.
 * Extending Sokhan with a new provider family = a new [AiProviderClient] added
 * here plus one `ProviderKind` value — the engine, UI and persistence layers
 * stay untouched.
 */
class DefaultProviderClientFactory(
    private val httpClient: OkHttpClient
) : ProviderClientFactory {

    override fun clientFor(kind: ProviderKind): AiProviderClient = when (kind) {
        ProviderKind.OPENAI_COMPATIBLE -> openAiClient
    }

    private val openAiClient: OpenAiCompatibleClient by lazy {
        OpenAiCompatibleClient(httpClient)
    }
}
