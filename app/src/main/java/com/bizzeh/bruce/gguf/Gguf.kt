package com.bizzeh.bruce.gguf

import java.io.File
import java.io.InputStream

object Gguf {
    private val MAGIC = "GGUF".toByteArray(Charsets.US_ASCII)

    val magicLength: Int = MAGIC.size

    fun hasMagic(file: File): Boolean = file.inputStream().use { hasMagic(readHeader(it)) }

    fun hasMagic(header: ByteArray): Boolean =
        header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] }

    /** Reads up to [magicLength] bytes; fewer only if the stream ends first. */
    fun readHeader(stream: InputStream): ByteArray {
        val header = ByteArray(MAGIC.size)
        var filled = 0
        while (filled < header.size) {
            val read = stream.read(header, filled, header.size - filled)
            if (read < 0) break
            filled += read
        }
        return header.copyOf(filled)
    }
}
