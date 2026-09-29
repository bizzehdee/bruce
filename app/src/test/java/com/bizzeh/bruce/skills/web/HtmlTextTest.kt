package com.bizzeh.bruce.skills.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HtmlTextTest {
    @Test
    fun headingsParagraphsListsAndLinksBecomeText() {
        val html = """
            <html><head><title>Bruce &amp; friends</title><style>p{color:red}</style></head>
            <body><h1>Hello</h1><p>First   para with <b>bold</b>.</p>
            <ul><li>one</li><li>two</li></ul>
            <p>See <a href="https://example.com/docs">the docs</a> or <a href="https://example.com">https://example.com</a> or <a href="/relative">here</a>.</p>
            <table><tr><td>a</td><td>b</td></tr></table>
            </body></html>
        """.trimIndent()

        assertEquals(
            "# Hello\n\nFirst para with bold.\n\n- one\n- two\n\nSee the docs (https://example.com/docs) or https://example.com or here.\n\na b",
            HtmlText.text(html),
        )
        assertEquals("Bruce & friends", HtmlText.title(html))
    }

    @Test
    fun scriptsStylesAndHiddenContentAreLeftOut() {
        val html = """
            <p>shown</p><script>var s = "<p>not shown</p>";</script><noscript>no</noscript>
            <div hidden>hidden attr</div><span aria-hidden="true">aria</span>
            <div style="display: none"><div>nested</div> still hidden</div><p style="visibility:hidden">invisible</p>
            <input type="hidden" value="secret"><!-- a comment <p>not shown</p> --><p>end</p>
        """.trimIndent()

        assertEquals("shown\n\nend", HtmlText.text(html))
    }

    @Test
    fun entitiesAreDecodedAndBrokenMarkupDoesNotStopIt() {
        assertEquals("a < b & c > d \" ' — é 😀 &unknown; &#0;", HtmlText.text("a &lt; b &amp; c &gt; d &quot; &#39; &mdash; &#233; &#x1F600; &unknown; &#0;"))
        assertEquals("text\n\nafter", HtmlText.text("<p class=\"a>b\">text</p><br/><img src=x alt='y'> after <!-- never closed"))
        assertEquals("", HtmlText.text("<div unclosed=\"yes>tag"), "an unclosed quote runs to the end, as in a browser")
        assertEquals("x", HtmlText.text("<>x</ > <!DOCTYPE html><?xml?>"))
        assertNull(HtmlText.title("<p>no title</p>"))
        assertNull(HtmlText.title("<title>  </title>"))
    }
}
