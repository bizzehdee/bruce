package com.bizzeh.bruce.litert

import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.runtime.BackendChat
import com.bizzeh.bruce.runtime.Prepared
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.StepEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiteRtBackendTest {
    /** [gate], when set, is awaited after the given [events] before the flow completes, so a test can hold a turn open until [cancel] releases it; [reachedGate] completes just before that wait starts. */
    private class FakeConversation(private val events: List<LiteRtEvent>, private val gate: Channel<Unit>? = null) : LiteRtConversation {
        var cancelled = false
        var closed = false
        var tokenCountReturned = 100
        val reachedGate = kotlinx.coroutines.CompletableDeferred<Unit>()

        override fun send(): Flow<LiteRtEvent> = flow {
            events.forEach { emit(it) }
            if (gate != null) {
                reachedGate.complete(Unit)
                gate.receive()
            }
        }

        override fun cancel() {
            cancelled = true
            gate?.trySend(Unit)
        }

        override fun tokenCount(): Int = tokenCountReturned

        override fun close() {
            closed = true
        }
    }

    private class FakeModel(override val maxContextTokens: Int = 4096) : LiteRtModel {
        var conversation = FakeConversation(listOf(LiteRtEvent.Done("", "", emptyList())))
        var lastSystemInstruction: String? = null
        var lastPriorMessages: List<ToolChatMessage>? = null
        var lastLatest: ToolChatMessage? = null
        var lastTools: List<ToolDefinition>? = null
        var lastThinking: Boolean? = null
        var closed = false

        override fun converse(
            systemInstruction: String,
            priorMessages: List<ToolChatMessage>,
            latest: ToolChatMessage,
            tools: List<ToolDefinition>,
            thinking: Boolean,
            maxTokens: Int,
            temperature: Float,
            topK: Int,
            topP: Float,
        ): LiteRtConversation {
            lastSystemInstruction = systemInstruction
            lastPriorMessages = priorMessages
            lastLatest = latest
            lastTools = tools
            lastThinking = thinking
            return conversation
        }

        override fun close() {
            closed = true
        }
    }

    private val model = FakeModel()
    private val backend = LiteRtBackend(model, Dispatchers.Unconfined)
    private val clock = ToolDefinition("get_datetime", "Get the date and time", """{"type":"object","properties":{}}""")
    private val chat = BackendChat(
        "You are Bruce.",
        listOf(
            ToolChatMessage(ChatRole.USER, "Hi"),
            ToolChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("get_datetime", "{}", "c1"))),
            ToolChatMessage(ChatRole.TOOL, "{\"status\":\"ok\"}", toolCallId = "c1", toolName = "get_datetime"),
            ToolChatMessage(ChatRole.USER, "What time is it?"),
        ),
        listOf(clock),
    )

    private fun run(prepared: Prepared.Ready = runBlocking { backend.prepare(chat) as Prepared.Ready }) =
        runBlocking { backend.generate(prepared, 200, 0.3f).toList() }

    @Test
    fun contextLengthComesFromTheModel() = runBlocking {
        assertEquals(4096, backend.contextLength())
    }

    @Test
    fun prepareEstimatesTokensFromCharacters() = runBlocking {
        val prepared = backend.prepare(chat) as Prepared.Ready
        assertTrue(prepared.tokens > 0, "estimate should be positive, was ${prepared.tokens}")
    }

    @Test
    fun generateSendsTheLatestMessageWithTheRestAsHistory() {
        run()

        assertEquals("You are Bruce.", model.lastSystemInstruction)
        assertEquals(listOf(clock), model.lastTools)
        assertEquals(false, model.lastThinking)
        assertEquals(chat.messages.dropLast(1), model.lastPriorMessages)
        assertEquals(chat.messages.last(), model.lastLatest)
    }

    @Test
    fun generateThinkingFollowsTheChat() {
        run(runBlocking { backend.prepare(chat.copy(thinking = true)) as Prepared.Ready })
        assertEquals(true, model.lastThinking)
    }

    @Test
    fun textAndReasoningStreamAsThinkingTags() {
        model.conversation = FakeConversation(
            listOf(
                LiteRtEvent.Reasoning("The user wants the time."),
                LiteRtEvent.Text("It is noon."),
                LiteRtEvent.Done("It is noon.", "The user wants the time.", emptyList()),
            ),
        )
        val events = run()
        val text = events.filterIsInstance<StepEvent.Text>().joinToString("") { it.text }
        assertEquals("<think>The user wants the time.</think>It is noon.", text)
        val done = events.last() as StepEvent.Done
        assertEquals("It is noon.", done.reply.content)
        assertEquals("The user wants the time.", done.reply.reasoning)
    }

    @Test
    fun toolCallsComeBackStructuredWithNoIdForTheRuntimeToNumber() {
        model.conversation = FakeConversation(listOf(LiteRtEvent.Done("", "", listOf(ToolCall("get_datetime", "{}", id = "")))))
        val done = run().single() as StepEvent.Done
        assertEquals(1, done.reply.toolCalls.size)
        assertEquals("get_datetime", done.reply.toolCalls.single().name)
        assertEquals("", done.reply.toolCalls.single().id)
    }

    @Test
    fun aFailedEventFailsTheTurn() {
        model.conversation = FakeConversation(listOf(LiteRtEvent.Failed))
        assertEquals(StepEvent.Failed(RuntimeError.GENERATION_FAILED), run().single())
    }

    @Test
    fun noMessagesFailsBeforeCallingTheModel() {
        val empty = BackendChat("You are Bruce.", emptyList(), emptyList())
        val prepared = runBlocking { backend.prepare(empty) as Prepared.Ready }
        assertEquals(StepEvent.Failed(RuntimeError.GENERATION_FAILED), run(prepared).single())
    }

    @Test
    fun stopCancelsTheOpenConversation() = runBlocking {
        val hanging = FakeConversation(listOf(LiteRtEvent.Text("partial")), gate = Channel())
        model.conversation = hanging
        val prepared = backend.prepare(chat) as Prepared.Ready
        val job = launch(Dispatchers.Default) { backend.generate(prepared, 200, 0.3f).toList() }
        withTimeout(1000) { hanging.reachedGate.await() }
        backend.stop()
        job.join()
        assertTrue(hanging.cancelled)
    }

    @Test
    fun theConversationIsAlwaysClosedAfterATurn() {
        run()
        assertTrue(model.conversation.closed)
    }

    @Test
    fun theTokenEstimateIsCorrectedFromTheConversationsOwnCount() = runBlocking {
        model.conversation = FakeConversation(listOf(LiteRtEvent.Done("reply", "", emptyList()))).apply { tokenCountReturned = 500 }
        val before = (backend.prepare(chat) as Prepared.Ready).tokens
        run()
        val after = (backend.prepare(chat) as Prepared.Ready).tokens
        assertTrue(after != before, "before $before, after $after")
    }
}
