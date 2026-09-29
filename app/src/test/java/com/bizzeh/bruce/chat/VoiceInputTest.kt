package com.bizzeh.bruce.chat

import android.content.Context
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
class VoiceInputTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val voice = AndroidVoiceRecogniser(context)
    private val heard = mutableListOf<String?>()

    @Test
    fun spokenTextJoinsWhatIsTyped() {
        assertEquals("turn it up", VoiceText.append("", " turn it up "))
        assertEquals("please turn it up", VoiceText.append("please  ", "turn it up"))
        assertEquals("please", VoiceText.append("please", "  "))
    }

    @Test
    fun onlyAnOnDeviceRecogniserIsUsed() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        assertFalse(voice.available)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        assertTrue(voice.available)
    }

    @Test
    @Config(sdk = [30])
    fun beforeAndroid12ThereIsNone() {
        assertFalse(voice.available)
        voice.start { heard += it }
        assertEquals(listOf<String?>(null), heard)
    }

    @Test
    fun theBestResultIsGivenAndTheRecogniserReleased() {
        voice.start { heard += it }
        val recogniser = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        val shadow = shadowOf(recogniser)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, shadow.lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertTrue(shadow.lastRecognizerIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))

        shadow.triggerOnResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("what time is it", "what time it is")) })

        assertEquals(listOf<String?>("what time is it"), heard)
        assertTrue(shadow.isDestroyed)
    }

    @Test
    fun anErrorGivesNothingAndStoppingReleasesTheRecogniser() {
        voice.start { heard += it }
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf<String?>(null), heard)

        voice.start { heard += it }
        val listening = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        voice.stop()
        assertTrue(shadowOf(listening).isDestroyed)
        assertNull(heard.getOrNull(1))
    }
}
