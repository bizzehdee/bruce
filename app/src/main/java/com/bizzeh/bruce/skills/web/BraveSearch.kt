package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.skills.DenialCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

data class SearchSource(val title: String, val url: String, val snippets: List<String>)

sealed interface SearchResult {
    data class Found(val sources: List<SearchSource>) : SearchResult

    data class Failed(val code: DenialCode, val reason: String) : SearchResult
}

/** A web search service the `web_search` skill can use (TASK-113); [host] is the one site it reaches. */
interface SearchEngine {
    val host: String

    suspend fun search(query: String): SearchResult
}

/**
 * Brave's LLM Context search (owner's choice, 2026-09-29; research/sources/brave-search-api-2026-09-29):
 * page text chunks for the query, the smallest budget Brave allows so small models' contexts cope.
 * The key goes only to Brave's API host; redirects are not followed, so it cannot be sent elsewhere.
 */
class BraveSearch(
    private val transport: HttpTransport,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
    private val key: suspend () -> String?,
) : SearchEngine {
    override val host = HOST

    override suspend fun search(query: String): SearchResult {
        val apiKey = key() ?: return SearchResult.Failed(DenialCode.TOOL_UNAVAILABLE, "No Brave Search key is saved in Settings.")
        val url = "https://$HOST/res/v1/llm/context?q=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&maximum_number_of_tokens=$TOKENS&maximum_number_of_urls=$URLS"
        val headers = mapOf("X-Subscription-Token" to apiKey, "Accept" to "application/json", "User-Agent" to userAgent)
        return withContext(io) {
            try {
                transport.openSingle(url, headers).use { response ->
                    when (response.status) {
                        200 -> parse(readUpTo(response.body.readBytes(), MAX_BYTES))
                        401, 403 -> SearchResult.Failed(DenialCode.TOOL_FAILED, "Brave did not accept the search key. The user can check it in Settings.")
                        429 -> SearchResult.Failed(DenialCode.RATE_LIMITED, "Brave's search limit was reached. Try again later.")
                        else -> SearchResult.Failed(DenialCode.TOOL_FAILED, "Brave's search answered with HTTP ${response.status}.")
                    }
                }
            } catch (e: IOException) {
                SearchResult.Failed(DenialCode.TOOL_FAILED, "Brave's search could not be reached.")
            }
        }
    }

    private fun readUpTo(bytes: ByteArray, limit: Int) = if (bytes.size > limit) null else bytes

    private fun parse(bytes: ByteArray?): SearchResult {
        bytes ?: return SearchResult.Failed(DenialCode.TOOL_FAILED, "Brave's answer was too large.")
        return try {
            val generic = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("grounding")?.optJSONArray("generic")
                ?: return SearchResult.Found(emptyList())
            val sources = (0 until generic.length()).mapNotNull { i ->
                val item = generic.optJSONObject(i) ?: return@mapNotNull null
                val snippets = item.optJSONArray("snippets")?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
                SearchSource(item.optString("title"), item.optString("url"), snippets).takeIf { it.url.isNotBlank() }
            }
            SearchResult.Found(sources)
        } catch (e: JSONException) {
            SearchResult.Failed(DenialCode.TOOL_FAILED, "Brave's answer could not be read.")
        }
    }

    companion object {
        const val HOST = "api.search.brave.com"

        /** Brave's minimum context budget. */
        private const val TOKENS = 1024
        private const val URLS = 5
        private const val MAX_BYTES = 1024 * 1024
    }
}
