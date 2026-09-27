package com.bizzeh.bruce.inference

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Decodes a stream of byte pieces whose boundaries may fall inside a UTF-8 character. */
internal class Utf8PieceDecoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun decode(piece: ByteArray): String = decode(pending + piece, endOfInput = false)

    fun finish(): String = decode(pending, endOfInput = true)

    private fun decode(bytes: ByteArray, endOfInput: Boolean): String {
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(bytes.size + 1)
        decoder.decode(input, output, endOfInput)
        if (endOfInput) decoder.flush(output)
        pending = ByteArray(input.remaining()).also(input::get)
        return output.flip().toString()
    }
}
