package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

/** Robolectric for Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class WikipediaArticlesTest {
    private val api = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles="
    private val responses = mutableMapOf<String, Pair<Int, String>>()
    private val requested = mutableListOf<String>()

    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("not used")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("GET only")
        override fun openSingle(url: String, headers: Map<String, String>): StreamingResponse {
            requested += url
            val (status, body) = responses[url] ?: (404 to "")
            val type = if (url.contains("api.php")) "application/json" else "text/html"
            return StreamingResponse(status, mapOf("content-type" to type), ByteArrayInputStream(body.toByteArray())) {}
        }
    }

    private fun read(address: String, allowed: Set<String> = setOf("en.wikipedia.org")) =
        runBlocking { WebPages(transport, Dispatchers.Unconfined, "Bruce/test").read(address) { it in allowed } }

    @Test
    fun anArticleAddressBecomesTheSameSitesExtractRequest() {
        assertEquals(api + "October_2", WikipediaArticles.extractAddress("https://en.wikipedia.org/wiki/October_2"))
        assertEquals(api.replace("en.", "de.") + "Julius+Caesar", WikipediaArticles.extractAddress("https://de.wikipedia.org/wiki/Julius%20Caesar"))
        assertEquals(api + "C%2B%2B", WikipediaArticles.extractAddress("https://en.wikipedia.org/wiki/C++"))
    }

    @Test
    fun otherAddressesAreNotArticles() {
        listOf(
            "http://en.wikipedia.org/wiki/October_2",
            "https://en.wikipedia.org/w/index.php?title=October_2",
            "https://en.wikipedia.org/wiki/October_2?action=edit",
            "https://en.wikipedia.org/wiki/",
            "https://en.wikipedia.org.example.com/wiki/October_2",
            "https://example.com/wiki/October_2",
            "not an address",
        ).forEach { assertNull(it, WikipediaArticles.extractAddress(it)) }
    }

    @Test
    fun anArticleIsReadAsItsExtract() {
        responses[api + "Caesar"] = 200 to """{"query":{"redirects":[{"from":"Caesar","to":"Julius Caesar"}],"pages":[{"pageid":1,"ns":0,"title":"Julius Caesar","extract":"Julius Caesar was a Roman general.\n\n== Life ==\nBorn in Rome."}]}}"""

        assertEquals(
            PageResult.Read("https://en.wikipedia.org/wiki/Caesar", "Julius Caesar", "Julius Caesar was a Roman general.\n\n== Life ==\nBorn in Rome.", cut = false),
            read("https://en.wikipedia.org/wiki/Caesar"),
        )
        assertEquals(listOf(api + "Caesar"), requested)
    }

    @Test
    fun aMissingArticleOrFailedExtractIsReadAsAPage() {
        responses[api + "Nothing"] = 200 to """{"query":{"pages":[{"ns":0,"title":"Nothing","missing":true}]}}"""
        responses["https://en.wikipedia.org/wiki/Nothing"] = 404 to ""
        responses[api + "Broken"] = 500 to ""
        responses["https://en.wikipedia.org/wiki/Broken"] = 200 to "<title>Broken</title><p>Page text</p>"

        assertEquals(PageResult.Failed("The site answered with HTTP 404."), read("https://en.wikipedia.org/wiki/Nothing"))
        assertEquals(PageResult.Read("https://en.wikipedia.org/wiki/Broken", "Broken", "Page text", cut = false), read("https://en.wikipedia.org/wiki/Broken"))
        assertNull(WikipediaArticles.parse("not json"))
        assertNull(WikipediaArticles.parse("""{"query":{"pages":[{"title":"Empty","extract":"  "}]}}"""))
    }

    @Test
    fun aSiteTheUserHasNotAllowedIsNotAsked() {
        assertEquals(PageResult.Failed("The page leads to en.wikipedia.org, which the network setting does not allow."), read("https://en.wikipedia.org/wiki/October_2", allowed = emptySet()))
        assertEquals(emptyList<String>(), requested)
    }
}
