package com.sokhan.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokhan.chat.R
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Lightweight Markdown renderer on the CommonMark AST + Compose primitives —
 * headings, emphasis, inline code, links, block quotes, nested lists,
 * fenced code (with optional highlighting) and GFM tables.
 */
object MarkdownParser {
    val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    fun parse(markdown: String): Document = parser.parse(markdown) as Document
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    codeHighlighting: Boolean = true
) {
    val document = remember(markdown) {
        runCatching { MarkdownParser.parse(markdown) }.getOrNull() ?: MarkdownParser.parse("")
    }
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )
    val inlineCodeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        document.children().forEach { child ->
            RenderBlock(child, codeHighlighting, linkStyle, inlineCodeStyle)
        }
    }
}

private fun Node.children(): List<Node> = buildList {
    var child = firstChild
    while (child != null) {
        add(child)
        child = child.next
    }
}

@Composable
private fun RenderBlock(
    node: Node,
    highlighting: Boolean,
    linkStyle: SpanStyle,
    inlineCodeStyle: SpanStyle,
    listDepth: Int = 0
) {
    when (node) {
        is Paragraph -> RenderInlineNodes(node.children(), listDepth)
        is Heading -> {
            val style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                4 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = node.plainInnerText(),
                style = style,
                modifier = Modifier.padding(top = if (node.level <= 2) 4.dp else 0.dp)
            )
        }
        is FencedCodeBlock -> CodeBlock(node.info?.trim(), node.literal.orEmpty(), highlighting)
        is IndentedCodeBlock -> CodeBlock(null, node.literal.orEmpty(), highlighting)
        is BlockQuote -> {
            val quoteColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            Row {
                Box(Modifier.width(3.dp).background(quoteColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    node.children().forEach { RenderBlock(it, highlighting, linkStyle, inlineCodeStyle, listDepth) }
                }
            }
        }
        is BulletList -> RenderList(node, highlighting, linkStyle, inlineCodeStyle, listDepth) { "•" }
        is OrderedList -> {
            val start = node.startNumber.takeIf { it > 0 } ?: 1
            RenderList(node, highlighting, linkStyle, inlineCodeStyle, listDepth) { index -> "${index + start}." }
        }
        is ThematicBreak -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        is org.commonmark.ext.gfm.tables.TableBlock -> GfmTable(node)
        is Document -> {
            // Rare: nested document-like content (e.g. parser fallback)
            node.children().forEach { RenderBlock(it, highlighting, linkStyle, inlineCodeStyle, listDepth) }
        }
        else -> RenderInlineNodes(node.children(), listDepth, fallbackText = node.plainInnerText())
    }
}

@Composable
private fun RenderList(
    list: ListBlock,
    highlighting: Boolean,
    linkStyle: SpanStyle,
    inlineCodeStyle: SpanStyle,
    listDepth: Int,
    marker: (Int) -> String
) {
    val items: List<ListItem> = list.children().filterIsInstance<ListItem>()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(start = (listDepth * 18).dp)) {
                Text(
                    text = marker(index),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.children().forEach { child ->
                        RenderBlock(child, highlighting, linkStyle, inlineCodeStyle, listDepth + 1)
                    }
                }
            }
        }
    }
}

/** Plain-text paragraph render with inline styling (text, links, code spans). */
@Composable
private fun RenderInlineNodes(
    nodes: List<Node>,
    listDepth: Int,
    fallbackText: String = ""
) {
    val base = if (listDepth > 0) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )
    val inlineCodeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val annotated: AnnotatedString = if (nodes.isNotEmpty()) {
        buildAnnotatedString { renderInlineEach(nodes, SpanStyle(), linkStyle, inlineCodeStyle) }
    } else {
        AnnotatedString(fallbackText)
    }
    SelectionContainer {
        Text(text = annotated, style = base)
    }
}

private fun AnnotatedString.Builder.renderInlineEach(
    nodes: List<Node>,
    style: SpanStyle,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle
) {
    nodes.forEach { node -> renderInline(node, style, linkStyle, codeStyle) }
}

private fun AnnotatedString.Builder.renderInline(
    root: Node,
    style: SpanStyle,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle
) {
    when (root) {
        is Text -> appendStyled(root.literal.orEmpty(), style)
        is SoftLineBreak -> append('\n')
        is HardLineBreak -> append('\n')
        is StrongEmphasis -> renderGroup(root.children(), style.copy(fontWeight = FontWeight.Bold), linkStyle, codeStyle)
        is Emphasis -> renderGroup(root.children(), style.copy(fontStyle = FontStyle.Italic), linkStyle, codeStyle)
        is Code -> appendStyled(root.literal.orEmpty(), style.merge(codeStyle))
        is Link -> {
            pushLink(LinkAnnotation.Url(url = root.destination.orEmpty(), styles = TextLinkStyles(linkStyle)))
            renderGroup(root.children(), style, linkStyle, codeStyle)
            pop()
        }
        is Image -> appendStyled("[${root.title ?: "image"}]", style)
        else -> {
            val plain = root.plainInnerText()
            if (plain.isNotBlank()) appendStyled(plain, style)
        }
    }
}

private fun AnnotatedString.Builder.renderGroup(
    children: List<Node>,
    style: SpanStyle,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle
) {
    children.forEach { child -> renderInline(child, style, linkStyle, codeStyle) }
}

private fun SpanStyle.merge(other: SpanStyle): SpanStyle = SpanStyle(
    color = other.color ?: color,
    background = other.background ?: background,
    fontWeight = other.fontWeight ?: fontWeight,
    fontStyle = other.fontStyle ?: fontStyle,
    fontFamily = other.fontFamily ?: fontFamily,
    textDecoration = other.textDecoration ?: textDecoration
)

private fun AnnotatedString.Builder.appendStyled(value: String, style: SpanStyle) {
    if (value.isEmpty()) return
    if (style == SpanStyle()) {
        append(value)
    } else {
        pushStyle(style)
        append(value)
        pop()
    }
}

private fun Node.plainInnerText(): String = buildString {
    var child = firstChild
    while (child != null) {
        when (child) {
            is Text -> append(child.literal.orEmpty())
            is SoftLineBreak -> append(' ')
            is HardLineBreak -> append('\n')
            is Code -> append(child.literal.orEmpty())
            else -> append(child.plainInnerText())
        }
        child = child.next
    }
}

@Composable
private fun CodeBlock(language: String?, code: String, highlighting: Boolean) {
    val clipboard = LocalClipboardManager.current
    val container = MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = MaterialTheme.colorScheme.onSurface
    var copied by rememberSaveable { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp)
            ) {
                Text(
                    text = language?.takeIf { it.isNotBlank() } ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                if (copied) {
                    Text(
                        text = stringResource(R.string.common_copied),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.chat_copy_code),
                        tint = onContainer.copy(alpha = 0.7f)
                    )
                }
            }
            val highlighted: AnnotatedString = if (highlighting) {
                CodeHighlighter.highlight(
                    code = code,
                    base = onContainer,
                    keyword = MaterialTheme.colorScheme.primary,
                    stringValue = MaterialTheme.colorScheme.tertiary,
                    comment = MaterialTheme.colorScheme.outline,
                    numberValue = MaterialTheme.colorScheme.secondary
                )
            } else {
                AnnotatedString(code)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun GfmTable(table: org.commonmark.ext.gfm.tables.TableBlock) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headRows: List<TableRow> = table.children().filterIsInstance<TableHead>()
        .flatMap { it.children().filterIsInstance<TableRow>() }
    val bodyRows: List<TableRow> = table.children().filterIsInstance<TableBody>()
        .flatMap { it.children().filterIsInstance<TableRow>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(8.dp)
            )
    ) {
        headRows.forEach { row ->
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                row.children().filterIsInstance<TableCell>().forEach { cell ->
                    Box(
                        Modifier
                            .width(120.dp)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = cell.plainInnerText(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            HorizontalDivider(color = borderColor)
        }
        bodyRows.forEach { row ->
            Row {
                row.children().filterIsInstance<TableCell>().forEach { cell ->
                    Box(
                        Modifier
                            .width(120.dp)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = cell.plainInnerText(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
        }
    }
}
