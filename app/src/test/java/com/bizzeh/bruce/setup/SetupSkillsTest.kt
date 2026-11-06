package com.bizzeh.bruce.setup

import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.CapabilityClass
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for org.json, which the tool definitions are measured with. */
@RunWith(RobolectricTestRunner::class)
class SetupSkillsTest {
    private fun skill(id: String, capability: Capability, default: SkillState = SkillState.DECLINED, highRisk: Boolean = false) =
        Skill(id, 1, "does $id", InputSchema(), setOf(capability), default, highRisk) { SkillOutcome.Done("") }

    private val clock = skill("get_datetime", Capability.TIME, SkillState.ACCEPTED)
    private val battery = skill("get_battery_status", Capability.BATTERY)
    private val read = skill("read_file", Capability.FILE_READ)
    private val delete = skill("delete_file", Capability.FILE_DELETE, SkillState.ASK, highRisk = true)
    private val findSettings = Skill("find_settings", 1, "finds settings", InputSchema(), emptySet(), SkillState.DECLINED, false) { SkillOutcome.Done("") }

    @Test
    fun aSkillWithNoCapabilitiesGroupsAsInformation() {
        assertEquals(CapabilityClass.INFORMATION, SetupSkills.kind(findSettings))
        val groups = SetupSkills.groups(listOf(findSettings, read), mapOf("find_settings" to SkillState.DECLINED, "read_file" to SkillState.DECLINED))
        assertEquals(listOf(CapabilityClass.INFORMATION, CapabilityClass.FILES), groups.map { it.kind })
    }

    @Test
    fun skillsAreGroupedByKindAndAKindIsOnWhenAnyOfItIs() {
        val groups = SetupSkills.groups(listOf(read, clock, battery, delete), mapOf("get_datetime" to SkillState.ACCEPTED, "get_battery_status" to SkillState.DECLINED, "read_file" to SkillState.DECLINED, "delete_file" to SkillState.DECLINED))
        assertEquals(listOf(CapabilityClass.INFORMATION, CapabilityClass.FILES), groups.map { it.kind })
        assertEquals(listOf(true, false), groups.map { it.on })
        assertEquals(listOf("get_datetime", "get_battery_status"), groups.first().skills.map { it.id })
    }

    @Test
    fun aKindTurnedOnRunsPhoneStatusFreelyAndAsksForEverythingElse() {
        assertEquals(SkillState.ACCEPTED, SetupSkills.onState(battery))
        assertEquals(SkillState.ASK, SetupSkills.onState(read))
        assertEquals(SkillState.ASK, SetupSkills.onState(delete))
    }

    @Test
    fun moreSkillsMakeABiggerPromptAndAQuarterOfTheContextIsTheLimit() {
        val none = SetupSkills.promptTokens("You are Bruce.", emptyList())
        val some = SetupSkills.promptTokens("You are Bruce.", listOf(clock, battery))
        assertTrue(some > none)
        assertFalse(SetupSkills.crowded(1024, 4096))
        assertTrue(SetupSkills.crowded(1025, 4096))
    }
}
