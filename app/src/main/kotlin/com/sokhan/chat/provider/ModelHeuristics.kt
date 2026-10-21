package com.sokhan.chat.provider

import com.sokhan.chat.domain.model.ModelCapabilities

/**
 * Heuristic capability inference from model ids. Providers rarely expose a
 * capabilities API; users can correct any flag manually in the model editor.
 * Always conservative: when unsure, the flag stays off (chat-only).
 */
object ModelHeuristics {

    private val VISION_HINTS = listOf("vision", "omni", "4o", "vl", "gemini", "claude", "llava", "pixtral", "qvq", "grok")
    private val REASONING_HINTS = listOf("o1", "o3", "o4", "r1", "reason", "thinking", "qwq")
    private val TOOLS_HINTS = listOf(
        "gpt-4", "gpt-3.5", "claude", "gemini", "qwen", "deepseek-chat", "deepseek-v",
        "mistral", "llama-3", "llama3.1", "firefunction", "tool", "agentic"
    )

    fun infer(modelId: String): ModelCapabilities {
        val lower = modelId.lowercase()
        val vision = VISION_HINTS.any { lower.contains(it) }
        val reasoning = REASONING_HINTS.any { lower.contains(it) }
        val tools = TOOLS_HINTS.any { lower.contains(it) }
        return ModelCapabilities(
            vision = vision,
            tools = tools,
            streaming = true,
            textFiles = vision || lower.contains("file"),
            pdfFiles = false,
            structured = tools,
            reasoning = reasoning
        )
    }
}
