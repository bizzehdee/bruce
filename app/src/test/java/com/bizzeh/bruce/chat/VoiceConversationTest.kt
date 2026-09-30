package com.bizzeh.bruce.chat

import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceConversationTest {
    private class FakeRecogniser : VoiceRecogniser {
        override val available = true
        var heard: ((String?) -> Unit)? = null
        var pause: Long? = null
        var starts = 0
        var stops = 0

        override fun start(pauseMillis: Long?, onDone: (String?) -> Unit) {
            starts++
            pause = pauseMillis
            heard = onDone
        }

        override fun stop() {
            stops++
        }

        fun say(text: String?) = heard!!.also { heard = null }(text)
    }

    private class FakeSpeaker : ReplySpeaker {
        val spoken = mutableListOf<List<String>>()
        var done: (() -> Unit)? = null

        override fun prepare(onReady: (Boolean) -> Unit) = onReady(true)

        override fun speak(parts: List<String>, onDone: () -> Unit): Boolean {
            spoken += parts
            done = onDone
            return false
        }

        override fun stop() {
            done?.also { done = null }?.invoke()
        }

        override fun release() = Unit

        fun finish() = done!!.also { done = null }()
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val recogniser = FakeRecogniser()
    private val speaker = FakeSpeaker()
    private val readAloud = ReadAloud(speaker).apply { prepare() }
    private val chat = MutableStateFlow(ChatState(entries = listOf(ChatEntry(ChatRole.USER, "earlier"), ChatEntry(ChatRole.ASSISTANT, "Earlier reply."))))
    private val sent = mutableListOf<String>()
    private var now = 0L
    private val conversation = VoiceConversation(recogniser, readAloud, chat, { words ->
        sent += words
        chat.update { it.copy(entries = it.entries + ChatEntry(ChatRole.USER, words), generating = true) }
    }, scope, { now })

    @After
    fun tearDown() = scope.cancel()

    private fun reply(vararg entries: ChatEntry) = chat.update { it.copy(entries = it.entries + entries, generating = false) }

    @Test
    fun listensSendsReadsTheReplyAndListensAgain() {
        conversation.start()
        assertEquals(ConversationPhase.LISTENING, conversation.phase.value)
        assertEquals(VoiceConversation.PAUSE_MILLIS, recogniser.pause)

        recogniser.say(" what time is it ")
        assertEquals(listOf("what time is it"), sent)
        assertEquals(ConversationPhase.WAITING, conversation.phase.value)

        reply(ChatEntry(ChatRole.ASSISTANT, "<think>x</think>It's ten."))
        assertEquals(ConversationPhase.SPEAKING, conversation.phase.value)
        assertEquals(listOf(listOf("It's ten.")), speaker.spoken)
        assertEquals(3, readAloud.state.value.reading)

        speaker.finish()
        assertEquals(ConversationPhase.LISTENING, conversation.phase.value)
        assertEquals(2, recogniser.starts)
    }

    @Test
    fun skillsAndApprovalsAreWaitedThrough() {
        conversation.start()
        recogniser.say("delete it")
        val asked = ChatEntry(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("delete_file", "{}", "c1")))
        val card = ChatEntry(ChatRole.TOOL, "", tool = ToolUse("c1", "delete_file", "", ToolStatus.AWAITING_APPROVAL))

        reply(asked, card)
        assertEquals("never approved by voice", ConversationPhase.WAITING, conversation.phase.value)
        assertEquals(1, recogniser.starts)

        reply(ChatEntry(ChatRole.ASSISTANT, "Deleted."))
        assertEquals(ConversationPhase.SPEAKING, conversation.phase.value)
    }

    @Test
    fun silenceEndsItAfterTenSeconds() {
        conversation.start()
        now = 9_999
        recogniser.say(null)
        assertEquals("heard nothing yet: keep listening", ConversationPhase.LISTENING, conversation.phase.value)
        now = 10_000
        recogniser.say("")

        assertNull(conversation.phase.value)
        assertEquals(1, recogniser.stops)
    }

    @Test
    fun anErrorEndsIt() {
        conversation.start()
        recogniser.say("hello")
        chat.update { it.copy(generating = false, error = ChatError.NO_MODEL_LOADED) }

        assertNull(conversation.phase.value)
    }

    @Test
    fun stoppingEndsEveryPhaseQuietly() {
        conversation.start()
        conversation.start()
        assertEquals("already running", 1, recogniser.starts)
        conversation.stop()
        assertNull(conversation.phase.value)
        conversation.stop()

        conversation.start()
        recogniser.say("hello")
        reply(ChatEntry(ChatRole.ASSISTANT, "Hi."))
        conversation.stop()
        assertNull(conversation.phase.value)
        assertEquals("stopping the reading does not listen again", 2, recogniser.starts)

        conversation.start()
        recogniser.say("hello")
        conversation.stop()
        reply(ChatEntry(ChatRole.ASSISTANT, "Too late."))
        assertEquals(listOf(listOf("Hi.")), speaker.spoken)
    }

    @Test
    fun lateRecognitionAfterStopIsIgnored() {
        conversation.start()
        val late = recogniser.heard!!
        conversation.stop()
        late("hello")

        assertEquals(emptyList<String>(), sent)
    }
}
