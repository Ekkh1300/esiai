package com.sokhan.chat.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val codec = BackupCodec()

    private fun bundle(
        schemaVersion: Int = BACKUP_SCHEMA_VERSION,
        providerKeys: Map<String, String> = emptyMap()
    ) = BackupBundle(
        schemaVersion = schemaVersion,
        exportedAt = 1_700_000_000_000L,
        conversations = listOf(
            BackupConversation(
                id = "c1",
                title = "Hello",
                createdAt = 10L,
                updatedAt = 20L,
                systemPrompt = null,
                messages = listOf(
                    BackupMessage(
                        id = "m1",
                        role = "USER",
                        content = "hi there",
                        createdAt = 11L
                    )
                )
            )
        ),
        assistants = listOf(
            BackupAssistant(id = "a1", name = "Coder", emoji = "🧑‍💻", systemPrompt = "code")
        ),
        providers = listOf(
            BackupProvider(
                id = "p1",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                defaultModelId = "gpt-4o-mini"
            )
        ),
        providerKeys = providerKeys
    )

    @Test
    fun `round trip preserves the bundle`() {
        val encoded = codec.encode(bundle())
        val decoded = codec.decode(encoded)
        assertEquals(bundle(), decoded)
    }

    @Test
    fun `provider keys survive a round trip when explicitly included`() {
        val encoded = codec.encode(bundle(providerKeys = mapOf("p1" to "sk-secret")))
        assertEquals("sk-secret", codec.decode(encoded)?.providerKeys?.get("p1"))
    }

    @Test
    fun `null default fields survive the round trip`() {
        val encoded = codec.encode(bundle())
        assertNull(codec.decode(encoded)?.conversations?.single()?.systemPrompt)
    }

    @Test
    fun `decode tolerates a BOM and surrounding whitespace`() {
        val encoded = "\uFEFF\n  " + codec.encode(bundle()) + "  \n"
        assertEquals("Hello", codec.decode(encoded)?.conversations?.single()?.title)
    }

    @Test
    fun `decode rejects garbage`() {
        assertNull(codec.decode("not json at all"))
        assertNull(codec.decode(""))
        assertNull(codec.decode("   "))
        assertNull(codec.decode("{\"nope\":true}"))
    }

    @Test
    fun `decode rejects backups from newer schema versions`() {
        assertNull(codec.decode(codec.encode(bundle(schemaVersion = BACKUP_SCHEMA_VERSION + 1))))
    }

    @Test
    fun `unknown fields are ignored on decode`() {
        val encoded = codec.encode(bundle())
        // Inject an unknown property into the top-level object only.
        val firstBrace = encoded.indexOf('{')
        val withExtra = buildString {
            append(encoded, 0, firstBrace + 1)
            append("\"unexpectedFutureField\": true,")
            append(encoded, firstBrace + 1, encoded.length)
        }
        assertEquals("Hello", codec.decode(withExtra)?.conversations?.single()?.title)
    }

    @Test
    fun `encode keeps defaults available for honoring versioned imports`() {
        val encoded = codec.encode(bundle())
        assertTrue(encoded.contains("\"exportedAt\""))
        assertTrue(encoded.contains("\"appName\": \"Sokhan\""))
        assertTrue(encoded.contains("\"schemaVersion\""))
    }
}
