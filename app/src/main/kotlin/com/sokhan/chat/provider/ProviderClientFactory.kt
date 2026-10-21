package com.sokhan.chat.provider

import com.sokhan.chat.domain.model.ProviderKind

/**
 * Registry that resolves a [AiProviderClient] per provider kind. Koin wires a
 * single implementation; tests can substitute fakes.
 */
interface ProviderClientFactory {
    fun clientFor(kind: ProviderKind): AiProviderClient
}
