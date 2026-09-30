package com.bizzeh.bruce.skills.contacts

import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContactSkillsTest {
    private val people = listOf(
        Contact("Ada Lovelace", listOf("+44 7700 900001 (Mobile)"), listOf("ada@example.com (Work)")),
        Contact("Adam Smith", emptyList(), listOf("adam@example.com")),
    )
    private val asked = mutableListOf<Pair<String, Int>>()
    private var found = people
    private val skill = ContactSkills({ name, limit -> asked += name to limit; found }, Dispatchers.Unconfined).create().single()

    private fun run(vararg arguments: Pair<String, Any>) = runBlocking { skill.execute(SkillArguments(mapOf(*arguments))) }

    @Test
    fun offByDefaultWithTheContactsPermission() {
        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(listOf(android.Manifest.permission.READ_CONTACTS), skill.androidPermissions)
    }

    @Test
    fun onlyTheDetailAskedForIsGiven() {
        assertEquals(SkillOutcome.Done("Ada Lovelace: +44 7700 900001 (Mobile)\nAdam Smith: no phone number"), run("name" to " Ad ", "detail" to "phone"))
        assertEquals(SkillOutcome.Done("Ada Lovelace: ada@example.com (Work)\nAdam Smith: adam@example.com"), run("name" to "Ad", "detail" to "email"))
        assertEquals(listOf("Ad" to 6, "Ad" to 6), asked)
    }

    @Test
    fun tooManyOrNoneOrTooShortAreSaid() {
        found = List(6) { Contact("Sam $it", listOf("1"), emptyList()) }
        assertEquals(true, (run("name" to "Sam", "detail" to "phone") as SkillOutcome.Done).content.endsWith("[More contacts match; give more of the name.]"))
        found = emptyList()
        assertEquals(SkillOutcome.Done("No contact matches that name."), run("name" to "Zed", "detail" to "phone"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, (run("name" to "Z", "detail" to "phone") as SkillOutcome.Failed).code)
    }
}
