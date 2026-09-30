package com.bizzeh.bruce.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOutputTest {
    @Test
    fun repliesAreReadAsPlainSentencesWithoutCodeOrReasoning() {
        val reply = """
            <think>Plan the answer.</think>
            # Steps

            1. Open **Settings**, then `Wi-Fi`.
            2. Read [the guide](https://example.com).

            ```kotlin
            val x = 1
            ```

            > Take care.

            | Name | Size |
            |---|---|
            | a | 1 |

            ![A dog](https://example.com/dog.png)

            ---
            Line one  
            line two
        """.trimIndent()

        assertEquals(
            "Steps\nOpen Settings, then Wi-Fi.\nRead the guide.\nTake care.\nName, Size\na, 1\nA dog\nLine one line two",
            SpokenText.of(reply),
        )
        assertEquals("", SpokenText.of("<think>only thinking"))
    }

    @Test
    fun longTextIsSplitAtParagraphsThenSentencesThenWords() {
        assertEquals(listOf("One.", "Two."), SpokenText.parts("One.\n\n  \nTwo.", 20))
        assertEquals(listOf("First sentence.", "Second one here."), SpokenText.parts("First sentence. Second one here.", 20))
        assertEquals(listOf("aaaaa", "aaaaa", "aa", "b"), SpokenText.parts("aaaaaaaaaaaa b", 5))
        SpokenText.parts("word ".repeat(2000), ReadAloud.MAX_PART).forEach { assertTrue(it.length <= ReadAloud.MAX_PART) }
    }

    private class FakeSpeaker(var ready: Boolean = true, var network: Boolean = false) : ReplySpeaker {
        val spoken = mutableListOf<List<String>>()
        var done: (() -> Unit)? = null
        var stops = 0
        var released = false

        override fun prepare(onReady: (Boolean) -> Unit) = onReady(ready)

        override fun speak(parts: List<String>, onDone: () -> Unit): Boolean {
            spoken += parts
            done = onDone
            return network
        }

        override fun stop() {
            stops++
            done?.also { done = null }?.invoke()
        }

        override fun release() {
            released = true
        }
    }

    @Test
    fun readingMarksTheReplyUntilItEnds() {
        val speaker = FakeSpeaker(network = true)
        val aloud = ReadAloud(speaker)
        aloud.prepare()
        var finished = 0

        aloud.read(3, "Hello **there**.") { finished++ }

        assertEquals(SpeechState(available = true, reading = 3, needsNetwork = true), aloud.state.value)
        assertEquals(listOf(listOf("Hello there.")), speaker.spoken)
        speaker.done!!()
        assertEquals(SpeechState(available = true), aloud.state.value)
        assertEquals(1, finished)
    }

    @Test
    fun startingAnotherReplyStopsTheFirst() {
        val speaker = FakeSpeaker()
        val aloud = ReadAloud(speaker)
        aloud.prepare()
        var firstDone = false

        aloud.read(1, "One.") { firstDone = true }
        aloud.read(3, "Three.")

        assertTrue(firstDone)
        assertEquals(3, aloud.state.value.reading)
        aloud.stop()
        assertEquals(null, aloud.state.value.reading)
        aloud.release()
        assertTrue(speaker.released)
        assertFalse(aloud.state.value.available)
    }

    @Test
    fun nothingToSayOrNoEngineEndsAtOnce() {
        val silent = ReadAloud(FakeSpeaker(ready = false)).apply { prepare() }
        var ended = 0
        silent.read(1, "Hello.") { ended++ }
        val speaker = FakeSpeaker()
        val codeOnly = ReadAloud(speaker).apply { prepare() }
        codeOnly.read(1, "```\ncode\n```") { ended++ }

        assertEquals(2, ended)
        assertEquals(emptyList<List<String>>(), speaker.spoken)
        assertEquals(null, codeOnly.state.value.reading)
    }
}
