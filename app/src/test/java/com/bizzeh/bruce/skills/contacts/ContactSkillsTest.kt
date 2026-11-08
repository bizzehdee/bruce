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
    private val counted = mutableListOf<String?>()
    private var found = people
    private var total = 0
    private val source = object : ContactsSource {
        override fun find(name: String, limit: Int): List<Contact> {
            asked += name to limit
            return found
        }
        override fun count(name: String?): Int {
            counted += name
            return total
        }
    }
    private val skills = ContactSkills(source, Dispatchers.Unconfined).create()
    private val findSkill = skills[0]
    private val countSkill = skills[1]

    private fun run(vararg arguments: Pair<String, Any>) = runBlocking { findSkill.execute(SkillArguments(mapOf(*arguments))) }

    private fun runCount(vararg arguments: Pair<String, Any>) = runBlocking { countSkill.execute(SkillArguments(mapOf(*arguments))) }

    @Test
    fun offByDefaultWithTheContactsPermission() {
        assertEquals(SkillState.DECLINED, findSkill.defaultState)
        assertEquals(listOf(android.Manifest.permission.READ_CONTACTS), findSkill.androidPermissions)
        assertEquals(SkillState.DECLINED, countSkill.defaultState)
        assertEquals(listOf(android.Manifest.permission.READ_CONTACTS), countSkill.androidPermissions)
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

    @Test
    fun countsEveryContactWhenNoNameIsGiven() {
        total = 42
        assertEquals(SkillOutcome.Done("42 contacts in total."), runCount())
        assertEquals(listOf<String?>(null), counted)
    }

    @Test
    fun countsOnlyTheMatchesWhenANameIsGiven() {
        total = 1
        assertEquals(SkillOutcome.Done("1 contact matches \"Ada\"."), runCount("name" to " Ada "))
        assertEquals(listOf("Ada"), counted)
    }

    @Test
    fun aTooShortNameToCountIsSaid() {
        assertEquals(DenialCode.INVALID_ARGUMENTS, (runCount("name" to "A") as SkillOutcome.Failed).code)
        assertEquals(emptyList<String?>(), counted)
    }
}
