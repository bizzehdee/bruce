package com.bizzeh.bruce.policy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SitesTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private var mode = NetworkMode.APPROVED_DOMAINS
    private val sites = SiteAccess(database.policy(), { mode }) { 5 }
    private val states = SkillStateStore(database.policy())
    private val runs = mutableListOf<String>()

    private fun web(id: String, default: SkillState) = Skill(
        id, 1, "Fetch.", InputSchema(listOf(Parameter("url", ParameterType.STRING, "Address"))), setOf(Capability.HTTP_READ), default,
        scope = ResourceScope.WEB, site = { WebAddress.host(it.string("url").orEmpty()) },
    ) {
        runs += id
        SkillOutcome.Done("page")
    }

    private val fetch = web("fetch_page", SkillState.ACCEPTED)
    private val careful = web("careful_fetch", SkillState.ASK)
    private val registry = SkillRegistry(listOf(fetch, careful))
    private val engine = PolicyEngine(registry, states, ToolOutput(), permissionGranted = { true }, scope = { error("web skills are checked by site") }, sites = sites)

    @After
    fun tearDown() = database.close()

    private fun decide(tool: String, url: String) = runBlocking { engine.decide(tool, """{"url":"$url"}""") }

    @Test
    fun onlyPlainWebAddressesNameASite() {
        assertEquals("example.com", WebAddress.host(" HTTPS://Example.COM./a?b=c "))
        assertEquals("example.com", WebAddress.host("http://example.com:8080/"))
        assertNull(WebAddress.host("ftp://example.com"))
        assertNull(WebAddress.host("https://user:secret@example.com/"))
        assertNull(WebAddress.host("javascript:alert(1)"))
        assertNull(WebAddress.host("https://exa mple.com"))
        assertNull(WebAddress.host("example.com/page"))
    }

    @Test
    fun theNetworkModeDecidesWhichSitesAreReached() = runBlocking {
        mode = NetworkMode.OFFLINE
        assertEquals(SiteRule.BLOCKED, sites.rule("example.com"))
        mode = NetworkMode.HUGGING_FACE
        assertEquals(SiteRule.BLOCKED, sites.rule("example.com"))
        mode = NetworkMode.GENERAL
        assertEquals(SiteRule.ALLOWED, sites.rule("example.com"))
        mode = NetworkMode.APPROVED_DOMAINS
        assertEquals(SiteRule.ASK, sites.rule("example.com"))
        sites.approve(listOf("example.com"))
        assertEquals(SiteRule.ALLOWED, sites.rule("example.com"))
        assertEquals(listOf("example.com"), sites.approved.first())
        sites.remove("example.com")
        assertEquals(SiteRule.ASK, sites.rule("example.com"))
        sites.approve(listOf("a.org", "b.org"))
        sites.clear()
        assertEquals(emptyList<String>(), sites.approved.first())
    }

    @Test
    fun blockedModesRefuseAndBadAddressesAreInvalid() {
        mode = NetworkMode.OFFLINE
        assertEquals(DenialCode.NETWORK_DISABLED, (decide("fetch_page", "https://example.com") as PolicyDecision.Denied).denial.code)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (decide("fetch_page", "file:///sdcard/x") as PolicyDecision.Denied).denial.code)
        val noSites = PolicyEngine(registry, states, ToolOutput(), permissionGranted = { true }, scope = { error("unused") })
        assertEquals(DenialCode.NETWORK_DISABLED, (runBlocking { noSites.decide("fetch_page", """{"url":"https://example.com"}""") } as PolicyDecision.Denied).denial.code)
    }

    @Test
    fun anyModeRunsAcceptedSkillsWithoutAsking() {
        mode = NetworkMode.GENERAL
        assertTrue(decide("fetch_page", "https://example.com") is PolicyDecision.Allowed)
        assertTrue("the skill's own Ask still asks", decide("careful_fetch", "https://example.com") is PolicyDecision.NeedsConfirmation)
    }

    @Test
    fun aNewSiteIsAskedAboutEvenForAnAcceptedSkillAndAllowedOnceOrAlways() = runBlocking {
        val asked = decide("fetch_page", "https://example.com/a") as PolicyDecision.NeedsConfirmation
        assertEquals(listOf("example.com"), asked.newSites)
        assertEquals(listOf(ResourceTarget("example.com", "site:example.com")), asked.targets)

        assertTrue(engine.confirm(asked) is PolicyDecision.Allowed)
        assertEquals("allowed once: not remembered", SiteRule.ASK, sites.rule("example.com"))

        val again = decide("fetch_page", "https://example.com/b") as PolicyDecision.NeedsConfirmation
        assertTrue(engine.confirm(again.copy(rememberSites = true)) is PolicyDecision.Allowed)
        assertEquals(listOf("example.com"), sites.approved.first())
        assertTrue(decide("fetch_page", "https://example.com/c") is PolicyDecision.Allowed)
    }

    @Test
    fun aRememberedAnswerIsKeptOnlyIfTheApprovalStillHolds() = runBlocking {
        val asked = decide("fetch_page", "https://example.com") as PolicyDecision.NeedsConfirmation
        mode = NetworkMode.OFFLINE

        assertTrue(engine.confirm(asked.copy(rememberSites = true)) is PolicyDecision.Denied)
        assertEquals(emptyList<String>(), sites.approved.first())
    }
}
