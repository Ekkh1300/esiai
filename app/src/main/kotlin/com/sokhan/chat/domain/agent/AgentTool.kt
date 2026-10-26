package com.sokhan.chat.domain.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A capability the model can invoke while chatting.
 *
 * The descriptor is sent to the provider verbatim in the OpenAI `tools`
 * array, so it has to stay provider-neutral: a name, a description and a
 * JSON-Schema object for the arguments.
 */
data class AgentTool(
    val name: String,
    val description: String,
    /** JSON Schema of the arguments object. */
    val parameters: JsonObject
)

/** A tool invocation requested by the model. */
@kotlinx.serialization.Serializable
data class ToolCall(
    val id: String,
    val name: String,
    /** Raw JSON arguments as the model produced them (often partial). */
    val argumentsJson: String
) {
    /** Parsed arguments, or an empty object when the model sent garbage. */
    fun arguments(): JsonObject = runCatching {
        if (argumentsJson.isBlank()) {
            JsonObject(emptyMap())
        } else {
            kotlinx.serialization.json.Json.parseToJsonElement(argumentsJson).jsonObject
        }
    }.getOrElse { JsonObject(emptyMap()) }

    /** Reads a string argument. */
    fun stringArg(key: String): String? =
        arguments()[key]?.let { element ->
            runCatching { element.jsonPrimitive.content }.getOrNull()
        }?.takeIf { it.isNotBlank() }

    /** Reads a boolean argument. */
    fun boolArg(key: String): Boolean? =
        arguments()[key]?.let { element ->
            runCatching { element.jsonPrimitive.content.toBooleanStrictOrNull() }.getOrNull()
        }
}

/** The outcome of running one tool. */
data class ToolResult(
    val callId: String,
    val toolName: String,
    val output: String,
    val isError: Boolean = false
)

/**
 * What a tool may do.
 *
 * Read-only tools run without asking. Anything that changes or removes data
 * needs the user's confirmation first — the agent must never delete a
 * conversation or wipe the app on its own.
 */
enum class ToolRisk {
    /** Only reads local state. */
    READ,

    /** Changes something the user can undo or expects to be reversible. */
    WRITE,

    /** Destroys data or exposes secrets. Always confirmed. */
    DESTRUCTIVE
}

/**
 * A registered tool: the descriptor the model sees plus the code that
 * actually performs the work.
 */
interface AgentToolHandler {
    val tool: AgentTool
    val risk: ToolRisk

    /**
     * Runs the tool.
     *
     * @param args the arguments parsed from the model's tool call
     * @return the textual result that is sent back to the model
     */
    suspend fun run(args: JsonObject): String
}

/** Registry of the tools the agent may use. */
class AgentToolRegistry(handlers: List<AgentToolHandler>) {

    private val byName: Map<String, AgentToolHandler> = handlers.associateBy { it.tool.name }

    val tools: List<AgentTool> = handlers.map { it.tool }

    fun find(name: String): AgentToolHandler? = byName[name]

    fun needsConfirmation(handler: AgentToolHandler): Boolean =
        handler.risk != ToolRisk.READ
}
