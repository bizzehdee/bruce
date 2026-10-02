package com.bizzeh.bruce.skills.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlTextFurnitureTest {
    @Test
    fun menusAndLinkOnlyListsAreLeftOutButListsWithTextStay() {
        val html = """
            <html><body>
            <header><a href="/">Home</a> Site name</header>
            <nav><ul><li><a href="/a">A</a></li><li><a href="/b">B</a></li></ul></nav>
            <main>
              <h1>October 2</h1>
              <ul class="languages"><li><a href="https://fr.example.org/x">Français</a></li><li><a href="https://de.example.org/x">Deutsch</a></li><li><a href="https://es.example.org/x">Español</a></li></ul>
              <ul><li><a href="https://en.example.org/wiki/48_BC">48 BC</a> – Caesar arrives in Egypt.</li><li>811 – A new emperor.</li><li>829 – Another.</li></ul>
              <ul><li><a href="https://en.example.org/one">One</a></li><li><a href="https://en.example.org/two">Two</a></li></ul>
            </main>
            <div role="contentinfo">Footer text</div>
            <aside>Related</aside>
            </body></html>
        """.trimIndent()

        val text = HtmlText.text(html, "en.example.org")

        assertEquals("# October 2\n\n- 48 BC – Caesar arrives in Egypt.\n- 811 – A new emperor.\n- 829 – Another.\n\n- One\n- Two", text)
    }

    @Test
    fun linksToOtherSitesKeepTheirAddresses() {
        val text = HtmlText.text("""<p>See <a href="https://en.example.org/x">this</a> and <a href="https://other.example/y">that</a>.</p>""", "en.example.org")
        assertEquals("See this and that (https://other.example/y).", text)
    }

    @Test
    fun anArticleHeaderStaysButThePagesOwnHeaderGoes() {
        val text = HtmlText.text("<header>Site</header><article><header><h2>Title</h2></header><p>Body.</p></article>")
        assertEquals("# Title\n\nBody.", text)
    }
}
