package com.bizzeh.bruce.notifications

import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteSkillsTest {
    private val posted = mutableListOf<Pair<String, String>>()
    private var allowed = true

    private fun skill(sdk: Int = 34) = NoteSkills({ title, text -> allowed.also { if (it) posted += title to text } }, sdk).create().single()

    @Test
    fun aNoteIsPostedWhenAndroidAllows() = runBlocking {
        val note = skill()
        assertEquals("post_note", note.id)
        assertEquals(SkillState.DECLINED, note.defaultState)

        assertEquals(SkillOutcome.Done("Posted the note."), note.execute(SkillArguments(mapOf("title" to " Milk ", "text" to "Buy milk "))))
        assertEquals(listOf("Milk" to "Buy milk"), posted)
        allowed = false
        assertEquals(DenialCode.ANDROID_PERMISSION_DENIED, (note.execute(SkillArguments(mapOf("title" to "a", "text" to "b"))) as SkillOutcome.Failed).code)
    }

    @Test
    fun theNotificationPermissionIsNeededFromAndroid13() {
        assertEquals(listOf(android.Manifest.permission.POST_NOTIFICATIONS), skill(33).androidPermissions)
        assertEquals(emptyList<String>(), skill(32).androidPermissions)
    }
}
