package com.sokhan.chat.domain.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolTest {

    private fun call(args: String) = ToolCall(id = "c1", name = "demo", argumentsJson = args)

    // ── ToolCall argument parsing ────────────────────────────────────────

    @Test
    fun `arguments parses a plain json object`() {
        val args = call("""{"conversationId":"abc","limit":5}""").arguments()
        assertEquals("abc", args["conversationId"]?.jsonPrimitive?.content)
        assertEquals("5", args["limit"]?.jsonPrimitive?.content)
    }

    @Test
    fun `arguments falls back to empty when the model sent garbage`() {
        assertTrue(call("{not json").arguments().isEmpty())
        assertTrue(call("[1,2,3]").arguments().isEmpty())
        assertTrue(call("").arguments().isEmpty())
    }

    @Test
    fun `stringArg unescapes and skips blanks`() {
        assertEquals("a\"b", call("""{"t":"a\"b"}""").stringArg("t"))
        assertNull(call("""{"t":"  "}""").stringArg("t"))
        assertNull(call("{}").stringArg("t"))
    }

    @Test
    fun `boolArg accepts real booleans and boolean-looking strings`() {
        assertEquals(true, call("""{"f":true}""").boolArg("f"))
        assertEquals(false, call("""{"f":false}""").boolArg("f"))
        // Models sometimes quote booleans; tolerate that, reject garbage.
        assertEquals(true, call("""{"f":"true"}""").boolArg("f"))
        assertNull(call("""{"f":"yes"}""").boolArg("f"))
    }

    // ── Registry ─────────────────────────────────────────────────────────

    private fun handler(name: String, risk: ToolRisk) = object : AgentToolHandler {
        override val tool = AgentTool(name, "desc", buildJsonObject { put("type", "object") })
        override val risk: ToolRisk = risk
        override suspend fun run(args: JsonObject): String = "ok"
    }

    @Test
    fun `registry finds tools by name and exposes descriptors`() {
        val registry = AgentToolRegistry(
            listOf(handler("read_x", ToolRisk.READ), handler("wipe_x", ToolRisk.DESTRUCTIVE))
        )
        assertNotNull(registry.find("read_x"))
        assertNull(registry.find("nope"))
        assertEquals(listOf("read_x", "wipe_x"), registry.tools.map { it.name })
    }

    @Test
    fun `only read-only tools run without confirmation`() {
        val registry = AgentToolRegistry(
            listOf(handler("read_x", ToolRisk.READ), handler("edit_x", ToolRisk.WRITE))
        )
        val read = registry.find("read_x")!!
        val edit = registry.find("edit_x")!!
        assertFalse(registry.needsConfirmation(read))
        assertTrue(registry.needsConfirmation(edit))
    }

    @Test
    fun `tool calls round-trip through kotlinx serialization`() {
        val original = ToolCall(id = "c7", name = "set_theme", argumentsJson = """{"theme":"dark"}""")
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()),
            listOf(original)
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()),
            json
        )
        assertEquals(listOf(original), decoded)
    }
}
