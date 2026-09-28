package com.bizzeh.bruce.policy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Against a real in-memory Room database; Robolectric also provides Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class PolicyEngineTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private val states = SkillStateStore(database.policy()) { 1_000 }
    private val runs = mutableListOf<String>()

    private fun skill(
        id: String,
        default: SkillState,
        highRisk: Boolean = false,
        scope: ResourceScope = ResourceScope.NONE,
        permissions: List<String> = emptyList(),
        run: () -> SkillOutcome = { SkillOutcome.Done("result of $id") },
    ) = Skill(
        id = id,
        version = 1,
        description = "Test skill $id.",
        input = InputSchema(listOf(Parameter("path", ParameterType.STRING, "A path", required = false))),
        capabilities = setOf(Capability.FILE_READ),
        defaultState = default,
        highRisk = highRisk,
        scope = scope,
        androidPermissions = permissions,
    ) {
        runs += id
        run()
    }

    private val clock = skill("get_datetime", SkillState.ACCEPTED)
    private val reader = skill("read_file", SkillState.DECLINED, scope = ResourceScope.GRANTED_FILES)
    private val writer = skill("write_file", SkillState.ASK, scope = ResourceScope.GRANTED_FILES)
    private val deleter = skill("delete_file", SkillState.ASK, highRisk = true, scope = ResourceScope.GRANTED_FILES)
    private val contacts = skill("find_contact", SkillState.ACCEPTED, permissions = listOf("android.permission.READ_CONTACTS"))
    private val broken = skill("broken", SkillState.ACCEPTED) { error("secret path /sdcard/private.txt") }
    private val registry = SkillRegistry(listOf(clock, reader, writer, deleter, contacts, broken))

    private var granted = setOf<String>()
    private var inScope = true
    private var target = "content://docs/one"
    private var now = 10_000L

    private val engine = PolicyEngine(
        registry = registry,
        states = states,
        output = ToolOutput(reservedMarkers = listOf("<tool_call>")),
        permissionGranted = { it in granted },
        scope = { if (inScope) ScopeCheck.InScope(listOf(ResourceTarget("Documents/a.txt", target))) else ScopeCheck.OutOfScope("Outside the granted folders.") },
        clock = { now },
    )

    @After
    fun tearDown() = database.close()

    private fun decide(tool: String, arguments: String = "{}") = runBlocking { engine.decide(tool, arguments) }
    private fun denial(tool: String, arguments: String = "{}") = (decide(tool, arguments) as PolicyDecision.Denied).denial

    @Test
    fun freshInstallUsesEachSkillsDefault() = runBlocking {
        assertTrue(decide("get_datetime") is PolicyDecision.Allowed)
        assertEquals(DenialCode.CAPABILITY_DISABLED, denial("read_file").code)
        assertTrue(decide("write_file") is PolicyDecision.NeedsConfirmation)
        assertTrue(runs.isEmpty())
    }

    @Test
    fun toolAndArgumentsAreCheckedBeforeAnythingElse() {
        assertEquals(DenialCode.UNKNOWN_TOOL, denial("set_skill_state", """{"skill":"read_file","state":"ACCEPTED"}""").code)
        assertEquals(DenialCode.INVALID_ARGUMENTS, denial("read_file", """{"path":1}""").code)
    }

    @Test
    fun theUsersStateDecides() = runBlocking {
        states.set(reader, SkillState.ACCEPTED)
        states.set(clock, SkillState.DECLINED)
        states.set(writer, SkillState.ACCEPTED)

        assertTrue(decide("read_file") is PolicyDecision.Allowed)
        val declined = denial("get_datetime")
        assertEquals(DenialCode.CAPABILITY_DISABLED, declined.code)
        assertTrue(declined.userCanChange)
        assertFalse(declined.retryable)
        assertTrue(decide("write_file") is PolicyDecision.Allowed)
    }

    @Test
    fun missingAndroidPermissionIsDenied() {
        assertEquals(DenialCode.ANDROID_PERMISSION_DENIED, denial("find_contact").code)
        granted = setOf("android.permission.READ_CONTACTS")
        assertTrue(decide("find_contact") is PolicyDecision.Allowed)
    }

    @Test
    fun scopeIsCheckedInEveryStateIncludingAccepted() = runBlocking {
        states.set(reader, SkillState.ACCEPTED)
        inScope = false

        val accepted = denial("read_file", """{"path":"x"}""")
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, accepted.code)
        assertEquals("Outside the granted folders.", accepted.message)
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, denial("write_file").code)
        assertTrue("skills with no scope are unaffected", decide("get_datetime") is PolicyDecision.Allowed)
    }

    @Test
    fun askNeedsConfirmationBoundToThePolicyVersion() = runBlocking {
        val before = decide("write_file") as PolicyDecision.NeedsConfirmation
        states.set(clock, SkillState.ACCEPTED)
        val after = decide("write_file") as PolicyDecision.NeedsConfirmation

        assertEquals("write_file", before.request.skill.id)
        assertEquals(before.policyVersion + 1, after.policyVersion)
    }

    @Test
    fun anApprovalRunsExactlyWhatWasShown() = runBlocking {
        val pending = decide("write_file", """{"path":"Documents/a.txt"}""") as PolicyDecision.NeedsConfirmation
        assertEquals(listOf(ResourceTarget("Documents/a.txt", "content://docs/one")), pending.targets)
        assertEquals(10_000L, pending.askedAt)

        now += PolicyEngine.CONFIRMATION_LIFETIME_MS
        val allowed = engine.confirm(pending) as PolicyDecision.Allowed
        assertEquals(pending.request, allowed.request)
        engine.execute(allowed)
        assertEquals(listOf("write_file"), runs)
    }

    @Test
    fun anApprovalIsRefusedWhenAnythingChangedOrItExpired() = runBlocking {
        fun ask() = decide("write_file", """{"path":"Documents/a.txt"}""") as PolicyDecision.NeedsConfirmation
        fun refusal(decision: PolicyDecision) = (decision as PolicyDecision.Denied).denial

        val late = ask()
        now += PolicyEngine.CONFIRMATION_LIFETIME_MS + 1
        assertEquals(DenialCode.CONFIRMATION_REQUIRED, refusal(engine.confirm(late)).code)

        val retargeted = ask()
        target = "content://docs/two"
        assertEquals(DenialCode.CONFIRMATION_REQUIRED, refusal(engine.confirm(retargeted)).code)

        val versioned = ask()
        states.set(clock, SkillState.ASK)
        assertEquals(DenialCode.CONFIRMATION_REQUIRED, refusal(engine.confirm(versioned)).code)

        val turnedOff = ask()
        states.set(writer, SkillState.DECLINED)
        assertEquals(DenialCode.CAPABILITY_DISABLED, refusal(engine.confirm(turnedOff)).code)
        states.set(writer, SkillState.ASK)

        val nowAccepted = ask()
        states.set(writer, SkillState.ACCEPTED)
        assertEquals(DenialCode.CONFIRMATION_REQUIRED, refusal(engine.confirm(nowAccepted)).code)

        assertEquals(emptyList<String>(), runs)
    }

    @Test
    fun decliningTellsTheModelTheUserSaidNo() = runBlocking {
        val pending = decide("write_file", """{"path":"Documents/a.txt"}""") as PolicyDecision.NeedsConfirmation
        val denial = engine.declined(pending).denial

        assertEquals(DenialCode.USER_DENIED, denial.code)
        assertFalse(denial.retryable)
    }

    @Test
    fun highRiskSkillsNeedTheWarningAcceptedBeforeAccepted() = runBlocking {
        assertEquals(StateChange.WarningNotAccepted, states.set(deleter, SkillState.ACCEPTED))
        assertTrue(decide("delete_file") is PolicyDecision.NeedsConfirmation)

        assertEquals(StateChange.Changed, states.set(deleter, SkillState.ACCEPTED, highRiskWarningAccepted = true))
        assertTrue(decide("delete_file") is PolicyDecision.Allowed)
        assertEquals(StateChange.Changed, states.set(deleter, SkillState.DECLINED))
    }

    @Test
    fun executionReturnsSanitisedResultsAndHidesFailureDetails() = runBlocking {
        val result = engine.execute(decide("get_datetime") as PolicyDecision.Allowed)
        assertEquals("ok", result.getString("status"))
        assertEquals("result of get_datetime", result.getString("untrusted_data"))
        assertEquals(listOf("get_datetime"), runs)

        val failed = engine.execute(decide("broken") as PolicyDecision.Allowed)
        assertEquals("TOOL_FAILED", failed.getString("code"))
        assertFalse(failed.toString().contains("private.txt"))

        assertEquals("CAPABILITY_DISABLED", engine.refusal(decide("read_file") as PolicyDecision.Denied).getString("code"))
    }

    @Test
    fun storedStatesAreListedAndResetGoesBackToDefaults() = runBlocking {
        states.set(reader, SkillState.ASK)
        assertEquals(mapOf("read_file" to SkillState.ASK), states.stored.first())
        val version = states.policyVersion()

        states.reset()

        assertEquals(emptyMap<String, SkillState>(), states.stored.first())
        assertEquals(SkillState.DECLINED, states.state(reader))
        assertEquals(version + 1, states.policyVersion())
    }

    @Test
    fun resetOnAFreshInstallStartsTheVersionAtOne() = runBlocking {
        assertEquals(0L, states.policyVersion())
        states.reset()
        assertEquals(1L, states.policyVersion())
    }

    @Test
    fun anUnreadableStoredStateCountsAsDeclined() = runBlocking {
        database.policy().upsert(SkillStateEntity("get_datetime", "SOMETIMES", 0))

        assertEquals(SkillState.DECLINED, states.state(clock))
        assertEquals(DenialCode.CAPABILITY_DISABLED, denial("get_datetime").code)
    }
}
