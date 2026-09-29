package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException

/** Robolectric for Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class BraveSearchTest {
    private var status = 200
    private var body = ""
    private var offline = false
    private var key: String? = "user-key-123"
    private val requests = mutableListOf<Pair<String, Map<String, String>>>()

    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("the key must not follow redirects")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("the key must not follow redirects")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun openSingle(url: String, headers: Map<String, String>): StreamingResponse {
            requests += url to headers
            if (offline) throw IOException("offline")
            return StreamingResponse(status, emptyMap(), ByteArrayInputStream(body.toByteArray())) {}
        }
    }
    private val brave = BraveSearch(transport, Dispatchers.Unconfined, "Bruce/test") { key }

    private fun search(query: String = "bruce the dog") = runBlocking { brave.search(query) }

    @Test
    fun aQueryGoesToBraveWithTheUsersKeyAndSmallestBudget() {
        body = """{"grounding":{"generic":[{"url":"https://a.example/x","title":"A","snippets":["one","","two"]},{"url":"","title":"no address"},{"url":"https://b.example","snippets":[]}],"map":[]},"sources":{}}"""

        val found = search("dogs & cats") as SearchResult.Found

        assertEquals(listOf(SearchSource("A", "https://a.example/x", listOf("one", "two")), SearchSource("", "https://b.example", emptyList())), found.sources)
        val (url, headers) = requests.single()
        assertEquals("https://api.search.brave.com/res/v1/llm/context?q=dogs+%26+cats&maximum_number_of_tokens=1024&maximum_number_of_urls=5", url)
        assertEquals("user-key-123", headers["X-Subscription-Token"])
    }

    @Test
    fun failuresSayWhatHappenedWithoutTheKey() {
        body = "{}"
        assertEquals(SearchResult.Found(emptyList()), search())
        body = "not json"
        assertEquals(DenialCode.TOOL_FAILED, (search() as SearchResult.Failed).code)
        status = 401
        assertTrue((search() as SearchResult.Failed).reason.contains("did not accept the search key"))
        status = 429
        assertEquals(DenialCode.RATE_LIMITED, (search() as SearchResult.Failed).code)
        status = 302
        assertEquals("Brave's search answered with HTTP 302.", (search() as SearchResult.Failed).reason)
        offline = true
        assertEquals("Brave's search could not be reached.", (search() as SearchResult.Failed).reason)
        key = null
        assertEquals(DenialCode.TOOL_UNAVAILABLE, (search() as SearchResult.Failed).code)
        requests.forEach { (_, headers) -> assertTrue(headers.values.none { it.contains("user-key-123") && it != "user-key-123" }) }
    }

    @Test
    fun theSkillIsOffLockedAndReachesOnlyBrave() = runBlocking {
        val skill = WebSkills(WebPages(transport, Dispatchers.Unconfined, "Bruce/test"), { true }, brave).create().single { it.id == "web_search" }
        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(ResourceScope.WEB, skill.scope)
        assertEquals(setOf(SkillRequirement.NETWORK_ALLOWED, SkillRequirement.SEARCH_KEY), skill.requires)
        assertEquals("api.search.brave.com", skill.site!!(SkillArguments(mapOf("query" to "anything"))))

        body = """{"grounding":{"generic":[{"url":"https://a.example","title":"A","snippets":["one"]},{"url":"https://b.example","snippets":["two"]}]}}"""
        assertEquals(SkillOutcome.Done("1. A\nhttps://a.example\none\n\n2. https://b.example\nhttps://b.example\ntwo"), skill.execute(SkillArguments(mapOf("query" to "q"))))
        body = "{}"
        assertEquals(SkillOutcome.Done("No results."), skill.execute(SkillArguments(mapOf("query" to "q"))))
        status = 429
        assertEquals(true, (skill.execute(SkillArguments(mapOf("query" to "q"))) as SkillOutcome.Failed).retryable)
        assertEquals(listOf("fetch_page"), WebSkills(WebPages(transport, Dispatchers.Unconfined, "x"), { true }).create().map { it.id })
    }
}
