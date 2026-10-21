package com.sokhan.chat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Small dependency-free syntax highlighter. A single-token scanner over the
 * code string, keyword sets cover the most common languages (Kotlin, Java,
 * Python, JS/TS, C-style, Rust, Go, SQL). It is intentionally approximate —
 * Sokhan values zero extra dependencies over perfect lexing.
 */
object CodeHighlighter {

    private val KEYWORDS = setOf(
        // Kotlin / Java
        "fun", "val", "var", "if", "else", "when", "for", "while", "return", "class",
        "object", "interface", "data", "sealed", "enum", "import", "package", "try",
        "catch", "finally", "throw", "is", "in", "as", "super", "this", "null", "true",
        "false", "suspend", "private", "public", "internal", "protected", "override",
        "open", "const", "companion", "init", "static", "void", "extends", "implements",
        "new", "instanceof", "switch", "case", "break", "continue", "default", "do",
        // Python / Ruby / shell
        "def", "elif", "lambda", "self", "let", "function", "async", "await", "from",
        "with", "end", "then", "fi", "echo", "local", "const",
        // JS / TS
        "typeof", "delete", "yield", "export", "type", "readonly", "declare", "namespace",
        // Rust / Go
        "fn", "match", "mut", "pub", "use", "mod", "crate", "impl", "struct", "trait",
        "where", "go", "defer", "chan", "select", "map", "range",
        // SQL (upper)
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE", "JOIN", "LEFT",
        "INNER", "GROUP", "ORDER", "BY", "LIMIT", "CREATE", "TABLE", "VALUES", "SET"
    )

    fun highlight(
        code: String,
        base: Color,
        keyword: Color,
        stringValue: Color,
        comment: Color,
        numberValue: Color
    ): AnnotatedString = buildAnnotatedString {
        val keywordStyle = SpanStyle(color = keyword, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        val stringStyle = SpanStyle(color = stringValue)
        val commentStyle = SpanStyle(color = comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        val numberStyle = SpanStyle(color = numberValue)
        val baseStyle = SpanStyle(color = base)

        var index = 0
        val length = code.length
        var normalStart = 0

        fun flush(upto: Int) {
            if (upto > normalStart) {
                appendStyleless(code.substring(normalStart, upto), baseStyle)
            }
        }

        while (index < length) {
            val char = code[index]
            when {
                // comments
                char == '/' && index + 1 < length && code[index + 1] == '/' -> {
                    flush(index)
                    val end = code.indexOf('\n', index).let { if (it == -1) length else it }
                    appendStyleless(code.substring(index, end), commentStyle)
                    index = end
                    normalStart = end
                }
                char == '/' && index + 1 < length && code[index + 1] == '*' -> {
                    flush(index)
                    val end = code.indexOf("*/", index + 2)
                    val stop = if (end == -1) length else end + 2
                    appendStyleless(code.substring(index, stop), commentStyle)
                    index = stop
                    normalStart = stop
                }
                char == '#' && (index == 0 || code[index - 1].isWhitespace()) -> {
                    flush(index)
                    val end = code.indexOf('\n', index).let { if (it == -1) length else it }
                    appendStyleless(code.substring(index, end), commentStyle)
                    index = end
                    normalStart = end
                }
                // strings
                char == '"' || char == '\'' || char == '`' -> {
                    flush(index)
                    var cursor = index + 1
                    while (cursor < length) {
                        if (code[cursor] == '\\' && cursor + 1 < length) {
                            cursor += 2
                            continue
                        }
                        if (code[cursor] == char) {
                            cursor++
                            break
                        }
                        if (char != '`' && code[cursor] == '\n') break
                        cursor++
                    }
                    appendStyleless(code.substring(index, cursor.coerceAtMost(length)), stringStyle)
                    index = cursor
                    normalStart = cursor
                }
                // numbers
                char.isDigit() -> {
                    var cursor = index
                    while (cursor < length &&
                        (code[cursor].isDigit() ||
                            code[cursor] == '.' ||
                            (code[cursor] == 'x' || code[cursor] == 'X' || code[cursor] == 'b' || code[cursor] == '_') &&
                            cursor > index && code[cursor - 1].let { it == '0' || it == 'x' || it == 'X' })
                    ) {
                        cursor++
                    }
                    flush(index)
                    appendStyleless(code.substring(index, cursor), numberStyle)
                    index = cursor
                    normalStart = cursor
                }
                // keywords
                char.isLetter() -> {
                    var cursor = index
                    while (cursor < length && (code[cursor].isLetterOrDigit() || code[cursor] == '_')) {
                        cursor++
                    }
                    val token = code.substring(index, cursor)
                    if (token in KEYWORDS) {
                        flush(cursor)
                        appendStyleless(token, keywordStyle)
                        index = cursor
                        normalStart = cursor
                    } else {
                        index = cursor
                    }
                }
                else -> index++
            }
        }
        flush(length)
    }

    private fun AnnotatedString.Builder.appendStyleless(value: String, style: SpanStyle) {
        if (value.isEmpty()) return
        pushStyle(style)
        append(value)
        pop()
    }
}
