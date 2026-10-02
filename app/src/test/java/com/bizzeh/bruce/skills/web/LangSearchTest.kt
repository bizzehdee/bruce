package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException

/** Robolectric for Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class LangSearchTest {
    private var status = 200
    private var body = ""
    private var titleBody = """{"pages":[]}"""
    private var offline = false
    private var key: String? = "ls-key-123"
    private val posts = mutableListOf<Triple<String, Map<String, String>, String>>()
    private val gets = mutableListOf<Pair<String, Map<String, String>>>()

    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("redirect-following requests are not used")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("redirect-following requests are not used")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun openSingle(url: String, headers: Map<String, String>): StreamingResponse {
            gets += url to headers
            if (offline) throw IOException("offline")
            val answer = if ("/search/title" in url) titleBody else body
            return StreamingResponse(status, emptyMap(), ByteArrayInputStream(answer.toByteArray())) {}
        }
        override fun postStream(url: String, headers: Map<String, String>, body: ByteArray, readTimeoutMs: Int): StreamingResponse {
            posts += Triple(url, headers, String(body))
            if (offline) throw IOException("offline")
            return StreamingResponse(status, emptyMap(), ByteArrayInputStream(this@LangSearchTest.body.toByteArray())) {}
        }
    }
    private val langSearch = LangSearch(transport, Dispatchers.Unconfined, "Bruce/test") { key }

    private fun search() = runBlocking { langSearch.search(" dogs ") }

    @Test
    fun aQueryIsPostedWithTheUsersKeyAndTheResultsRead() {
        body = """{"code":200,"log_id":"x","msg":null,"data":{"_type":"SearchResponse","webPages":{"value":[
            {"id":"result-0","name":"Dogs","url":"https://a.example/dogs","displayUrl":"a.example","snippet":"All about dogs."},
            {"id":"result-1","name":"Full","url":"https://b.example","text":"Full page text."},
            {"id":"result-2","name":"No address","url":""}]}},"usage":{"input_tokens":5,"output_tokens":15}}"""

        val found = search() as SearchResult.Found

        assertEquals(listOf(SearchSource("Dogs", "https://a.example/dogs", listOf("All about dogs.")), SearchSource("Full", "https://b.example", listOf("Full page text."))), found.sources)
        val (url, headers, sent) = posts.single()
        assertEquals("https://api.langsearch.com/v1/web-search", url)
        assertEquals("Bearer ls-key-123", headers["Authorization"])
        assertEquals("dogs", JSONObject(sent).getString("query"))
        assertEquals(5, JSONObject(sent).getInt("count"))
    }

    @Test
    fun failuresSayWhatHappened() {
        body = "{}"
        assertEquals(SearchResult.Found(emptyList()), search())
        body = "not json"
        assertEquals(DenialCode.TOOL_FAILED, (search() as SearchResult.Failed).code)
        status = 401
        assertTrue((search() as SearchResult.Failed).reason.contains("did not accept the search key"))
        status = 429
        assertEquals(DenialCode.RATE_LIMITED, (search() as SearchResult.Failed).code)
        status = 500
        assertEquals("LangSearch answered with HTTP 500.", (search() as SearchResult.Failed).reason)
        offline = true
        assertEquals("LangSearch could not be reached.", (search() as SearchResult.Failed).reason)
        key = null
        assertEquals(DenialCode.TOOL_UNAVAILABLE, (search() as SearchResult.Failed).code)
    }

    @Test
    fun wikipediaNeedsNoKeyAndSearchesThePhonesLanguage() {
        body = javaClass.getResource("/web/wikipedia-search.json")!!.readText()
        var language = "fr"
        val wikipedia = WikipediaSearch(transport, Dispatchers.Unconfined, "Bruce/test") { language }

        val found = runBlocking { wikipedia.search("capital of australia") } as SearchResult.Found

        assertEquals(
            listOf("https://fr.wikipedia.org/w/rest.php/v1/search/page?q=capital+of+australia&limit=5", "https://fr.wikipedia.org/w/rest.php/v1/search/title?q=capital+of+australia&limit=3"),
            gets.map { it.first },
        )
        assertTrue(gets.all { it.second["User-Agent"] == "Bruce/test" })
        val first = found.sources.first()
        assertEquals("Australian Capital Territory", first.title)
        assertEquals("https://fr.wikipedia.org/wiki/Australian_Capital_Territory", first.url)
        assertTrue(first.snippets.joinToString().contains("Canberra"))
        assertTrue("search markup is removed", found.sources.flatMap { it.snippets }.none { "<span" in it })
        language = "not a language"
        assertEquals("en.wikipedia.org", wikipedia.host)
        status = 503
        assertEquals("Wikipedia answered with HTTP 503.", (runBlocking { wikipedia.search("x") } as SearchResult.Failed).reason)
    }

    @Test
    fun anArticleTitledAsTheQueryComesFirst() {
        body = """{"pages":[{"key":"2012_in_American_television","title":"2012 in American television","excerpt":"events","description":"x"},{"key":"October_2","title":"October 2","excerpt":"day","description":"Day of the year"}]}"""
        titleBody = """{"pages":[{"key":"October_2","title":"October 2","matched_title":"2 October","description":"Day of the year"},{"key":"2_October_(film)","title":"2 October (film)","matched_title":null}]}"""
        val wikipedia = WikipediaSearch(transport, Dispatchers.Unconfined, "Bruce/test") { "en" }

        val found = runBlocking { wikipedia.search("2 October") } as SearchResult.Found

        assertEquals(listOf("October 2", "2012 in American television"), found.sources.map { it.title })
        assertEquals(listOf("Day of the year"), found.sources.first().snippets)
    }

    @Test
    fun searchResultsPointTheModelAtReadingThePage() = runBlocking {
        body = """{"pages":[{"key":"October_2","title":"October 2","excerpt":"day","description":"Day of the year"}]}"""
        val wikipedia = WikipediaSearch(transport, Dispatchers.Unconfined, "Bruce/test") { "en" }
        val skill = WebSkills(WebPages(transport, Dispatchers.Unconfined, "Bruce/test"), { true }, wikipedia = wikipedia).create().single { it.id == "search_wikipedia" }

        val text = (skill.execute(SkillArguments(mapOf("query" to "October 2"))) as SkillOutcome.Done).content

        assertTrue(text, text.endsWith("read it with fetch_page and its address."))
        assertTrue(skill.description.contains("not a question"))
    }

    @Test
    fun theSkillsReachOnlyTheirOwnHosts() = runBlocking {
        val wikipedia = WikipediaSearch(transport, Dispatchers.Unconfined, "Bruce/test") { "en" }
        val skills = WebSkills(WebPages(transport, Dispatchers.Unconfined, "Bruce/test"), { true }, langSearch, wikipedia).create()
        val web = skills.single { it.id == "web_search" }
        val wiki = skills.single { it.id == "search_wikipedia" }

        assertEquals("api.langsearch.com", web.site!!(SkillArguments(mapOf("query" to "x"))))
        assertEquals("en.wikipedia.org", wiki.site!!(SkillArguments(mapOf("query" to "x"))))
        assertEquals(setOf(SkillRequirement.NETWORK_ALLOWED), wiki.requires)
        status = 503
        assertTrue(wiki.execute(SkillArguments(mapOf("query" to "x"))) is SkillOutcome.Failed)
    }
}
