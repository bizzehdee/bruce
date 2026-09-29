package com.bizzeh.bruce.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MarkdownTest {
    private fun plain(text: String) = MdInline.Plain(text)

    @Test
    fun headingsParagraphsAndInlineStyles() {
        val blocks = Markdown.parse("# Title\n\nSome *soft* and **bold** with `code`\nwrapped.")

        assertEquals(
            listOf(
                MdBlock.Heading(1, listOf(plain("Title"))),
                MdBlock.Paragraph(
                    listOf(
                        plain("Some "), MdInline.Emphasis(listOf(plain("soft"))), plain(" and "), MdInline.Strong(listOf(plain("bold"))),
                        plain(" with "), MdInline.Code("code"), plain(" "), plain("wrapped."),
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun codeBlocksKeepTheirTextAndAnUnclosedFenceIsCodeSoFar() {
        assertEquals(listOf(MdBlock.CodeBlock("kotlin", "val x = 1\n  *not emphasis*")), Markdown.parse("```kotlin\nval x = 1\n  *not emphasis*\n```"))
        assertEquals(listOf(MdBlock.CodeBlock("py", "print(1)")), Markdown.parse("```py\nprint(1)"), "streaming")
        assertEquals(listOf(MdBlock.CodeBlock(null, "indented")), Markdown.parse("    indented"))
    }

    @Test
    fun listsQuotesRulesAndTables() {
        val list = Markdown.parse("3. three\n4. four\n   - nested").single() as MdBlock.ListBlock
        assertEquals(true, list.ordered)
        assertEquals(3, list.start)
        assertEquals(MdBlock.Paragraph(listOf(plain("three"))), list.items[0].single())
        assertEquals(MdBlock.ListBlock(false, 1, listOf(listOf(MdBlock.Paragraph(listOf(plain("nested")))))), list.items[1][1])

        assertEquals(listOf(MdBlock.Quote(listOf(MdBlock.Paragraph(listOf(plain("quoted")))))), Markdown.parse("> quoted"))
        assertEquals(MdBlock.Rule, Markdown.parse("a\n\n---\n\nb")[1])

        val table = Markdown.parse("| Item | Aisle |\n|---|---|\n| Milk | 3 |\n| Bread |").single() as MdBlock.Table
        assertEquals(listOf(listOf(plain("Item")), listOf(plain("Aisle"))), table.header)
        assertEquals(listOf(listOf(listOf(plain("Milk")), listOf(plain("3"))), listOf(listOf(plain("Bread")), emptyList())), table.rows)
    }

    @Test
    fun rawHtmlIsShownAsText() {
        assertEquals(listOf(MdBlock.Paragraph(listOf(plain("<div onclick=\"x()\">hi</div>")))), Markdown.parse("<div onclick=\"x()\">hi</div>"))
        assertEquals(listOf(MdBlock.Paragraph(listOf(plain("a "), plain("<b>"), plain("b"), plain("</b>")))), Markdown.parse("a <b>b</b>"))
    }

    @Test
    fun onlyWebLinksCanBeOpened() {
        val links = (Markdown.parse("[a](https://example.com/x) [b](http://example.com) [c](javascript:alert(1)) [d](intent://x)").single() as MdBlock.Paragraph)
            .content.filterIsInstance<MdInline.Link>()
        assertEquals(listOf("https://example.com/x", "http://example.com", null, null), links.map { it.url })
        assertEquals(listOf(plain("c")), links[2].children)
        assertNull(Markdown.openable("file:///sdcard/x"))
    }

    @Test
    fun imagesStandApartAndOnlyHttpsCanBeLoaded() {
        val blocks = Markdown.parse("Look: ![a cat](https://example.com/cat.png) and ![b](http://example.com/b.png)")

        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(plain("Look: "))),
                MdBlock.Image("https://example.com/cat.png", "a cat", "https://example.com/cat.png"),
                MdBlock.Paragraph(listOf(plain(" and "))),
                MdBlock.Image(null, "b", "http://example.com/b.png"),
            ),
            blocks,
        )
        val linked = Markdown.parse("[![logo](https://example.com/l.png)](https://example.com)").single() as MdBlock.Paragraph
        assertEquals(MdInline.Link("https://example.com", listOf(plain("logo"))), linked.content.single(), "an image inside a link is only its description")
    }
}
