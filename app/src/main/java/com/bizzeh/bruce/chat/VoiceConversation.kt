package com.bizzeh.bruce.chat

import com.bizzeh.bruce.inference.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ConversationPhase { LISTENING, WAITING, SPEAKING }

/**
 * Hands-free chat (TASK-081): listen, send what was said, read the reply aloud, listen again.
 * Silence for [SILENCE_END_MILLIS] ends it, as do an error, [stop] and leaving the screen. A skill
 * awaiting approval keeps it waiting: approval is only ever a tap, never a spoken word.
 *
 * [send] puts the words in the message box and sends them. Recogniser calls run on [scope],
 * which must be the main thread's, as SpeechRecognizer requires.
 */
class VoiceConversation(
    private val recogniser: VoiceRecogniser,
    private val readAloud: ReadAloud,
    private val chat: StateFlow<ChatState>,
    private val send: (String) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val _phase = MutableStateFlow<ConversationPhase?>(null)
    val phase: StateFlow<ConversationPhase?> = _phase.asStateFlow()

    private var waiting: Job? = null
    private var silentSince = 0L

    fun start() {
        if (_phase.value != null) return
        silentSince = clock()
        listen()
    }

    fun stop() {
        if (_phase.value == null) return
        _phase.value = null
        waiting?.cancel()
        recogniser.stop()
        readAloud.stop()
    }

    private fun listen() {
        _phase.value = ConversationPhase.LISTENING
        recogniser.start(PAUSE_MILLIS) { text ->
            if (_phase.value != ConversationPhase.LISTENING) return@start
            val words = text?.trim().orEmpty()
            when {
                words.isNotEmpty() -> ask(words)
                clock() - silentSince < SILENCE_END_MILLIS -> listen()
                else -> stop()
            }
        }
    }

    private fun ask(words: String) {
        _phase.value = ConversationPhase.WAITING
        val sentAfter = chat.value.entries.size
        send(words)
        waiting = scope.launch {
            val done = chat.first { it.error != null || (!it.generating && answer(it, sentAfter) != null) }
            if (_phase.value != ConversationPhase.WAITING) return@launch
            val index = answer(done, sentAfter)
            if (done.error != null || index == null) return@launch stop()
            _phase.value = ConversationPhase.SPEAKING
            readAloud.read(index, done.entries[index].text) {
                scope.launch {
                    if (_phase.value != ConversationPhase.SPEAKING) return@launch
                    silentSince = clock()
                    listen()
                }
            }
        }
    }

    /** The reply to the words sent: the last entry, when it is the sidekick's answer. */
    private fun answer(state: ChatState, sentAfter: Int): Int? {
        val last = state.entries.lastIndex
        if (last <= sentAfter) return null
        val entry = state.entries[last]
        return last.takeIf { entry.role == ChatRole.ASSISTANT && entry.toolCalls.isEmpty() && ThinkingText.split(entry.text).answer.isNotBlank() }
    }

    companion object {
        /** A pause this long ends what the user says (owner, 2026-09-30). */
        const val PAUSE_MILLIS = 2_000L

        /** Listening this long without hearing anything ends the conversation (owner, 2026-09-30). */
        const val SILENCE_END_MILLIS = 10_000L
    }
}
