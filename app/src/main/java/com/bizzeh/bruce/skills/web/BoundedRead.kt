package com.bizzeh.bruce.skills.web

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Up to [limit] + 1 bytes, so a caller can tell an answer that is too large without reading it all. */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() <= limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
