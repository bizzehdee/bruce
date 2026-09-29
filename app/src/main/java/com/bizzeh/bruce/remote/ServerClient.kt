package com.bizzeh.bruce.remote

import java.io.InputStream
import com.bizzeh.bruce.huggingface.HttpTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

sealed interface ServerCheck {
    data class Models(val ids: List<String>) : ServerCheck

    data class Failed(val reason: String) : ServerCheck
}

/**
 * Talks to a model server's OpenAI-compatible API (TASK-075). Adding a server is the permission to
 * reach that one address, so only Offline mode blocks it. Redirects are not followed: the API key
 * must not go anywhere but the server the user named.
 */
class ServerClient(
    private val transport: HttpTransport,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
    private val networkAllowed: suspend () -> Boolean,
) {
    suspend fun models(server: RemoteServer, key: String?): ServerCheck {
        if (!networkAllowed()) return ServerCheck.Failed("Bruce is set to Offline, so it cannot reach servers.")
        val headers = buildMap {
            put("Accept", "application/json")
            put("User-Agent", userAgent)
            key?.let { put("Authorization", "Bearer $it") }
        }
        return withContext(io) {
            try {
                transport.openSingle("${server.address}/v1/models", headers).use { response ->
                    when (response.status) {
                        200 -> parse(readUpTo(response.body, MAX_BYTES + 1))
                        401, 403 -> ServerCheck.Failed("The server refused the API key.")
                        in 300..399 -> ServerCheck.Failed("The server redirects elsewhere; enter the address it redirects to.")
                        else -> ServerCheck.Failed("The server answered with HTTP ${response.status}.")
                    }
                }
            } catch (e: IOException) {
                ServerCheck.Failed("The server could not be reached.")
            }
        }
    }

    private fun readUpTo(stream: InputStream, limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buffer.size() < limit) {
            val read = stream.read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun parse(bytes: ByteArray): ServerCheck {
        if (bytes.size > MAX_BYTES) return ServerCheck.Failed("The server's model list was too large.")
        return try {
            val data = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("data") ?: return ServerCheck.Failed("The server does not look like an OpenAI-compatible API.")
            ServerCheck.Models((0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) })
        } catch (e: JSONException) {
            ServerCheck.Failed("The server does not look like an OpenAI-compatible API.")
        }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
