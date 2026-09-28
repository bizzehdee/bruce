package com.bizzeh.bruce.chat

import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.models.ActiveModelState
import com.bizzeh.bruce.policy.PolicyDecision
import com.bizzeh.bruce.policy.ResourceTarget
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
import kotlinx.coroutines.flow.flow
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

    private val note = Skill("write_note", 1, "Write a note.", InputSchema(), emptySet(), SkillState.ASK) { SkillOutcome.Done("") }

    private fun pending(skill: Skill = note) = PolicyDecision.NeedsConfirmation(
        SkillRequest(skill, SkillArguments(emptyMap())),
        policyVersion = 3,
        targets = listOf(ResourceTarget("Notes/todo.txt", "content://notes/todo")),
        askedAt = 0,
    )

    /** First turn: the model asks to write; later turns: it answers with whatever it was given. */
    private inner class Script {
        val call = ToolCall("write_note", """{"path":"Notes/todo.txt","text":"milk"}""", "c1")
        val histories = mutableListOf<List<ToolChatMessage>>()
        val answers = mutableListOf<Boolean>()
        val saved = mutableListOf<List<ChatEntry>>()

        fun respond(history: List<ToolChatMessage>) = flow {
            histories += history
            if (histories.size == 1) {
                emit(RuntimeEvent.Step("", "", listOf(call), null))
                emit(RuntimeEvent.NeedsConfirmation(call, pending()))
            } else {
                emit(RuntimeEvent.Step("Done.", "", emptyList(), null))
            }
            emit(RuntimeEvent.Finished(emptyList()))
        }

        suspend fun answer(call: ToolCall, decision: PolicyDecision.NeedsConfirmation, approved: Boolean): RuntimeEvent.ToolResult {
            answers += approved
            return RuntimeEvent.ToolResult(call, if (approved) """{"status":"ok"}""" else """{"code":"USER_DENIED"}""", ran = approved)
        }

        val vm = ChatViewModel(
            FakeEngine(), MutableStateFlow(ActiveModelState()), { _, entries -> saved += entries; 1L }, { null }, ::respond, ::answer,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.ask(script: Script) {
        script.vm.setInput("Add milk")
        script.vm.send()
        advanceUntilIdle()
    }

    @Test
    fun theCardShowsExactlyWhatWouldRun() = runTest(dispatcher) {
        val script = Script()
        ask(script)

        val confirmation = script.vm.state.value.confirmations.getValue("c1")
        assertEquals("write_note", confirmation.skillId)
        assertEquals(listOf("Notes/todo.txt"), confirmation.targets)
        assertEquals(listOf("path" to "Notes/todo.txt", "text" to "milk"), confirmation.arguments)
    }

    @Test
    fun approvingRunsItAndTheModelCarriesOnWithTheResult() = runTest(dispatcher) {
        val script = Script()
        ask(script)

        script.vm.decide("c1", approved = true)
        advanceUntilIdle()

        assertEquals(listOf(true), script.answers)
        val tool = script.vm.state.value.entries.single { it.tool != null }
        assertEquals(ToolStatus.RAN, tool.tool!!.status)
        assertEquals(ChatRole.TOOL, script.histories.last().last().role)
        assertEquals("""{"status":"ok"}""", script.histories.last().last().content)
        assertEquals("Done.", script.vm.state.value.entries.last().text)
        assertEquals(emptyMap<String, Confirmation>(), script.vm.state.value.confirmations)

        script.vm.decide("c1", approved = true)
        advanceUntilIdle()
        assertEquals("a decision is used once", listOf(true), script.answers)
    }

    @Test
    fun decliningIsShownAndTheModelIsTold() = runTest(dispatcher) {
        val script = Script()
        ask(script)

        script.vm.decide("c1", approved = false)
        advanceUntilIdle()

        assertEquals(ToolStatus.DECLINED, script.vm.state.value.entries.single { it.tool != null }.tool!!.status)
        assertEquals("""{"code":"USER_DENIED"}""", script.histories.last().last().content)
    }

    @Test
    fun aNewChatDropsPendingApprovals() = runTest(dispatcher) {
        val script = Script()
        ask(script)

        script.vm.newChat()
        script.vm.decide("c1", approved = true)
        advanceUntilIdle()

        assertEquals(emptyList<Boolean>(), script.answers)
        assertEquals(emptyMap<String, Confirmation>(), script.vm.state.value.confirmations)
    }

    @Test
    fun aCallAwaitingApprovalIsShownAndTheModelIsToldSo() = runTest(dispatcher) {
        val call = ToolCall("write_note", "{}", "c1")
        val skill = Skill("write_note", 1, "Write a note.", InputSchema(), emptySet(), SkillState.ASK) { SkillOutcome.Done("") }
        val events = flowOf(RuntimeEvent.Step("", "", listOf(call), null), RuntimeEvent.NeedsConfirmation(call, pending(skill)), RuntimeEvent.Finished(emptyList()))
        val vm = ChatViewModel(FakeEngine(), MutableStateFlow(ActiveModelState()), { _, _ -> 1L }, { null }, { events }, { _, _, _ -> error("not answered") })

        vm.setInput("Note this")
        vm.send()
        advanceUntilIdle()

        val tool = vm.state.value.entries.last()
        assertEquals(ChatRole.TOOL, tool.role)
        assertEquals(ToolStatus.AWAITING_APPROVAL, tool.tool!!.status)
        assertEquals("CONFIRMATION_REQUIRED", JSONObject(tool.text).getString("code"))
    }
}
