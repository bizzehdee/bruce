package com.bizzeh.bruce.skills.location

import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocationSkillsTest {
    private var enabled = true
    private var precise = false
    private var reading: Reading? = Reading(51.507351, -0.127758, 1500f)
    private var readings = 0
    private var last: Reading? = null
    private var onScreen = true

    private val skill = LocationSkills(object : LocationSource {
        override fun enabled() = enabled
        override fun precise() = precise
        override suspend fun current() = reading.also { readings++ }
        override fun lastKnown() = last
    }) { onScreen }.create().single()

    private fun run() = runBlocking { skill.execute(SkillArguments(emptyMap())) }

    @Test
    fun offByDefaultNeedingApproximateLocationOnly() {
        assertEquals("get_location", skill.id)
        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), skill.androidPermissions)
        assertEquals(listOf(android.Manifest.permission.ACCESS_FINE_LOCATION), skill.optionalPermissions)
    }

    @Test
    fun approximateReadingsAreRoundedToAboutAKilometre() {
        assertEquals(SkillOutcome.Done("Latitude 51.51, longitude -0.13 (to within about 2 km)."), run())
        reading = Reading(51.507351, -0.127758, 12f)
        assertEquals(SkillOutcome.Done("Latitude 51.51, longitude -0.13 (to within about 1 km)."), run())
        assertEquals(2, readings)
    }

    @Test
    fun preciseReadingsKeepTheirDetail() {
        precise = true
        reading = Reading(51.507351, -0.127758, 12.4f)
        assertEquals(SkillOutcome.Done("Latitude 51.50735, longitude -0.12776 (to within about 12 m)."), run())
        reading = Reading(1.0, 2.0, null)
        assertEquals(SkillOutcome.Done("Latitude 1.00000, longitude 2.00000."), run())
    }

    @Test
    fun aRecentLastKnownPositionStandsInWhenNoFreshOneComes() {
        reading = null
        last = Reading(51.507351, -0.127758, 800f, ageSeconds = 330)
        assertEquals(SkillOutcome.Done("Latitude 51.51, longitude -0.13 (to within about 1 km). As of about 5 minutes ago."), run())
        last = Reading(51.5, -0.1, 800f, ageSeconds = LocationSkills.MAX_AGE_SECONDS + 1)
        assertEquals(DenialCode.TOOL_FAILED, (run() as SkillOutcome.Failed).code)
    }

    @Test
    fun onlyWhileBruceIsOnScreen() {
        onScreen = false
        assertEquals(DenialCode.TOOL_UNAVAILABLE, (run() as SkillOutcome.Failed).code)
        assertEquals(0, readings)
    }

    @Test
    fun locationOffOrNoFixIsSaid() {
        reading = null
        assertEquals(DenialCode.TOOL_FAILED, (run() as SkillOutcome.Failed).code)
        enabled = false
        assertEquals(DenialCode.TOOL_UNAVAILABLE, (run() as SkillOutcome.Failed).code)
        assertEquals(1, readings)
    }
}
