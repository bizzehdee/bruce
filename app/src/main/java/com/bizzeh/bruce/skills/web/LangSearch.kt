package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.skills.DenialCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * LangSearch's free web search with the user's own free key (TASK-113;
 * research/sources/web-search-providers-2026-10-02). The key goes only to LangSearch's API host, and
 * the request does not follow redirects, so it cannot be sent elsewhere.
 */
class LangSearch(
    private val transport: HttpTransport,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
    private val key: suspend () -> String?,
) : SearchEngine {
    override val host = HOST

    override suspend fun search(query: String): SearchResult {
        val apiKey = key() ?: return SearchResult.Failed(DenialCode.TOOL_UNAVAILABLE, "No LangSearch key is saved in Settings.")
        val body = JSONObject().put("query", query.trim()).put("count", RESULTS).toString().toByteArray(Charsets.UTF_8)
        val headers = mapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json", "Accept" to "application/json", "User-Agent" to userAgent)
        return withContext(io) {
            try {
                transport.postStream("https://$HOST/v1/web-search", headers, body, TIMEOUT_MS).use { response ->
                    when (response.status) {
                        200 -> parse(response.body.readAtMost(MAX_BYTES))
                        401, 403 -> SearchResult.Failed(DenialCode.TOOL_FAILED, "LangSearch did not accept the search key. The user can check it in Settings.")
                        429 -> SearchResult.Failed(DenialCode.RATE_LIMITED, "LangSearch's daily allowance or rate limit was reached. Try again later.")
                        else -> SearchResult.Failed(DenialCode.TOOL_FAILED, "LangSearch answered with HTTP ${response.status}.")
                    }
                }
            } catch (e: IOException) {
                SearchResult.Failed(DenialCode.TOOL_FAILED, "LangSearch could not be reached.")
            }
        }
    }

    private fun parse(bytes: ByteArray): SearchResult {
        if (bytes.size > MAX_BYTES) return SearchResult.Failed(DenialCode.TOOL_FAILED, "LangSearch's answer was too large.")
        return try {
            val pages = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("data")?.optJSONObject("webPages")?.optJSONArray("value")
                ?: return SearchResult.Found(emptyList())
            SearchResult.Found(
                (0 until pages.length()).mapNotNull { i ->
                    val page = pages.optJSONObject(i) ?: return@mapNotNull null
                    val text = page.optString("snippet").ifBlank { page.optString("text") }
                    SearchSource(page.optString("name"), page.optString("url"), listOfNotNull(text.takeIf(String::isNotBlank))).takeIf { it.url.isNotBlank() }
                },
            )
        } catch (e: JSONException) {
            SearchResult.Failed(DenialCode.TOOL_FAILED, "LangSearch's answer could not be read.")
        }
    }

    companion object {
        const val HOST = "api.langsearch.com"

        /** Enough sources for a small model's context. */
        private const val RESULTS = 5
        private const val TIMEOUT_MS = 20_000
        private const val MAX_BYTES = 1024 * 1024
    }
}
