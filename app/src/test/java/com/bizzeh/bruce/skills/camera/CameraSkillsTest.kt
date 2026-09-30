package com.bizzeh.bruce.skills.camera

import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class CameraSkillsTest {
    private val photos = PhotoRequests(wait = 5.seconds)
    private var onScreen = true
    private val skill = CameraSkills(photos) { onScreen }.create().single()

    @Test
    fun offByDefault() {
        assertEquals("take_photo", skill.id)
        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(emptyList<String>(), skill.androidPermissions)
    }

    @Test
    fun thePhotoTheUserTakesGoesWithTheirNextMessage() = runTest {
        val outcome = async { skill.execute(SkillArguments(emptyMap())) }
        photos.pending.filterNotNull().first()
        photos.complete("a.jpg")

        assertEquals(SkillOutcome.Done("The user took a photo. It is attached to their next message; ask them to send it with what they want to know."), outcome.await())
        assertNull(photos.pending.value)
    }

    @Test
    fun closingTheCameraOrWaitingTooLongGivesNoPhoto() = runTest {
        val closed = async { skill.execute(SkillArguments(emptyMap())) }
        photos.pending.filterNotNull().first()
        photos.complete(null)
        assertEquals(SkillOutcome.Done("The user closed the camera without taking a photo."), closed.await())

        assertEquals(SkillOutcome.Done("The user closed the camera without taking a photo."), skill.execute(SkillArguments(emptyMap())))
        assertNull(photos.pending.value)
    }

    @Test
    fun onlyWhileBruceIsOnScreen() = runTest {
        onScreen = false
        assertEquals(DenialCode.TOOL_UNAVAILABLE, (skill.execute(SkillArguments(emptyMap())) as SkillOutcome.Failed).code)
        assertNull(photos.pending.value)
        photos.complete("ignored.jpg")
    }
}
