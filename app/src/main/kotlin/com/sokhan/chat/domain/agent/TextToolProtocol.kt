package com.sokhan.chat.domain.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Tool calling for models that cannot use the provider's native `tools`
 * array.
 *
 * Many endpoints accept a `tools` parameter and then silently ignore it
 * (small open-weight models, several OpenRouter free routes, older local
 * servers). Instead of giving up on agent mode, the engine switches to this
 * text protocol: the system prompt asks the model to answer with a small JSON
 * block, and the reply text is parsed back into a [ToolCall].
 *
 * The protocol is deliberately tolerant — the block may sit in a fenced
 * ```tool_call / ```json block or appear bare in the prose, and the arguments
 * may be spelled `arguments`, `args` or `parameters`.
 */
object TextToolProtocol {

    /** A call recovered from reply text. */
    data class Parsed(val call: ToolCall, /** Reply text with the block removed. */
                      val cleanedText: String)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Names may look like `set_theme`, `openrouter.tools/x` … nothing else. */
    private val NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.\\-]{0,63}$")

    private val FENCE_PATTERN = Regex(
        "```(?:tool_call|toolcall|json|javascript|js)?\\s*\\n?(.*?)```",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /**
     * System-prompt suffix that teaches the protocol and lists the tools.
     * Kept in English on purpose: model instruction-following is far more
     * reliable in English than in the interface language.
     *
     * @param native true when the request also carries the API's `tools`
     * array — the block is then only the fallback for endpoints that drop it
     * or models that ignore it, so the wording prefers the native mechanism.
     */
    fun instruction(tools: List<AgentTool>, native: Boolean = false): String = buildString {
        appendLine("You can act inside this app by calling its tools.")
        if (native) {
            appendLine("Prefer the tool-calling mechanism of this API (the `tools` parameter).")
            appendLine("If it is unavailable to you or you are asked to, write the call as a")
            appendLine("fenced code block containing one JSON object instead:")
        } else {
            appendLine("This model is reached through plain text, so a tool call is written")
            appendLine("as a fenced code block containing one JSON object:")
        }
        appendLine()
        appendLine("```tool_call")
        appendLine("{\"name\": \"set_theme\", \"arguments\": {\"theme\": \"dark\"}}")
        appendLine("```")
        appendLine()
        appendLine("Rules:")
        appendLine("- Emit at most ONE tool call per reply, on its own, then stop.")
        appendLine("- \"arguments\" must be a JSON object matching the tool's schema below.")
        appendLine(
            if (native) {
                "- After the call the tool result comes back and you answer the user normally."
            } else {
                "- After the call you will receive a message starting with \"Tool result\";" +
                    "\n  answer the user normally from there."
            }
        )
        appendLine("- When no tool is needed, reply normally with no code block.")
        appendLine()
        appendLine("Available tools:")
        tools.forEach { tool ->
            append("- ${tool.name}: ${tool.description} arguments=${compactSchema(tool)}")
        }
    }

    /** Canonical rendering of a call, used when replaying history. */
    fun renderCall(call: ToolCall): String =
        "```tool_call\n{\"name\": ${quote(call.name)}, \"arguments\": ${
            call.arguments().toString()
        }}\n```"

    /** The message that carries a tool result back to the model. */
    fun renderResult(toolName: String?, output: String): String =
        "Tool result for \"${toolName ?: "tool"}\":\n$output"

    /**
     * Finds the first tool call in [text].
     *
     * @return the call plus the reply text with the block stripped out, or
     * null when the reply contains no recognisable call.
     */
    fun parse(text: String): Parsed? {
        if (text.isBlank()) return null
        // Fenced blocks first — that is what the instruction asks for.
        FENCE_PATTERN.findAll(text).forEach { match ->
            val inner = match.groupValues[1]
            extract(inner)?.let { call ->
                return Parsed(call, strip(text, match.range))
            }
        }
        // Then any balanced JSON object sitting in the prose.
        balancedObjects(text).forEach { raw ->
            extract(raw)?.let { call ->
                return Parsed(call, text.replace(raw, "").cleaned())
            }
        }
        return null
    }

    // ── Parsing internals ────────────────────────────────────────────────

    /** Pulls a tool call out of one candidate JSON object, or null. */
    private fun extract(raw: String): ToolCall? {
        val element = runCatching { json.parseToJsonElement(raw.trim()) }.getOrNull()
            ?: return null
        val root = element as? JsonObject ?: return null
        val callObject = when {
            root["tool_call"] is JsonObject -> root["tool_call"]!!.jsonObject
            root["toolCall"] is JsonObject -> root["toolCall"]!!.jsonObject
            root["function"] is JsonObject -> root["function"]!!.jsonObject
            root["name"] != null -> root
            else -> return null
        }
        val name = runCatching {
            callObject["name"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull()?.trim()
        if (name.isNullOrBlank() || !NAME_PATTERN.matches(name)) return null
        val rawArguments = callObject["arguments"]
            ?: callObject["args"]
            ?: callObject["parameters"]
        // Models frequently copy the OpenAI wire shape, where `arguments` is a
        // JSON *string* rather than an object — unwrap it before validating.
        val arguments: JsonObject? = when (rawArguments) {
            null -> JsonObject(emptyMap())
            is JsonObject -> rawArguments
            is JsonPrimitive -> rawArguments.content
                .takeIf { rawArguments.isString }
                ?.let { text ->
                    runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                }
            else -> null
        } ?: return null // arguments present but not an object — reject as garbage
        // Never trust the model with an id: the engine pairs results by id.
        return ToolCall(
            id = "txt_${name}_${raw.hashCode().toUInt()}",
            name = name,
            argumentsJson = arguments.toString()
        )
    }

    /** Balanced top-level `{…}` regions, innermost-safe (brace counting). */
    private fun balancedObjects(text: String): List<String> {
        val found = ArrayList<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        for (index in text.indices) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        found += text.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return found
    }

    /** Removes the matched region plus the blank line it usually sits on. */
    private fun strip(text: String, range: IntRange): String {
        var from = range.first
        var to = range.last + 1
        while (from > 0 && text[from - 1].isWhitespace()) from--
        while (to < text.length && text[to].isWhitespace()) to++
        // Keep one whitespace so two words never fuse together.
        return (text.substring(0, from) + " " + text.substring(to)).cleaned()
    }

    private fun String.cleaned(): String = lines()
        .filterNot { it.isBlank() }
        .joinToString("\n")
        .trim()

    /** One-line JSON Schema summary, short enough for a system prompt. */
    private fun compactSchema(tool: AgentTool): String {
        val properties = tool.parameters["properties"] as? JsonObject ?: return "{}"
        val required = (tool.parameters["required"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
        if (properties.isEmpty()) return "{}"
        val parts = properties.entries.map { (key, value) ->
            val type = (value as? JsonObject)?.get("type")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: "string"
            val mark = if (key in required) "" else "?"
            "$key$mark:$type"
        }
        return "{" + parts.joinToString(", ") + "}"
    }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""
}
