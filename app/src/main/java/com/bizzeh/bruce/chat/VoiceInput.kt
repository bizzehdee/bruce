package com.bizzeh.bruce.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/** Where voice input stands in the chat (TASK-074). */
enum class VoiceState {
    /** The phone has no on-device recogniser: no microphone button. */
    UNAVAILABLE,
    IDLE,
    LISTENING,
}

/** Speech to text on the phone; AndroidVoiceRecogniser on a phone. */
interface VoiceRecogniser {
    val available: Boolean

    /** [onDone] gets the recognised text, or null when nothing was recognised or it failed. */
    fun start(onDone: (String?) -> Unit)
    fun stop()
}

object VoiceText {
    /** Spoken text joins what is already typed, with one space between. */
    fun append(input: String, spoken: String): String {
        val words = spoken.trim()
        if (words.isEmpty()) return input
        return if (input.isBlank()) words else input.trimEnd() + " " + words
    }
}

/**
 * Android's on-device speech recogniser only (owner, 2026-09-29): audio never leaves the phone, so
 * it needs no network mode. Phones before Android 12, or without an on-device recogniser, have
 * none. Must be used on the main thread, as SpeechRecognizer requires.
 */
class AndroidVoiceRecogniser(private val context: Context) : VoiceRecogniser {
    private var recogniser: SpeechRecognizer? = null

    override val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun start(onDone: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return onDone(null)
        stop()
        val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recogniser = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }

            override fun onError(error: Int) {
                // Codes only: nothing the user said is logged.
                Log.i(TAG, "recognition ended: error $error")
                finish(null)
            }

            private fun finish(text: String?) {
                stop()
                onDone(text)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
        )
    }

    override fun stop() {
        recogniser?.destroy()
        recogniser = null
    }

    private companion object {
        const val TAG = "BruceVoice"
    }
}
