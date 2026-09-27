package com.bizzeh.bruce.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Utf8PieceDecoderTest {
    private val decoder = Utf8PieceDecoder()

    @Test
    fun asciiPassesThrough() {
        assertEquals("Bruce", decoder.decode("Bruce".toByteArray()))
        assertEquals("", decoder.finish())
    }

    @Test
    fun holdsIncompleteCharacterUntilItCompletes() {
        val bytes = "é🐕".toByteArray()

        assertEquals("", decoder.decode(bytes.copyOfRange(0, 1)))
        assertEquals("é", decoder.decode(bytes.copyOfRange(1, 3)))
        assertEquals("🐕", decoder.decode(bytes.copyOfRange(3, 6)))
    }

    @Test
    fun invalidBytesBecomeReplacementCharacters() {
        assertEquals("a�b", decoder.decode(byteArrayOf('a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte())))
    }

    @Test
    fun finishFlushesIncompleteCharacterAsReplacement() {
        decoder.decode(byteArrayOf(0xE2.toByte(), 0x82.toByte()))
        assertEquals("�", decoder.finish())
    }
}
