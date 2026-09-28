package com.bizzeh.bruce.chat

import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.ChatMessage
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.StopReason
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import com.bizzeh.bruce.models.ActiveModelState
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine().apply { loadedModel = ModelInfo("qwen3 0.6B Q4_0", 1, 1, 40_960) }
    private val stats = GenerationStats(10, 1.seconds, 3, 1.seconds)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.chat(text: String, vm: ChatViewModel) {
        vm.setInput(text)
        vm.send()
        advanceUntilIdle()
    }

    private val activeModel = MutableStateFlow(ActiveModelState(active = File("Qwen3-0.6B-Q4_0.gguf")))

    private fun TestScope.viewModel() = ChatViewModel(engine, activeModel).also { advanceUntilIdle() }

    @Test
    fun titleFollowsTheActiveModel() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals("Qwen3-0.6B-Q4_0", vm.state.value.modelName)

        activeModel.value = ActiveModelState()
        advanceUntilIdle()

        assertNull(vm.state.value.modelName)
    }

    @Test
    fun sendStreamsTheReplyAndClearsTheComposer() = runTest(dispatcher) {
        engine.events = listOf(GenerationEvent.Token("Hel"), GenerationEvent.Token("lo!"), GenerationEvent.Completed(StopReason.END_OF_GENERATION, stats))
        val vm = viewModel()

        chat("  Hi Bruce  ", vm)

        val state = vm.state.value
        assertEquals(listOf(ChatEntry(ChatRole.USER, "Hi Bruce"), ChatEntry(ChatRole.ASSISTANT, "Hello!", stats)), state.entries)
        assertEquals("", state.input)
        assertFalse(state.generating)
        assertEquals("<formatted>", engine.requests.single().prompt)
    }

    @Test
    fun laterTurnsSendHistoryWithoutEarlierReasoning() = runTest(dispatcher) {
        val vm = viewModel()
        engine.events = listOf(GenerationEvent.Token("<think>greeting</think>\n\nHello!"), GenerationEvent.Completed(StopReason.END_OF_GENERATION, stats))
        chat("Hi", vm)
        engine.events = listOf(GenerationEvent.Token("Fine."), GenerationEvent.Completed(StopReason.END_OF_GENERATION, stats))

        chat("How are you?", vm)

        assertEquals(
            listOf(
                ChatMessage(ChatRole.USER, "Hi"),
                ChatMessage(ChatRole.ASSISTANT, "Hello!"),
                ChatMessage(ChatRole.USER, "How are you?"),
            ),
            engine.formatted.last(),
        )
        assertEquals(4, vm.state.value.entries.size)
    }

    @Test
    fun blankInputAndSendWhileGeneratingAreIgnored() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setInput("   ")
        vm.send()
        vm.setInput("one")
        vm.send()
        vm.setInput("two")
        vm.send()
        advanceUntilIdle()

        assertEquals(1, engine.formatted.size)
    }

    @Test
    fun noModelLoadedShowsAnErrorAndDropsTheEmptyReply() = runTest(dispatcher) {
        engine.prompt = null
        val vm = viewModel()

        chat("Hi", vm)

        assertEquals(ChatError.NO_MODEL_LOADED, vm.state.value.error)
        assertEquals(listOf(ChatEntry(ChatRole.USER, "Hi")), vm.state.value.entries)
        assertFalse(vm.state.value.generating)
    }

    @Test
    fun generationFailuresMapToChatErrors() = runTest(dispatcher) {
        val cases = mapOf(
            GenerationError.PROMPT_TOO_LONG to ChatError.CONVERSATION_TOO_LONG,
            GenerationError.DECODE_FAILED to ChatError.GENERATION_FAILED,
            GenerationError.NO_MODEL_LOADED to ChatError.NO_MODEL_LOADED,
        )
        for ((generationError, chatError) in cases) {
            val vm = viewModel()
            engine.events = listOf(GenerationEvent.Failed(generationError))
            chat("Hi", vm)
            assertEquals(chatError, vm.state.value.error)
        }
    }

    @Test
    fun failureAfterPartialReplyKeepsWhatWasSaid() = runTest(dispatcher) {
        val vm = viewModel()
        engine.events = listOf(GenerationEvent.Token("Partial"), GenerationEvent.Failed(GenerationError.DECODE_FAILED))

        chat("Hi", vm)

        assertEquals("Partial", vm.state.value.entries.last().text)
        assertEquals(ChatError.GENERATION_FAILED, vm.state.value.error)
    }

    @Test
    fun nextMessageClearsThePreviousError() = runTest(dispatcher) {
        engine.prompt = null
        val vm = viewModel()
        chat("Hi", vm)
        engine.prompt = com.bizzeh.bruce.inference.ChatPrompt("<p>", false)

        chat("Again", vm)

        assertNull(vm.state.value.error)
    }

    @Test
    fun stopAndNewChat() = runTest(dispatcher) {
        val vm = viewModel()
        chat("Hi", vm)

        vm.stop()
        vm.newChat()

        assertEquals(1, engine.stops)
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals("Qwen3-0.6B-Q4_0", vm.state.value.modelName)
    }

    @Test
    fun newChatWhileGeneratingStopsTheReply() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setInput("Hi")
        vm.send()

        vm.newChat()

        assertEquals(1, engine.stops)
    }

    @Test
    fun repliesUseTheActiveModelsTemperature() = runTest(dispatcher) {
        val vm = ChatViewModel(engine, activeModel) { 0.3f }.also { advanceUntilIdle() }

        chat("Hi", vm)

        assertEquals(0.3f, engine.requests.single().temperature)
    }

    @Test
    fun errorText() {
        assertNull(ChatText.error(null))
        assertEquals(R.string.chat_error_no_model, ChatText.error(ChatError.NO_MODEL_LOADED))
        assertEquals(R.string.chat_error_too_long, ChatText.error(ChatError.CONVERSATION_TOO_LONG))
        assertEquals(R.string.chat_error_failed, ChatText.error(ChatError.GENERATION_FAILED))
    }
}
