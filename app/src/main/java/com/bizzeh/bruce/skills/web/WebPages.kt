package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.policy.WebAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

sealed interface PageResult {
    /** [address] is where the page was finally read from, after any redirects. */
    data class Read(val address: String, val title: String?, val text: String, val cut: Boolean) : PageResult

    data class Failed(val reason: String) : PageResult
}

/**
 * Reads one web page with GET only (TASK-069): no cookies, no stored credentials, no request body.
 * Redirects are followed one at a time, each to a site [allowed] accepts and never from https to
 * http. At most [MAX_BYTES] are read; HTML becomes plain text, other text types are kept as they are.
 */
class WebPages(
    private val transport: HttpTransport,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
) {
    suspend fun read(address: String, allowed: suspend (host: String) -> Boolean): PageResult {
        var current = address.trim()
        repeat(MAX_REDIRECTS + 1) {
            val host = WebAddress.host(current) ?: return PageResult.Failed("That is not an http or https address.")
            if (!allowed(host)) return PageResult.Failed("The page leads to $host, which the network setting does not allow.")
            val response = try {
                withContext(io) { transport.openSingle(current, mapOf("User-Agent" to userAgent, "Accept" to ACCEPT)) }
            } catch (e: IOException) {
                return PageResult.Failed("The site could not be reached.")
            }
            response.use {
                val location = response.headers["location"]
                if (response.status in REDIRECTS && location != null) {
                    val next = resolve(current, location) ?: return PageResult.Failed("The page redirects to an address Bruce cannot follow.")
                    if (current.startsWith("https:", ignoreCase = true) && !next.startsWith("https:", ignoreCase = true)) {
                        return PageResult.Failed("The page redirects from a secure address to an insecure one, which Bruce does not follow.")
                    }
                    current = next
                    return@repeat
                }
                if (response.status !in 200..299) return PageResult.Failed("The site answered with HTTP ${response.status}.")
                val type = response.headers["content-type"].orEmpty().lowercase()
                val kind = when {
                    type.startsWith("text/html") || type.startsWith("application/xhtml") -> Kind.HTML
                    type.startsWith("text/") || type.startsWith("application/json") || type.startsWith("application/xml") || type.isEmpty() -> Kind.TEXT
                    else -> return PageResult.Failed("The page is not text (${type.substringBefore(';')}).")
                }
                val (bytes, cut) = withContext(io) { readUpTo(response.body, MAX_BYTES) }
                val body = String(bytes, charset(type))
                return if (kind == Kind.HTML) {
                    PageResult.Read(current, HtmlText.title(body), HtmlText.text(body), cut)
                } else {
                    PageResult.Read(current, null, body.trim(), cut)
                }
            }
        }
        return PageResult.Failed("The page redirects too many times.")
    }

    private enum class Kind { HTML, TEXT }

    private fun resolve(from: String, location: String): String? = try {
        URI(from).resolve(location.trim()).toString()
    } catch (e: URISyntaxException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun charset(type: String): Charset {
        val name = type.substringAfter("charset=", "").trim().trim('"').substringBefore(';')
        return try {
            if (name.isEmpty()) Charsets.UTF_8 else Charset.forName(name)
        } catch (e: IllegalCharsetNameException) {
            Charsets.UTF_8
        } catch (e: UnsupportedCharsetException) {
            Charsets.UTF_8
        }
    }

    /** The first [limit] bytes, and whether there was more. */
    private fun readUpTo(stream: InputStream, limit: Int): Pair<ByteArray, Boolean> {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buffer.size() < limit) {
            val read = stream.read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) return buffer.toByteArray() to false
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray() to (stream.read() >= 0)
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private const val ACCEPT = "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1"
    }
}
