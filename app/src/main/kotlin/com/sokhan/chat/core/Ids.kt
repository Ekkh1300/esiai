package com.sokhan.chat.core

import java.security.SecureRandom
import java.text.BreakIterator
import java.util.Locale

/**
 * Small, dependency-free helpers used across layers.
 */
object Ids {
    private val random = SecureRandom()
    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** Short random id (16 chars) used for local rows; crypto-strength enough for ids. */
    fun newId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val chars = StringBuilder(16)
        for (b in bytes) {
            // `and 0x7F` keeps the index positive; alphabet length <= 128 fits.
            chars.append(alphabet[(b.toInt() and 0x7F) % alphabet.length])
        }
        return chars.toString()
    }
}

/**
 * Rough token estimation for the "≈N tok" counters. The real usage figures come
 * from the provider response when available; this estimate is only shown when
 * the API does not return usage data. Reference: [TokenEstimator] in common
 * open-source clients uses a chars/4 heuristic for English and chars/2 for
 * CJK-heavy text.
 */
object TokenEstimator {
    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        var cjk = 0
        // BreakIterator gives us word/grapheme-ish segmentation without regex cost.
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        var codePoints = 0
        var previous = iterator.first()
        var current = iterator.next()
        while (current != BreakIterator.DONE) {
            codePoints++
            val ch = text.codePointAt(previous)
            if (isCjk(ch)) cjk++ else cjk += 0
            previous = current
            current = iterator.next()
        }
        val latin = codePoints - cjk
        return (latin / 4.0).toInt() + (cjk * 0.85).toInt()
    }

    private fun isCjk(codePoint: Int): Boolean =
        (codePoint in 0x4E00..0x9FFF) || (codePoint in 0x3040..0x30FF) || (codePoint in 0xAC00..0xD7AF)
}
