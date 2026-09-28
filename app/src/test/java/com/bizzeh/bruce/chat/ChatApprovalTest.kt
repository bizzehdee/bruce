package com.bizzeh.bruce.chat

import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.models.ActiveModelState
import com.bizzeh.bruce.policy.PolicyDecision
import com.bizzeh.bruce.runtime.RuntimeEvent
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequest
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A skill awaiting approval; Robolectric for Android's org.json in the refusal the model sees. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatApprovalTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aCallAwaitingApprovalIsShownAndTheModelIsToldSo() = runTest(dispatcher) {
        val call = ToolCall("write_note", "{}", "c1")
        val skill = Skill("write_note", 1, "Write a note.", InputSchema(), emptySet(), SkillState.ASK) { SkillOutcome.Done("") }
        val pending = PolicyDecision.NeedsConfirmation(SkillRequest(skill, SkillArguments(emptyMap())), policyVersion = 3)
        val events = flowOf(RuntimeEvent.Step("", "", listOf(call), null), RuntimeEvent.NeedsConfirmation(call, pending), RuntimeEvent.Finished(emptyList()))
        val vm = ChatViewModel(FakeEngine(), MutableStateFlow(ActiveModelState()), { _, _ -> 1L }, { null }, { events })

        vm.setInput("Note this")
        vm.send()
        advanceUntilIdle()

        val tool = vm.state.value.entries.last()
        assertEquals(ChatRole.TOOL, tool.role)
        assertEquals(ToolStatus.AWAITING_APPROVAL, tool.tool!!.status)
        assertEquals("CONFIRMATION_REQUIRED", JSONObject(tool.text).getString("code"))
    }
}
