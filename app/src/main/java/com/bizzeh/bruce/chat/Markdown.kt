package com.bizzeh.bruce.chat

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/** Inline Markdown: text with its emphasis, code spans and links. */
sealed interface MdInline {
    data class Plain(val text: String) : MdInline

    data class Emphasis(val children: List<MdInline>) : MdInline

    data class Strong(val children: List<MdInline>) : MdInline

    data class Code(val text: String) : MdInline

    /** [url] is null when the link cannot be opened (not http or https); its text is still shown. */
    data class Link(val url: String?, val children: List<MdInline>) : MdInline

    data object LineBreak : MdInline
}

/** Block Markdown, as Bruce draws it. */
sealed interface MdBlock {
    data class Heading(val level: Int, val content: List<MdInline>) : MdBlock

    data class Paragraph(val content: List<MdInline>) : MdBlock

    data class CodeBlock(val language: String?, val code: String) : MdBlock

    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<List<MdBlock>>) : MdBlock

    data class Quote(val blocks: List<MdBlock>) : MdBlock

    data class Table(val header: List<List<MdInline>>, val rows: List<List<List<MdInline>>>) : MdBlock

    data object Rule : MdBlock

    /** Never fetched until the user asks; [url] is null when it is not an https address. */
    data class Image(val url: String?, val alt: String, val address: String) : MdBlock
}

/**
 * Parses replies with commonmark-java. Nothing is rendered as HTML: raw HTML stays visible text.
 * A reply still streaming parses as it stands; CommonMark runs an unclosed code fence to the end,
 * so a code block shows as code while it is written.
 */
object Markdown {
    private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

    fun parse(text: String): List<MdBlock> = blocks(parser.parse(text))

    /** http and https only: other schemes (intent:, file:, javascript:) never open. */
    fun openable(url: String): String? = url.trim().takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

    private fun blocks(parent: Node): List<MdBlock> = children(parent).flatMap(::block)

    private fun block(node: Node): List<MdBlock> = when (node) {
        is Heading -> listOf(MdBlock.Heading(node.level, inlines(node)))
        is Paragraph -> paragraph(node)
        is FencedCodeBlock -> listOf(MdBlock.CodeBlock(node.info?.trim()?.substringBefore(' ')?.ifEmpty { null }, node.literal.removeSuffix("\n")))
        is IndentedCodeBlock -> listOf(MdBlock.CodeBlock(null, node.literal.removeSuffix("\n")))
        is BulletList -> listOf(MdBlock.ListBlock(ordered = false, start = 1, items = children(node).map { blocks(it) }))
        is OrderedList -> listOf(MdBlock.ListBlock(ordered = true, start = node.markerStartNumber ?: 1, items = children(node).map { blocks(it) }))
        is ListItem -> blocks(node)
        is BlockQuote -> listOf(MdBlock.Quote(blocks(node)))
        is ThematicBreak -> listOf(MdBlock.Rule)
        is HtmlBlock -> listOf(MdBlock.Paragraph(listOf(MdInline.Plain(node.literal.removeSuffix("\n")))))
        is TableBlock -> listOf(table(node))
        else -> blocks(node)
    }

    /** Images become blocks of their own, between the text around them. */
    private fun paragraph(node: Paragraph): List<MdBlock> {
        val result = mutableListOf<MdBlock>()
        var run = mutableListOf<MdInline>()
        for (child in children(node)) {
            if (child is Image) {
                if (run.isNotEmpty()) result += MdBlock.Paragraph(trimmed(run))
                run = mutableListOf()
                val alt = plainText(child)
                result += MdBlock.Image(child.destination.trim().takeIf { it.startsWith("https://", ignoreCase = true) }, alt, child.destination)
            } else {
                run += inline(child)
            }
        }
        if (run.isNotEmpty()) trimmed(run).takeIf { it.isNotEmpty() }?.let { result += MdBlock.Paragraph(it) }
        return result
    }

    private fun trimmed(run: List<MdInline>) = run.dropWhile { it == MdInline.LineBreak }.dropLastWhile { it == MdInline.LineBreak }

    private fun table(node: TableBlock): MdBlock.Table {
        val sections = children(node)
        val header = sections.filterIsInstance<TableHead>().flatMap { children(it) }.firstOrNull()?.let(::cells).orEmpty()
        val rows = sections.filterIsInstance<TableBody>().flatMap { children(it) }.filterIsInstance<TableRow>().map(::cells)
        return MdBlock.Table(header, rows)
    }

    private fun cells(row: Node): List<List<MdInline>> = children(row).filterIsInstance<TableCell>().map { inlines(it) }

    private fun inlines(parent: Node): List<MdInline> = children(parent).flatMap(::inline)

    private fun inline(node: Node): List<MdInline> = when (node) {
        is Text -> listOf(MdInline.Plain(node.literal))
        is Emphasis -> listOf(MdInline.Emphasis(inlines(node)))
        is StrongEmphasis -> listOf(MdInline.Strong(inlines(node)))
        is Code -> listOf(MdInline.Code(node.literal))
        is Link -> listOf(MdInline.Link(openable(node.destination), inlines(node)))
        is SoftLineBreak -> listOf(MdInline.Plain(" "))
        is HardLineBreak -> listOf(MdInline.LineBreak)
        is HtmlInline -> listOf(MdInline.Plain(node.literal))
        // An image inside a link or emphasis is shown by its description; it is never fetched.
        is Image -> listOf(MdInline.Plain(plainText(node)))
        else -> inlines(node)
    }

    private fun plainText(node: Node): String = buildString {
        fun walk(n: Node) {
            when (n) {
                is Text -> append(n.literal)
                is Code -> append(n.literal)
                is SoftLineBreak, is HardLineBreak -> append(' ')
                else -> children(n).forEach(::walk)
            }
        }
        walk(node)
    }

    private fun children(node: Node): List<Node> = generateSequence(node.firstChild) { it.next }.toList()
}
