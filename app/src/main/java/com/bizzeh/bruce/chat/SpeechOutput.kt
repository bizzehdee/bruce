package com.bizzeh.bruce.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** Reading replies aloud (TASK-080). [reading] is the chat entry being read; null when silent. */
data class SpeechState(
    val available: Boolean = false,
    val reading: Int? = null,
    /** The voice in use sends text to its engine's servers; the chat says so while reading. */
    val needsNetwork: Boolean = false,
)

/** Text to speech; AndroidReplySpeaker on a phone. */
interface ReplySpeaker {
    /** [onReady] hears whether the phone has an engine that can speak; called once, on the main thread. */
    fun prepare(onReady: (Boolean) -> Unit)

    /** Reads [parts] in order; [onDone] when the last ends or reading stops. Returns whether the voice needs the network. */
    fun speak(parts: List<String>, onDone: () -> Unit): Boolean
    fun stop()
    fun release()
}

/** Which reply is being read, over whichever [ReplySpeaker] the phone has. */
class ReadAloud(private val speaker: ReplySpeaker) {
    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    fun prepare() = speaker.prepare { ok -> _state.update { it.copy(available = ok) } }

    /** Reads entry [index]'s reply; [onDone] when it ends, is stopped or has nothing to say. */
    fun read(index: Int, raw: String, onDone: () -> Unit = {}) {
        speaker.stop()
        val parts = SpokenText.parts(SpokenText.of(raw), MAX_PART)
        if (!_state.value.available || parts.isEmpty()) return onDone()
        _state.update { it.copy(reading = index, needsNetwork = false) }
        val network = speaker.speak(parts) {
            _state.update { it.copy(reading = null, needsNetwork = false) }
            onDone()
        }
        _state.update { if (it.reading == index) it.copy(needsNetwork = network) else it }
    }

    fun stop() = speaker.stop()

    fun release() {
        speaker.release()
        _state.value = SpeechState()
    }

    companion object {
        /** Parts stay under the engine's input limit (TextToSpeech.getMaxSpeechInputLength, 4,000). */
        const val MAX_PART = 3_900
    }
}

object SpokenText {
    /**
     * What a reply sounds like: its Markdown as plain sentences. Code blocks are skipped (read
     * aloud they are noise) and images give their description; reasoning is never read.
     */
    fun of(raw: String): String = blocks(Markdown.parse(ThinkingText.split(raw).answer)).joinToString("\n").trim()

    /** Pieces no longer than [max] characters, split at paragraphs, then sentences, then words. */
    fun parts(text: String, max: Int): List<String> {
        require(max > 0)
        return text.split('\n').map(String::trim).filter(String::isNotEmpty).flatMap { split(it, max) }
    }

    private fun split(paragraph: String, max: Int): List<String> {
        if (paragraph.length <= max) return listOf(paragraph)
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (piece in Regex("(?<=[.!?])\\s+|\\s+").split(paragraph)) {
            if (current.isNotEmpty() && current.length + 1 + piece.length > max) {
                result += current.toString()
                current = StringBuilder()
            }
            if (piece.length > max) {
                piece.chunked(max).forEach { result += it }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun blocks(blocks: List<MdBlock>): List<String> = blocks.flatMap { block ->
        when (block) {
            is MdBlock.Heading -> listOf(inline(block.content))
            is MdBlock.Paragraph -> listOf(inline(block.content))
            is MdBlock.CodeBlock, MdBlock.Rule -> emptyList()
            is MdBlock.ListBlock -> block.items.flatMap(::blocks)
            is MdBlock.Quote -> blocks(block.blocks)
            is MdBlock.Table -> (listOf(block.header) + block.rows).map { row -> row.joinToString(", ") { inline(it) } }
            is MdBlock.Image -> listOf(block.alt).filter(String::isNotBlank)
        }
    }

    private fun inline(content: List<MdInline>): String = content.joinToString("") { part ->
        when (part) {
            is MdInline.Plain -> part.text
            is MdInline.Code -> part.text
            is MdInline.Emphasis -> inline(part.children)
            is MdInline.Strong -> inline(part.children)
            is MdInline.Link -> inline(part.children)
            MdInline.LineBreak -> " "
        }
    }
}

/**
 * Android's text to speech with whatever engine the phone has (owner, 2026-09-30). An offline
 * voice for the phone's language is preferred; when only a network voice exists it is used and
 * [ReplySpeaker.speak] says so. Only lengths and error codes are logged, never the text.
 */
class AndroidReplySpeaker(private val context: Context) : ReplySpeaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var finished: (() -> Unit)? = null

    override fun prepare(onReady: (Boolean) -> Unit) {
        if (tts != null) return onReady(ready)
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) Log.i(TAG, "no text-to-speech engine: status $status")
            onReady(ready)
        }
    }

    override fun speak(parts: List<String>, onDone: () -> Unit): Boolean {
        val engine = tts?.takeIf { ready } ?: return false.also { onDone() }
        stop()
        val voice = chooseVoice(engine)
        voice?.let(engine::setVoice)
        finished = onDone
        val last = "reply-${parts.size - 1}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == last) finish()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.i(TAG, "speech failed: error $errorCode")
                finish()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
        })
        parts.forEachIndexed { index, part -> engine.speak(part, TextToSpeech.QUEUE_ADD, null, "reply-$index") }
        return voice?.isNetworkConnectionRequired == true
    }

    private fun finish() {
        val done = finished ?: return
        finished = null
        done()
    }

    override fun stop() {
        tts?.stop()
        finish()
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    /** The phone's language: an installed offline voice first, then the engine's default. */
    private fun chooseVoice(engine: TextToSpeech): Voice? {
        val locale = Locale.getDefault()
        val voices = try {
            engine.voices.orEmpty()
        } catch (e: RuntimeException) {
            // Some engines throw here rather than return null.
            emptySet()
        }
        val usable = voices.filter { it.locale.language == locale.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        return usable.filter { !it.isNetworkConnectionRequired }.minByOrNull { if (it.locale.country == locale.country) 0 else 1 }
            ?: engine.defaultVoice
            ?: usable.firstOrNull()
    }

    private companion object {
        const val TAG = "BruceSpeech"
    }
}
