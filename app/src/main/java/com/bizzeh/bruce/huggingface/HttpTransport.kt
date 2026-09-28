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

    /** POSTs a form body; returns null when the response exceeds [maxBytes]. Throws [IOException] on network failure. */
    fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse?
}

private const val MAX_REDIRECTS = 5
private val REDIRECTS = setOf(301, 302, 303, 307, 308)

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
        var current = URL(url)
        var currentHeaders = headers
        repeat(MAX_REDIRECTS) {
            val response = connect(current, currentHeaders)
            val location = response.headers["location"]
            if (response.status !in REDIRECTS || location == null) return response
            response.close()
            val next = URL(current, location)
            if (current.protocol == "https" && next.protocol != "https") throw IOException("refused a redirect from HTTPS to ${next.protocol}")
            // A bearer token belongs to the host it was issued for, never to a redirect target
            // elsewhere (Hugging Face downloads redirect to a CDN).
            if (next.authority != current.authority) {
                currentHeaders = currentHeaders.filterKeys { !it.equals("Authorization", ignoreCase = true) }
            }
            current = next
        }
        throw IOException("too many redirects")
    }

    override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            headers.forEach(connection::setRequestProperty)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = form.entries.joinToString("&") { (key, value) ->
                java.net.URLEncoder.encode(key, "UTF-8") + "=" + java.net.URLEncoder.encode(value, "UTF-8")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = (if (status >= 400) connection.errorStream else connection.inputStream) ?: java.io.ByteArrayInputStream(ByteArray(0))
            val bytes = stream.use { readBounded(it, maxBytes) } ?: return null
            return HttpResponse(status, headerMap(connection), bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(url: URL, headers: Map<String, String>): StreamingResponse {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            // Redirects are followed in open(), so headers can be checked at each hop.
            connection.instanceFollowRedirects = false
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = (if (status >= 400) connection.errorStream else connection.inputStream) ?: java.io.ByteArrayInputStream(ByteArray(0))
            return StreamingResponse(status, headerMap(connection), stream, connection::disconnect)
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    private fun headerMap(connection: HttpURLConnection): Map<String, String> = connection.headerFields
        .filterKeys { it != null }
        .mapKeys { it.key.lowercase() }
        .mapValues { it.value.joinToString(",") }

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
