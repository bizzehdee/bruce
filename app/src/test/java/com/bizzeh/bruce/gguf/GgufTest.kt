package com.bizzeh.bruce.gguf

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class GgufTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun recognisesGgufMagic() {
        val file = File(dir, "m.gguf").apply { writeBytes("GGUFrest".toByteArray()) }
        assertTrue(Gguf.hasMagic(file))
    }

    @Test
    fun rejectsOtherContent() {
        val file = File(dir, "m.gguf").apply { writeBytes("GGML....".toByteArray()) }
        assertFalse(Gguf.hasMagic(file))
    }

    @Test
    fun rejectsFileShorterThanMagic() {
        val file = File(dir, "m.gguf").apply { writeBytes("GG".toByteArray()) }
        assertFalse(Gguf.hasMagic(file))
    }

    @Test
    fun readHeaderAssemblesShortReads() {
        val oneByteAtATime = object : InputStream() {
            private val data = ByteArrayInputStream("GGUFxyz".toByteArray())
            override fun read(): Int = data.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = data.read(b, off, minOf(len, 1))
        }
        assertArrayEquals("GGUF".toByteArray(), Gguf.readHeader(oneByteAtATime))
    }
}
