package com.bizzeh.bruce.skills.apps

import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppSkillsTest {
    private class FakeActions : PhoneActions {
        val done = mutableListOf<String>()
        var works = true
        val apps = listOf(LaunchableApp("Camera", "cam"), LaunchableApp("Google Maps", "maps"), LaunchableApp("Messages", "sms"), LaunchableApp("Messages Pro", "sms2"))
        override fun share(text: String, subject: String?) = works.also { done += "share $text $subject" }
        override fun openUrl(url: String) = works.also { done += "url $url" }
        override fun launchable() = apps
        override fun launch(packageName: String) = works.also { done += "launch $packageName" }
        override fun copy(text: String) { done += "copy $text" }
        override fun compose(number: String?, text: String) = works.also { done += "sms $number $text" }
        override fun dial(number: String) = works.also { done += "dial $number" }
    }

    private val actions = FakeActions()
    private var onScreen = true
    private val skills = AppSkills(actions) { onScreen }.create().associateBy { it.id }

    private fun run(id: String, vararg arguments: Pair<String, Any>) = runBlocking { skills.getValue(id).execute(SkillArguments(mapOf(*arguments))) }

    private fun code(outcome: SkillOutcome) = (outcome as SkillOutcome.Failed).code

    @Test
    fun allAreOffByDefaultAndPagesFollowTheSiteRules() {
        assertEquals(listOf("share_text", "open_url", "open_app", "copy_to_clipboard", "write_text_message", "dial_number"), skills.keys.toList())
        assertTrue(skills.values.all { it.defaultState == SkillState.DECLINED })
        assertEquals(ResourceScope.WEB, skills.getValue("open_url").scope)
        assertEquals("example.com", skills.getValue("open_url").site!!(SkillArguments(mapOf("url" to "https://Example.com/a"))))
    }

    @Test
    fun copyingWorksEvenOffScreen() {
        onScreen = false
        assertEquals(SkillOutcome.Done("Copied to the clipboard."), run("copy_to_clipboard", "text" to "4417"))
        assertEquals(listOf("copy 4417"), actions.done)
    }

    @Test
    fun messagesAndCallsAreOnlyPreparedForTheUser() {
        assertEquals(SkillOutcome.Done("The message is written in the messaging app; the user sends it."), run("write_text_message", "text" to "On my way", "number" to " +44 7700 900123 "))
        assertTrue(run("write_text_message", "text" to "Hi") is SkillOutcome.Done)
        assertEquals(SkillOutcome.Done("The number is ready in the phone app; the user places the call."), run("dial_number", "number" to "(020) 7946-0018"))
        assertEquals(listOf("sms +44 7700 900123 On my way", "sms null Hi", "dial (020) 7946-0018"), actions.done)

        assertEquals(DenialCode.INVALID_ARGUMENTS, code(run("dial_number", "number" to "*#06#")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code(run("dial_number", "number" to "call mum")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code(run("write_text_message", "text" to "x", "number" to "12")))
        actions.works = false
        assertEquals(DenialCode.TOOL_UNAVAILABLE, code(run("dial_number", "number" to "999")))
        assertEquals(DenialCode.TOOL_UNAVAILABLE, code(run("write_text_message", "text" to "x")))
    }

    @Test
    fun sharingOpensTheSheet() {
        assertEquals(SkillOutcome.Done("The share sheet is open; the user chooses where it goes."), run("share_text", "text" to "Hello", "subject" to "Hi"))
        assertEquals(listOf("share Hello Hi"), actions.done)
    }

    @Test
    fun onlyWebAddressesOpen() {
        assertTrue(run("open_url", "url" to " https://example.com/page ") is SkillOutcome.Done)
        assertEquals(listOf("url https://example.com/page"), actions.done)
        assertEquals(DenialCode.INVALID_ARGUMENTS, code(run("open_url", "url" to "intent://evil#Intent;end")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code(run("open_url", "url" to "file:///sdcard/x")))
    }

    @Test
    fun appsOpenByAnExactOrUniqueName() {
        assertEquals(SkillOutcome.Done("Opened Camera."), run("open_app", "name" to "camera"))
        assertEquals(SkillOutcome.Done("Opened Google Maps."), run("open_app", "name" to "maps"))
        assertEquals(SkillOutcome.Done("Opened Messages."), run("open_app", "name" to "Messages"))
        val ambiguous = run("open_app", "name" to "mess") as SkillOutcome.Failed
        assertEquals("More than one app matches: Messages, Messages Pro. Use the full name.", ambiguous.message)
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, code(run("open_app", "name" to "Nothing")))
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, code(run("open_app", "name" to " ")))
        assertEquals(listOf("launch cam", "launch maps", "launch sms"), actions.done)
    }

    @Test
    fun nothingOpensWhileBruceIsNotOnScreenOrNothingCanTakeIt() {
        onScreen = false
        assertEquals(DenialCode.TOOL_UNAVAILABLE, code(run("share_text", "text" to "x")))
        assertTrue(actions.done.isEmpty())
        onScreen = true
        actions.works = false
        assertEquals(DenialCode.TOOL_UNAVAILABLE, code(run("share_text", "text" to "x")))
        assertEquals(DenialCode.TOOL_UNAVAILABLE, code(run("open_url", "url" to "https://example.com")))
        assertEquals(DenialCode.TOOL_UNAVAILABLE, code(run("open_app", "name" to "Camera")))
    }
}
