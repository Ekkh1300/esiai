package com.sokhan.chat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {

    @Test
    fun `newId is 16 chars from the lowercase alphanumeric alphabet`() {
        repeat(50) {
            val id = Ids.newId()
            assertEquals(16, id.length)
            assertTrue("unexpected chars in $id", id.matches(Regex("^[a-z0-9]{16}$")))
        }
    }

    @Test
    fun `newId is unique across many generations`() {
        val ids = (1..200).map { Ids.newId() }.toSet()
        assertEquals(200, ids.size)
    }

    @Test
    fun `token estimator is zero for blank input`() {
        assertEquals(0, TokenEstimator.estimate(""))
        assertEquals(0, TokenEstimator.estimate("   "))
    }

    @Test
    fun `token estimator scales latin text at roughly chars-over-four`() {
        // 14 printable code points -> ceil-ish 3 with the /4 heuristic (3.5 -> 3)
        assertEquals(3, TokenEstimator.estimate("hello world po"))
        assertEquals(4, TokenEstimator.estimate("hello world this"))
    }

    @Test
    fun `token estimator charges CJK glyphs per-character`() {
        // Two CJK code points at 0.85 each -> 1
        assertEquals(1, TokenEstimator.estimate("你好"))
        // Four CJK code points -> 3
        assertEquals(3, TokenEstimator.estimate("你好世界"))
    }

    @Test
    fun `token estimator is monotonic in text length`() {
        val short = TokenEstimator.estimate("hello")
        val long = TokenEstimator.estimate("hello world and a much longer sentence")
        assertTrue(short < long)
    }
}
