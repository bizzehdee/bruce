package com.bizzeh.bruce.chat

import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.models.ActiveModelState
import com.bizzeh.bruce.policy.PolicyDecision
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.RuntimeEvent
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** The chat over a scripted runtime (the runtime itself is tested in BruceRuntimeTest). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val stats = GenerationStats(10, 1.seconds, 3, 1.seconds)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val activeModel = MutableStateFlow(ActiveModelState(active = File("Qwen3-0.6B-Q4_0.gguf")))

    /** Saved conversations by id, standing in for the Room store (tested in ConversationStoreTest). */
    private val saved = mutableMapOf<Long, List<ChatEntry>>()
    private val save: suspend (Long?, List<ChatEntry>) -> Long = { id, entries ->
        (id?.takeIf { it in saved } ?: (saved.size + 1L)).also { saved[it] = entries }
    }
    private val load: suspend (Long) -> List<ChatEntry>? = { saved[it] }

    /** What the runtime does on each turn, in order; and the history it was given. */
    private val turns = ArrayDeque<Flow<RuntimeEvent>>()
    private val histories = mutableListOf<List<ToolChatMessage>>()
    private val respond: (List<ToolChatMessage>) -> Flow<RuntimeEvent> = { history ->
        histories += history
        turns.removeFirstOrNull() ?: flowOf(RuntimeEvent.Finished(emptyList()))
    }

    private fun reply(text: String) = flowOf(RuntimeEvent.Text(text), RuntimeEvent.Step(text, "", emptyList(), stats), RuntimeEvent.Finished(emptyList()))

    private val noAnswer: suspend (ToolCall, PolicyDecision.NeedsConfirmation, Boolean) -> RuntimeEvent.ToolResult = { _, _, _ -> error("no approvals in these tests") }

    private fun TestScope.viewModel() = ChatViewModel(engine, activeModel, save, load, respond, noAnswer).also { advanceUntilIdle() }

    private fun TestScope.chat(text: String, vm: ChatViewModel) {
        vm.setInput(text)
        vm.send()
        advanceUntilIdle()
    }

    @Test
    fun titleFollowsTheActiveModel() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals("Qwen3-0.6B-Q4_0", vm.state.value.modelName)

        activeModel.value = ActiveModelState()
        advanceUntilIdle()

        assertNull(vm.state.value.modelName)
    }

    @Test
    fun theChatUsesTheChosenSidekicksNameAndKeepsItOnNewChats() = runTest(dispatcher) {
        val vm = ChatViewModel(engine, activeModel, save, load, respond, noAnswer, flowOf("Milo"))
        advanceUntilIdle()
        assertEquals("Milo", vm.state.value.sidekick)

        vm.newChat()

        assertEquals("Milo", vm.state.value.sidekick)
    }

    @Test
    fun aReplyStreamsThenSettlesAndIsSaved() = runTest(dispatcher) {
        turns += flow {
            emit(RuntimeEvent.Text("Hel"))
            emit(RuntimeEvent.Text("lo!"))
            emit(RuntimeEvent.Step("Hello!", "", emptyList(), stats))
            emit(RuntimeEvent.Finished(emptyList()))
        }
        val vm = viewModel()
        vm.setInput("  Hi Bruce  ")
        vm.send()
        assertTrue(vm.state.value.generating)
        assertEquals("", vm.state.value.input)
        advanceUntilIdle()

        val expected = listOf(ChatEntry(ChatRole.USER, "Hi Bruce"), ChatEntry(ChatRole.ASSISTANT, "Hello!", stats))
        assertEquals(expected, vm.state.value.entries)
        assertFalse(vm.state.value.generating)
        assertEquals(expected, saved.getValue(1))
        assertEquals(1L, vm.state.value.conversationId)
        assertEquals(listOf(ToolChatMessage(ChatRole.USER, "Hi Bruce")), histories.single())
    }

    @Test
    fun skillUsesAreShownSavedAndSentBackNextTurn() = runTest(dispatcher) {
        val call = ToolCall("get_datetime", "{}", "call_1")
        val refused = ToolCall("read_file", "{}", "call_2")
        turns += flowOf(
            RuntimeEvent.Step("", "", listOf(call), stats),
            RuntimeEvent.ToolResult(call, """{"status":"ok"}""", ran = true),
            RuntimeEvent.Step("", "", listOf(refused), stats),
            RuntimeEvent.ToolResult(refused, """{"status":"denied"}""", ran = false),
            RuntimeEvent.Text("<think>hmm</think>It is noon."),
            RuntimeEvent.Step("<think>hmm</think>It is noon.", "", emptyList(), stats),
            RuntimeEvent.Finished(emptyList()),
        )
        val vm = viewModel()

        chat("What time is it?", vm)
        chat("Thanks", vm)

        val entries = saved.getValue(1)
        assertEquals(
            listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT, ChatRole.USER),
            entries.map { it.role },
        )
        assertEquals(listOf(call), entries[1].toolCalls)
        assertEquals(ToolUse("call_1", "get_datetime", """{"status":"ok"}""", ToolStatus.RAN), entries[2].tool)
        assertEquals(ToolStatus.REFUSED, entries[4].tool!!.status)
        val sent = histories[1]
        assertEquals(listOf(call), sent[1].toolCalls)
        assertEquals("call_1", sent[2].toolCallId)
        assertEquals("get_datetime", sent[2].toolName)
        assertEquals("It is noon.", sent[5].content, "earlier reasoning is not sent back")
    }

    @Test
    fun failuresMapToChatErrorsAndKeepTheQuestion() = runTest(dispatcher) {
        mapOf(
            RuntimeError.NO_MODEL_LOADED to ChatError.NO_MODEL_LOADED,
            RuntimeError.CONVERSATION_TOO_LONG to ChatError.CONVERSATION_TOO_LONG,
            RuntimeError.GENERATION_FAILED to ChatError.GENERATION_FAILED,
            RuntimeError.TOO_MANY_TOOL_CALLS to ChatError.TOO_MANY_TOOL_CALLS,
            RuntimeError.TIMED_OUT to ChatError.TIMED_OUT,
        ).forEach { (runtimeError, chatError) ->
            turns += flowOf(RuntimeEvent.Failed(runtimeError, emptyList()))
            val vm = viewModel()
            chat("Hi", vm)
            assertEquals(chatError, vm.state.value.error)
            assertEquals(listOf(ChatEntry(ChatRole.USER, "Hi")), vm.state.value.entries)
            assertFalse(vm.state.value.generating)
        }
    }

    @Test
    fun theNextMessageClearsThePreviousError() = runTest(dispatcher) {
        turns += flowOf(RuntimeEvent.Failed(RuntimeError.GENERATION_FAILED, emptyList()))
        turns += reply("ok")
        val vm = viewModel()

        chat("one", vm)
        chat("two", vm)

        assertNull(vm.state.value.error)
    }

    @Test
    fun blankInputAndSendWhileGeneratingAreIgnored() = runTest(dispatcher) {
        turns += flow { awaitCancellation() }
        val vm = viewModel()

        chat("   ", vm)
        assertTrue(histories.isEmpty())
        chat("first", vm)
        chat("second", vm)

        assertEquals(1, histories.size)
    }

    @Test
    fun stopKeepsWhatWasSaidAndSavesIt() = runTest(dispatcher) {
        turns += flow {
            emit(RuntimeEvent.Text("Once upon"))
            awaitCancellation()
        }
        val vm = viewModel()
        chat("Story", vm)

        vm.stop()
        advanceUntilIdle()

        assertEquals(1, engine.stops)
        assertFalse(vm.state.value.generating)
        assertEquals(listOf("Story", "Once upon"), saved.getValue(1).map { it.text })
        vm.stop()
        assertEquals(1, engine.stops, "stop with nothing running does nothing")
    }

    @Test
    fun newChatDuringATurnEndsItAndSavesItToItsOwnChat() = runTest(dispatcher) {
        turns += flow {
            emit(RuntimeEvent.Text("Late"))
            awaitCancellation()
        }
        val vm = viewModel()
        chat("Hi", vm)

        vm.newChat()
        advanceUntilIdle()

        assertEquals(listOf("Hi", "Late"), saved.getValue(1).map { it.text })
        assertEquals(ChatState(modelName = "Qwen3-0.6B-Q4_0"), vm.state.value)
        assertEquals(1, engine.stops)
    }

    @Test
    fun openingASavedConversationShowsAndContinuesIt() = runTest(dispatcher) {
        saved[7] = listOf(ChatEntry(ChatRole.USER, "Earlier"), ChatEntry(ChatRole.ASSISTANT, "Reply"))
        turns += reply("Sure.")
        val vm = viewModel()

        vm.open(7)
        advanceUntilIdle()
        assertEquals(ChatState(modelName = "Qwen3-0.6B-Q4_0", conversationId = 7, entries = saved.getValue(7)), vm.state.value)
        chat("More", vm)

        assertEquals(listOf("Earlier", "Reply", "More", "Sure."), saved.getValue(7).map { it.text })
        vm.open(99)
        advanceUntilIdle()
        assertEquals(7L, vm.state.value.conversationId, "a missing conversation changes nothing")
    }

    @Test
    fun forgettingTheShownConversationStartsANewChat() = runTest(dispatcher) {
        saved[3] = listOf(ChatEntry(ChatRole.USER, "x"))
        val vm = viewModel()
        vm.open(3)
        advanceUntilIdle()

        vm.forget(setOf(4))
        assertEquals(3L, vm.state.value.conversationId)
        vm.forget(setOf(3, 4))
        assertNull(vm.state.value.conversationId)
    }

    @Test
    fun errorAndToolText() {
        assertNull(ChatText.error(null))
        assertEquals(R.string.chat_error_no_model, ChatText.error(ChatError.NO_MODEL_LOADED))
        assertEquals(R.string.chat_error_too_long, ChatText.error(ChatError.CONVERSATION_TOO_LONG))
        assertEquals(R.string.chat_error_failed, ChatText.error(ChatError.GENERATION_FAILED))
        assertEquals(R.string.chat_error_too_many_tools, ChatText.error(ChatError.TOO_MANY_TOOL_CALLS))
        assertEquals(R.string.chat_error_timed_out, ChatText.error(ChatError.TIMED_OUT))
        assertEquals(R.string.chat_tool_ran, ChatText.toolStatus(ToolStatus.RAN))
        assertEquals(R.string.chat_tool_refused, ChatText.toolStatus(ToolStatus.REFUSED))
        assertEquals(R.string.chat_tool_awaiting, ChatText.toolStatus(ToolStatus.AWAITING_APPROVAL))
    }
}
