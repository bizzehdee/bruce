package com.bizzeh.bruce.huggingface

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

/** Minimal HTTP GET, so the Hub client can be tested without a network. */
interface HttpTransport {
    /**
     * Returns the response, or null when the body exceeds [maxBytes].
     * Throws [IOException] when the network fails.
     */
    fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse?
}

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { readBounded(it, maxBytes) ?: return null } ?: ByteArray(0)
            val responseHeaders = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.joinToString(",") }
            return HttpResponse(status, responseHeaders, body)
        } finally {
            connection.disconnect()
        }
    }

    /** Null if the stream holds more than [maxBytes]; the rest is never read into memory. */
    private fun readBounded(stream: InputStream, maxBytes: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) return buffer.toByteArray()
            if (buffer.size() + read > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
    }
}
