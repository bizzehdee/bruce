package com.bizzeh.bruce.skills.web

import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * Wikipedia articles read as their plain-text extract from the MediaWiki API (TextExtracts): the
 * article's text and section headings without its boxes, tables, menus and edit links. As HTML text,
 * the start of a date's article is about 2,000 characters of calendar tables, so a small model asked
 * for the whole page and the phone spent minutes reading it (2026-10-02).
 */
internal object WikipediaArticles {
    private val HOST = Regex("[a-z]{2,3}(-[a-z]+)*\\.wikipedia\\.org")
    private const val ARTICLE_PATH = "/wiki/"

    /** The API address for [address]'s extract, on the same site; null when it is not a Wikipedia article address. */
    fun extractAddress(address: String): String? {
        val uri = try {
            URI(address.trim())
        } catch (e: URISyntaxException) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || !HOST.matches(host) || uri.rawQuery != null) return null
        val title = uri.path?.takeIf { it.startsWith(ARTICLE_PATH) }?.removePrefix(ARTICLE_PATH)?.takeIf { it.isNotBlank() } ?: return null
        return "https://$host/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles=" +
            URLEncoder.encode(title, "UTF-8")
    }

    /** The article's title and text from the API's answer; null for a missing page, an empty extract or an unexpected answer. */
    fun parse(json: String): Pair<String, String>? = try {
        val page = JSONObject(json).getJSONObject("query").getJSONArray("pages").getJSONObject(0)
        val text = page.optString("extract").trim()
        if (page.optBoolean("missing") || text.isEmpty()) null else page.getString("title") to text
    } catch (e: JSONException) {
        null
    }
}
