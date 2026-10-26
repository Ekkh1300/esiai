package com.sokhan.chat.domain.agent

import com.sokhan.chat.domain.model.Conversation
import com.sokhan.chat.domain.model.Message
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Reads a string argument, unescaped (models emit real JSON strings). */
private fun JsonObject.strArg(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * The tools the agent can use to act inside the app.
 *
 * Everything here is local: the agent reads and writes this app's own data.
 * It never reaches the network — the model only asks, this code does.
 */
class AppAgentTools(
    private val conversations: ConversationRepository,
    private val assistants: AssistantRepository,
    private val providers: ProviderRepository,
    private val settings: SettingsRepository,
    /** Runs a backup export and returns a human-readable location. */
    private val exportBackup: suspend (includeKeys: Boolean) -> String
) {

    fun handlers(): List<AgentToolHandler> = listOf(
        SearchConversationsTool(conversations),
        ReadConversationTool(conversations),
        ListAssistantsTool(assistants),
        ListProvidersTool(providers),
        CreateConversationTool(conversations),
        RenameConversationTool(conversations),
        DeleteConversationTool(conversations),
        SendMessageTool(conversations),
        SetLanguageTool(settings),
        SetThemeTool(settings),
        SetDynamicColorsTool(settings),
        ExportBackupTool(exportBackup)
    )

    // ── Read tools ────────────────────────────────────────────────────────

    private class SearchConversationsTool(
        private val conversations: ConversationRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.READ

        override val tool = AgentTool(
            name = "search_conversations",
            description = "Search the user's conversations by title or message text. " +
                "Use it when the user refers to something they talked about earlier.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Text to look for."))
                    }
                    putJsonObject("limit") {
                        put("type", JsonPrimitive("integer"))
                        put("description", JsonPrimitive("Maximum number of results (default 10)."))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val query = args.strArg("query").orEmpty()
            val limit = args.strArg("limit")?.toIntOrNull() ?: 10
            val all = conversations.listAllConversations()
            val hits = if (query.isBlank()) {
                all
            } else {
                all.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.id.contains(query, ignoreCase = true)
                }
            }.take(limit.coerceIn(1, 50))
            if (hits.isEmpty()) {
                return "No conversation matched \"$query\"."
            }
            return hits.joinToString("\n") { conversation ->
                "- id=${conversation.id} | title=\"${conversation.title}\" | updated=${conversation.updatedAt}"
            }
        }
    }

    private class ReadConversationTool(
        private val conversations: ConversationRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.READ

        override val tool = AgentTool(
            name = "read_conversation",
            description = "Read the full transcript of one conversation, newest message last.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("conversationId") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The conversation id."))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val id = args.strArg("conversationId").orEmpty()
            val conversation = conversations.get(id)
                ?: return "No conversation with id \"$id\"."
            val messages = runCatching { conversations.observeMessages(id).first() }
                .getOrDefault(emptyList())
            val body = messages.joinToString("\n") { message ->
                "[${message.role.name.lowercase()}] ${message.content}"
            }
            return "Conversation \"${conversation.title}\" (id=$id):\n$body"
        }
    }

    private class ListAssistantsTool(
        private val assistants: AssistantRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.READ

        override val tool = AgentTool(
            name = "list_assistants",
            description = "List the assistant personas available to the user.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val all = runCatching { assistants.observeAll().first() }.getOrDefault(emptyList())
            if (all.isEmpty()) return "There are no assistants yet."
            return all.joinToString("\n") { assistant ->
                "- id=${assistant.id} | name=\"${assistant.name}\" | model=${assistant.modelLabel ?: "-"}"
            }
        }
    }

    private class ListProvidersTool(
        private val providers: ProviderRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.READ

        override val tool = AgentTool(
            name = "list_providers",
            description = "List the configured AI providers (names and models, never the API keys).",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val all = runCatching { providers.observeAll().first() }.getOrDefault(emptyList())
            if (all.isEmpty()) return "No providers are configured yet."
            return all.joinToString("\n") { provider ->
                "- id=${provider.id} | name=\"${provider.name}\" | model=${provider.defaultModelId ?: "-"}"
            }
        }
    }

    // ── Write tools ───────────────────────────────────────────────────────

    private class CreateConversationTool(
        private val conversations: ConversationRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.WRITE

        override val tool = AgentTool(
            name = "create_conversation",
            description = "Create a new empty conversation with a title.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Short title for the new conversation."))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val title = args.strArg("title")
                ?: "New conversation"
            val conversation = Conversation(
                id = com.sokhan.chat.core.Ids.newId(),
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            conversations.upsert(conversation)
            return "Created conversation \"$title\" with id ${conversation.id}."
        }
    }

    private class RenameConversationTool(
        private val conversations: ConversationRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.WRITE

        override val tool = AgentTool(
            name = "rename_conversation",
            description = "Rename an existing conversation.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("conversationId") {
                        put("type", JsonPrimitive("string"))
                    }
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val id = args.strArg("conversationId").orEmpty()
            val title = args.strArg("title").orEmpty()
            if (title.isBlank()) return "A title is required."
            if (conversations.get(id) == null) return "No conversation with id \"$id\"."
            conversations.rename(id, title)
            return "Renamed conversation $id to \"$title\"."
        }
    }

    private class DeleteConversationTool(
        private val conversations: ConversationRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.DESTRUCTIVE

        override val tool = AgentTool(
            name = "delete_conversation",
            description = "Delete a conversation and all of its messages. The user confirms this.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("conversationId") {
                        put("type", JsonPrimitive("string"))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val id = args.strArg("conversationId").orEmpty()
            val conversation = conversations.get(id)
                ?: return "No conversation with id \"$id\"."
            conversations.delete(id)
            return "Deleted conversation \"${conversation.title}\" (id=$id)."
        }
    }

    private class SendMessageTool(
        private val conversations: ConversationRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.WRITE

        override val tool = AgentTool(
            name = "send_message",
            description = "Post a message into an existing conversation on the user's behalf.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("conversationId") {
                        put("type", JsonPrimitive("string"))
                    }
                    putJsonObject("text") {
                        put("type", JsonPrimitive("string"))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val id = args.strArg("conversationId").orEmpty()
            val text = args.strArg("text").orEmpty()
            if (text.isBlank()) return "The message text is required."
            if (conversations.get(id) == null) return "No conversation with id \"$id\"."
            val now = System.currentTimeMillis()
            conversations.insertMessage(
                Message(
                    id = com.sokhan.chat.core.Ids.newId(),
                    conversationId = id,
                    role = com.sokhan.chat.domain.model.MessageRole.USER,
                    content = text,
                    createdAt = now
                )
            )
            return "Message sent to conversation $id."
        }
    }

    private class SetLanguageTool(
        private val settings: SettingsRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.WRITE

        override val tool = AgentTool(
            name = "set_language",
            description = "Switch the interface language of the app.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("language") {
                        put("type", JsonPrimitive("string"))
                        put(
                            "description",
                            JsonPrimitive("One of: system, en, fa.")
                        )
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val language = args.strArg("language").orEmpty()
            if (language !in setOf("system", "en", "fa")) {
                return "Unsupported language \"$language\". Use system, en or fa."
            }
            settings.setLanguage(language)
            return "The interface language is now \"$language\"."
        }
    }

    private class SetThemeTool(
        private val settings: SettingsRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.WRITE

        override val tool = AgentTool(
            name = "set_theme",
            description = "Switch the app theme.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("theme") {
                        put("type", JsonPrimitive("string"))
                        put(
                            "description",
                            JsonPrimitive("One of: system, light, dark.")
                        )
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val theme = args.strArg("theme").orEmpty()
            if (theme !in setOf("system", "light", "dark")) {
                return "Unsupported theme \"$theme\". Use system, light or dark."
            }
            settings.setThemeMode(theme)
            return "The theme is now \"$theme\"."
        }
    }

    private class SetDynamicColorsTool(
        private val settings: SettingsRepository
    ) : AgentToolHandler {

        override val risk = ToolRisk.WRITE

        override val tool = AgentTool(
            name = "set_dynamic_colors",
            description = "Turn Material You dynamic colours on or off.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("enabled") {
                        put("type", JsonPrimitive("boolean"))
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val enabled = args.strArg("enabled")?.toBooleanStrictOrNull()
                ?: return "The value of \"enabled\" must be true or false."
            settings.update { it.copy(dynamicColors = enabled) }
            return "Dynamic colours are now ${if (enabled) "on" else "off"}."
        }
    }

    private class ExportBackupTool(
        private val exportBackup: suspend (Boolean) -> String
    ) : AgentToolHandler {

        override val risk = ToolRisk.DESTRUCTIVE

        override val tool = AgentTool(
            name = "export_backup",
            description = "Export a JSON backup of conversations, assistants and providers. " +
                "The user confirms this, and API keys are only included when asked for.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("includeApiKeys") {
                        put("type", JsonPrimitive("boolean"))
                        put(
                            "description",
                            JsonPrimitive("Include the user's API keys in plaintext. Defaults to false.")
                        )
                    }
                }
                putJsonArray("required") { }
            }
        )

        override suspend fun run(args: JsonObject): String {
            val includeKeys = args.strArg("includeApiKeys")?.toBooleanStrictOrNull()
                ?: false
            val location = exportBackup(includeKeys)
            return "Backup exported to $location."
        }
    }
}
