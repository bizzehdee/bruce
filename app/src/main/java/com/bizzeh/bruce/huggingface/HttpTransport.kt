package com.bizzeh.bruce.huggingface

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

/** A response whose body is read as a stream; close it when done. */
class StreamingResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: InputStream,
    private val onClose: () -> Unit,
) : java.io.Closeable {
    override fun close() {
        body.close()
        onClose()
    }
}

/** Minimal HTTP GET, so the Hub client can be tested without a network. */
interface HttpTransport {
    /**
     * Returns the response, or null when the body exceeds [maxBytes].
     * Throws [IOException] when the network fails.
     */
    fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse?

    /** Opens a response for streaming large bodies. Throws [IOException] when the network fails. */
    fun open(url: String, headers: Map<String, String>): StreamingResponse
}

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse? =
        open(url, headers).use { response ->
            val body = readBounded(response.body, maxBytes) ?: return null
            HttpResponse(response.status, response.headers, body)
        }

    override fun open(url: String, headers: Map<String, String>): StreamingResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = (if (status >= 400) connection.errorStream else connection.inputStream) ?: java.io.ByteArrayInputStream(ByteArray(0))
            val responseHeaders = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.joinToString(",") }
            return StreamingResponse(status, responseHeaders, stream, connection::disconnect)
        } catch (e: IOException) {
            connection.disconnect()
            throw e
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
