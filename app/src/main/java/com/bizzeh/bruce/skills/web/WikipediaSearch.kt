package com.bizzeh.bruce.skills.web

import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.skills.DenialCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Wikipedia's search (TASK-113): free, with no key or sign-up, in the phone's language. Results are
 * article titles, addresses and the matching excerpt (research/sources/web-search-providers-2026-10-02).
 */
class WikipediaSearch(
    private val transport: HttpTransport,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
    /** The Wikipedia language edition, such as "en"; the phone's language where Wikipedia has one. */
    private val language: () -> String,
) : SearchEngine {
    override val host: String get() = hostFor(language())

    override suspend fun search(query: String): SearchResult {
        val site = host
        val url = "https://$site/w/rest.php/v1/search/page?q=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=$RESULTS"
        return withContext(io) {
            try {
                transport.openSingle(url, mapOf("Accept" to "application/json", "User-Agent" to userAgent)).use { response ->
                    when (response.status) {
                        200 -> parse(response.body.readAtMost(MAX_BYTES), site)
                        429 -> SearchResult.Failed(DenialCode.RATE_LIMITED, "Wikipedia asked Bruce to slow down. Try again later.")
                        else -> SearchResult.Failed(DenialCode.TOOL_FAILED, "Wikipedia answered with HTTP ${response.status}.")
                    }
                }
            } catch (e: IOException) {
                SearchResult.Failed(DenialCode.TOOL_FAILED, "Wikipedia could not be reached.")
            }
        }
    }

    private fun parse(bytes: ByteArray, site: String): SearchResult {
        if (bytes.size > MAX_BYTES) return SearchResult.Failed(DenialCode.TOOL_FAILED, "Wikipedia's answer was too large.")
        return try {
            val pages = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("pages") ?: return SearchResult.Found(emptyList())
            SearchResult.Found(
                (0 until pages.length()).mapNotNull { i ->
                    val page = pages.optJSONObject(i) ?: return@mapNotNull null
                    val key = page.optString("key").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val excerpt = HtmlText.text(page.optString("excerpt")).replace("\n", " ")
                    val description = page.optString("description").takeIf { it.isNotBlank() && it != "null" }
                    SearchSource(page.optString("title"), "https://$site/wiki/${URLEncoder.encode(key, "UTF-8").replace("+", "_")}", listOfNotNull(description, excerpt.takeIf(String::isNotBlank)))
                },
            )
        } catch (e: JSONException) {
            SearchResult.Failed(DenialCode.TOOL_FAILED, "Wikipedia's answer could not be read.")
        }
    }

    companion object {
        private const val RESULTS = 5
        private const val MAX_BYTES = 512 * 1024
        private val LANGUAGE = Regex("[a-z]{2,3}")

        /** A language code Wikipedia could have an edition for, or English. */
        fun hostFor(language: String): String = "${language.lowercase().takeIf { LANGUAGE.matches(it) } ?: "en"}.wikipedia.org"
    }
}
