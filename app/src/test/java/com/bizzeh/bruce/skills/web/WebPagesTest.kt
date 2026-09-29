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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class WebPagesTest {
    private class Site(val status: Int, val headers: Map<String, String>, val body: ByteArray = ByteArray(0))

    private val sites = mutableMapOf<String, Site>()
    private val requested = mutableListOf<Pair<String, Map<String, String>>>()
    private var offline = false

    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("pages must not follow redirects blindly")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("GET only")

        override fun openSingle(url: String, headers: Map<String, String>): StreamingResponse {
            requested += url to headers
            if (offline) throw IOException("offline")
            val site = sites[url] ?: Site(404, emptyMap())
            return StreamingResponse(site.status, site.headers, ByteArrayInputStream(site.body)) {}
        }
    }
    private val pages = WebPages(transport, Dispatchers.Unconfined, "Bruce/test")
    private var allowedHosts = setOf("example.com")

    private fun read(address: String) = runBlocking { pages.read(address) { it in allowedHosts } }

    private fun html(body: String, charset: String = "utf-8") = Site(200, mapOf("content-type" to "text/html; charset=$charset"), body.toByteArray(java.nio.charset.Charset.forName(charset)))

    @Test
    fun anHtmlPageBecomesTextWithItsTitle() {
        sites["https://example.com/a"] = html("<title>A</title><p>Hello</p>")

        assertEquals(PageResult.Read("https://example.com/a", "A", "Hello", cut = false), read("https://example.com/a"))
        assertEquals(setOf("User-Agent", "Accept"), requested.single().second.keys)
    }

    @Test
    fun plainTextIsKeptAndOtherTypesAreRefused() {
        sites["https://example.com/t"] = Site(200, mapOf("content-type" to "text/plain"), " just text \n".toByteArray())
        sites["https://example.com/j"] = Site(200, emptyMap(), "{\"a\":1}".toByteArray())
        sites["https://example.com/i"] = Site(200, mapOf("content-type" to "image/png"), byteArrayOf(1, 2))

        assertEquals(PageResult.Read("https://example.com/t", null, "just text", false), read("https://example.com/t"))
        assertEquals("{\"a\":1}", (read("https://example.com/j") as PageResult.Read).text)
        assertEquals(PageResult.Failed("The page is not text (image/png)."), read("https://example.com/i"))
    }

    @Test
    fun redirectsAreFollowedOnlyToAllowedSitesAndNeverToInsecureAddresses() {
        sites["https://example.com/old"] = Site(301, mapOf("location" to "/new"))
        sites["https://example.com/new"] = html("<p>moved</p>")
        sites["https://example.com/away"] = Site(302, mapOf("location" to "https://tracker.net/x"))
        sites["https://example.com/down"] = Site(302, mapOf("location" to "http://example.com/plain"))
        sites["https://example.com/loop"] = Site(302, mapOf("location" to "/loop"))

        assertEquals(PageResult.Read("https://example.com/new", null, "moved", false), read("https://example.com/old"))
        assertEquals(PageResult.Failed("The page leads to tracker.net, which the network setting does not allow."), read("https://example.com/away"))
        assertTrue((read("https://example.com/down") as PageResult.Failed).reason.contains("insecure"))
        assertEquals(PageResult.Failed("The page redirects too many times."), read("https://example.com/loop"))
    }

    @Test
    fun failuresAreExplainedWithoutContent() {
        assertEquals(PageResult.Failed("That is not an http or https address."), read("file:///sdcard/x"))
        assertEquals(PageResult.Failed("The site answered with HTTP 404."), read("https://example.com/missing"))
        offline = true
        assertEquals(PageResult.Failed("The site could not be reached."), read("https://example.com/a"))
    }

    @Test
    fun bigPagesAreCutAndCharsetsAreRespected() {
        sites["https://example.com/big"] = Site(200, mapOf("content-type" to "text/plain"), ByteArray(WebPages.MAX_BYTES + 10) { 'a'.code.toByte() })
        sites["https://example.com/latin"] = html("<p>café</p>", "iso-8859-1")
        sites["https://example.com/odd"] = Site(200, mapOf("content-type" to "text/plain; charset=nonsense-9"), "ok".toByteArray())

        val big = read("https://example.com/big") as PageResult.Read
        assertTrue(big.cut)
        assertEquals(WebPages.MAX_BYTES, big.text.length)
        assertEquals("café", (read("https://example.com/latin") as PageResult.Read).text)
        assertEquals("ok", (read("https://example.com/odd") as PageResult.Read).text)
    }

    @Test
    fun theSkillIsOffLockedWhileOfflineAndReportsThePage() = runBlocking {
        sites["https://example.com/a"] = html("<title>A</title><p>Hello</p>")
        sites["https://example.com/empty"] = html("<script>x</script>")
        sites["https://example.com/away"] = Site(302, mapOf("location" to "https://elsewhere.org/"))
        sites["https://elsewhere.org/"] = html("<p>there</p>")
        allowedHosts = emptySet()
        val skill = WebSkills(pages) { false }.create().single()

        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(ResourceScope.WEB, skill.scope)
        assertEquals(SkillRequirement.NETWORK_ALLOWED, skill.requires)
        assertEquals("example.com", skill.site!!(SkillArguments(mapOf("url" to "https://example.com/a"))))
        assertEquals(SkillOutcome.Done("Address: https://example.com/a\nTitle: A\n\nHello"), skill.execute(SkillArguments(mapOf("url" to "https://example.com/a"))))
        assertEquals(SkillOutcome.Done("Address: https://example.com/empty\n\n(The page has no readable text.)"), skill.execute(SkillArguments(mapOf("url" to "https://example.com/empty"))))
        val refused = skill.execute(SkillArguments(mapOf("url" to "https://example.com/away"))) as SkillOutcome.Failed
        assertEquals(DenialCode.TOOL_FAILED, refused.code)
        assertTrue(refused.message.contains("elsewhere.org"), "a redirect to a site not allowed is refused, even after the first was approved")
    }
}
