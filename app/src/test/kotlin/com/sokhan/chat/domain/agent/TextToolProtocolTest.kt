package com.sokhan.chat.domain.agent

import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolProtocolTest {

    private fun tool(name: String) = AgentTool(
        name = name,
        description = "does a thing",
        parameters = buildJsonObject { put("type", kotlinx.serialization.json.JsonPrimitive("object")) }
    )

    @Test
    fun `parses a fenced tool_call block and strips it from the reply`() {
        val reply = """
            I'll switch that for you.

            ```tool_call
            {"name": "set_theme", "arguments": {"theme": "dark"}}
            ```
        """.trimIndent()

        val parsed = TextToolProtocol.parse(reply)

        assertNotNull(parsed)
        assertEquals("set_theme", parsed!!.call.name)
        assertEquals("dark", parsed.call.stringArg("theme"))
        assertTrue(!parsed.cleanedText.contains("tool_call"))
        assertTrue(parsed.cleanedText.startsWith("I'll switch that for you."))
    }

    @Test
    fun `parses a plain json fence without the tool_call tag`() {
        val reply = """
            ```json
            {"name": "set_language", "args": {"language": "fa"}}
            ```
        """.trimIndent()

        val parsed = TextToolProtocol.parse(reply)

        assertNotNull(parsed)
        assertEquals("set_language", parsed!!.call.name)
        assertEquals("fa", parsed.call.stringArg("language"))
    }

    @Test
    fun `parses a bare object written into the prose`() {
        val reply = """doing it now {"tool_call": {"name": "rename_conversation", "arguments": {"title": "Hi"}}} one sec"""

        val parsed = TextToolProtocol.parse(reply)

        assertNotNull(parsed)
        assertEquals("rename_conversation", parsed!!.call.name)
        assertEquals("Hi", parsed.call.stringArg("title"))
        assertTrue(!parsed.cleanedText.contains("rename_conversation"))
        assertTrue(parsed.cleanedText.contains("doing it now"))
    }

    @Test
    fun `rejects an object without a usable tool name`() {
        assertNull(TextToolProtocol.parse("""{"arguments": {"theme": "dark"}}"""))
        assertNull(TextToolProtocol.parse("""{"name": "not a real name!!", "arguments": {}}"""))
        assertNull(TextToolProtocol.parse("just a normal answer, no block"))
        assertNull(TextToolProtocol.parse(""))
    }

    @Test
    fun `survives braces and quotes inside string arguments`() {
        val reply = """
            ```tool_call
            {"name": "create_conversation", "arguments": {"title": "note } with \"quotes\" { and json"}}
            ```
        """.trimIndent()

        val parsed = TextToolProtocol.parse(reply)

        assertNotNull(parsed)
        assertEquals(
            "note } with \"quotes\" { and json",
            parsed!!.call.stringArg("title")
        )
    }

    @Test
    fun `unwraps json-string arguments copied from the OpenAI wire shape`() {
        // Many models answer with `"arguments": "{\"theme\":\"dark\"}"`.
        val reply = """
            ```tool_call
            {"name": "set_theme", "arguments": "{\"theme\": \"dark\"}"}
            ```
        """.trimIndent()

        val parsed = TextToolProtocol.parse(reply)

        assertNotNull(parsed)
        assertEquals("dark", parsed!!.call.stringArg("theme"))
    }

    @Test
    fun `rejects arguments that are neither an object nor a json string`() {
        assertNull(TextToolProtocol.parse("""{"name": "set_theme", "arguments": 42}"""))
        assertNull(TextToolProtocol.parse("""{"name": "set_theme", "arguments": "not json"}"""))
        assertNull(TextToolProtocol.parse("""{"name": "set_theme", "arguments": [1,2]}"""))
    }

    @Test
    fun `instruction lists every tool with a compact schema`() {
        val instruction = TextToolProtocol.instruction(listOf(tool("set_theme")))

        assertTrue(instruction.contains("set_theme"))
        assertTrue(instruction.contains("```tool_call"))
        assertTrue(instruction.contains("Available tools:"))
    }

    @Test
    fun `rendered call round-trips through the parser`() {
        val original = ToolCall(
            id = "native_1",
            name = "set_dynamic_colors",
            argumentsJson = "{\"enabled\":false}"
        )
        val parsed = TextToolProtocol.parse(TextToolProtocol.renderCall(original))

        assertNotNull(parsed)
        assertEquals(original.name, parsed!!.call.name)
        assertEquals("false", parsed.call.boolArg("enabled").toString())
    }

    @Test
    fun `native instruction prefers the api tools parameter but keeps the block fallback`() {
        val instruction = TextToolProtocol.instruction(listOf(tool("set_theme")), native = true)

        assertTrue(instruction.contains("`tools` parameter"))
        assertTrue(instruction.contains("```tool_call"))
        assertTrue(instruction.contains("set_theme"))
        assertTrue(instruction.contains("Available tools:"))
    }
}
