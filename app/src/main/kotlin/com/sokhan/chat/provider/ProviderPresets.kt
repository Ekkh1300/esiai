package com.sokhan.chat.provider

/**
 * One-tap provider presets in the editor ("quick setup"). These only prefill
 * the base URL — API keys are never shipped with Sokhan.
 */
data class ProviderPreset(val name: String, val baseUrl: String)

object ProviderPresets {
    val ALL = listOf(
        ProviderPreset("OpenAI", "https://api.openai.com/v1"),
        ProviderPreset("DeepSeek", "https://api.deepseek.com/v1"),
        ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
        ProviderPreset("Groq", "https://api.groq.com/openai/v1"),
        ProviderPreset("Mistral", "https://api.mistral.ai/v1"),
        ProviderPreset("Together", "https://api.together.xyz/v1"),
        ProviderPreset("Ollama (local)", "http://localhost:11434/v1"),
        ProviderPreset("LM Studio (local)", "http://localhost:1234/v1")
    )
}
