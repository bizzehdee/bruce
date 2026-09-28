package com.bizzeh.bruce.huggingface

import com.bizzeh.bruce.gguf.GgufError
import com.bizzeh.bruce.gguf.GgufPrefixResult
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** A repository from a Hub search, with the GGUF details the search already carries. */
data class HubModel(
    val id: String,
    val downloads: Long,
    val gated: Boolean,
    val license: String?,
    val architecture: String?,
    val parameterCount: Long?,
    val contextLength: Long?,
)

data class HubFile(val path: String, val sizeBytes: Long, val sha256: String?)

sealed interface HubResult<out T> {
    data class Success<T>(val value: T) : HubResult<T>

    data class Failure(val error: HubError) : HubResult<Nothing>
}

enum class HubError {
    /** The network mode does not allow Hugging Face requests. */
    NETWORK_DISABLED,
    OFFLINE,
    RATE_LIMITED,
    NOT_FOUND,
    /** Gated or private: the user must sign in or accept the model's terms. */
    UNAUTHORISED,
    SERVER_ERROR,
    MALFORMED_RESPONSE,
    RESPONSE_TOO_LARGE,
}

/**
 * Anonymous Hugging Face Hub API client (.learnings/hf-hub-api-for-model-discovery.md).
 * Everything the Hub returns is untrusted: repository ids and file paths are validated,
 * response sizes are capped, and anything unexpected is MALFORMED_RESPONSE.
 */
class HubClient(
    private val transport: HttpTransport,
    private val networkAllowed: suspend () -> Boolean,
    private val dispatcher: CoroutineDispatcher,
    private val userAgent: String,
    private val baseUrl: String = "https://huggingface.co",
) {
    init {
        require(baseUrl.startsWith("https://")) { "Hub base URL must use HTTPS" }
    }

    suspend fun search(query: String, limit: Int = 20): HubResult<List<HubModel>> {
        require(limit in 1..MAX_RESULTS) { "limit must be 1..$MAX_RESULTS, was $limit" }
        val url = "$baseUrl/api/models?search=${encode(query.trim())}&filter=gguf&sort=downloads&direction=-1" +
            "&limit=$limit" + SEARCH_EXPANSIONS.joinToString("") { "&expand[]=$it" }
        return request(url, MAX_SEARCH_BYTES) { body -> parseModels(JSONArray(body)) }
    }

    suspend fun ggufFiles(repositoryId: String, revision: String = "main"): HubResult<List<HubFile>> {
        require(isRepositoryId(repositoryId)) { "not a repository id: $repositoryId" }
        require(REVISION.matches(revision)) { "not a revision: $revision" }
        val url = "$baseUrl/api/models/$repositoryId/tree/${encode(revision)}?recursive=true"
        return request(url, MAX_TREE_BYTES) { body -> parseFiles(JSONArray(body)) }
    }

    /**
     * Reads a model file's GGUF metadata by downloading only the start of it, growing the range
     * until the header parses. Network problems are [HubResult.Failure]; a file that is not
     * valid GGUF is a [HubResult.Success] holding [GgufReadResult.Failed].
     */
    suspend fun ggufHeader(repositoryId: String, path: String, revision: String = "main"): HubResult<GgufReadResult> {
        require(isRepositoryId(repositoryId)) { "not a repository id: $repositoryId" }
        require(REVISION.matches(revision)) { "not a revision: $revision" }
        require(isSafePath(path) && path.endsWith(GGUF_EXTENSION, ignoreCase = true)) { "not a GGUF path: $path" }
        val url = "$baseUrl/$repositoryId/resolve/${encode(revision)}/" + path.split('/').joinToString("/", transform = ::encodePathSegment)

        for (length in HEADER_RANGE_STEPS) {
            val response = when (val fetched = fetch(url, length, mapOf("Range" to "bytes=0-${length - 1}"))) {
                is HubResult.Failure -> return fetched
                is HubResult.Success -> fetched.value
            }
            val total = when (response.status) {
                206 -> contentRangeTotal(response.headers["content-range"]) ?: return HubResult.Failure(HubError.MALFORMED_RESPONSE)
                // A server that ignores Range sends the whole file; this one fitted inside the limit.
                200 -> response.body.size.toLong()
                else -> return HubResult.Failure(HubError.MALFORMED_RESPONSE)
            }
            when (val result = GgufReader.readPrefix(response.body.inputStream(), total)) {
                is GgufPrefixResult.Read -> return HubResult.Success(GgufReadResult.Read(result.metadata))
                is GgufPrefixResult.Failed -> return HubResult.Success(GgufReadResult.Failed(result.error))
                GgufPrefixResult.NeedMoreBytes ->
                    if (response.body.size >= total) return HubResult.Success(GgufReadResult.Failed(GgufError.MALFORMED))
            }
        }
        return HubResult.Failure(HubError.RESPONSE_TOO_LARGE)
    }

    private suspend fun <T> request(url: String, maxBytes: Int, parse: (String) -> T?): HubResult<T> {
        val response = when (val fetched = fetch(url, maxBytes, mapOf("Accept" to "application/json"))) {
            is HubResult.Failure -> return fetched
            is HubResult.Success -> fetched.value
        }
        val parsed = try {
            parse(response.body.toString(Charsets.UTF_8))
        } catch (e: JSONException) {
            null
        }
        return parsed?.let { HubResult.Success(it) } ?: HubResult.Failure(HubError.MALFORMED_RESPONSE)
    }

    /** Network gate, transport call and HTTP status mapping shared by every request. */
    private suspend fun fetch(url: String, maxBytes: Int, headers: Map<String, String>): HubResult<HttpResponse> {
        if (!networkAllowed()) return HubResult.Failure(HubError.NETWORK_DISABLED)
        val response = try {
            withContext(dispatcher) { transport.get(url, headers + ("User-Agent" to userAgent), maxBytes) }
        } catch (e: IOException) {
            return HubResult.Failure(HubError.OFFLINE)
        } ?: return HubResult.Failure(HubError.RESPONSE_TOO_LARGE)
        statusError(response.status)?.let { return HubResult.Failure(it) }
        return HubResult.Success(response)
    }

    private fun contentRangeTotal(header: String?): Long? =
        header?.let(CONTENT_RANGE::matchEntire)?.groupValues?.get(1)?.toLongOrNull()

    private fun encodePathSegment(segment: String) = encode(segment).replace("+", "%20")

    private fun statusError(status: Int): HubError? = when {
        status in 200..299 -> null
        status == 401 || status == 403 -> HubError.UNAUTHORISED
        status == 404 -> HubError.NOT_FOUND
        status == 429 -> HubError.RATE_LIMITED
        status >= 500 -> HubError.SERVER_ERROR
        else -> HubError.MALFORMED_RESPONSE
    }

    private fun parseModels(array: JSONArray): List<HubModel>? = (0 until array.length()).map { index ->
        val item = array.optJSONObject(index) ?: return null
        val id = item.optString("id").takeIf(::isRepositoryId) ?: return null
        val gguf = item.optJSONObject("gguf")
        HubModel(
            id = id,
            downloads = item.optLong("downloads", 0),
            gated = item.opt("gated").let { it != null && it != false && it != JSONObject.NULL },
            license = license(item.optJSONObject("cardData")),
            architecture = gguf?.optString("architecture")?.takeIf { it.isNotBlank() },
            parameterCount = gguf?.positiveLong("total"),
            contextLength = gguf?.positiveLong("context_length"),
        )
    }

    private fun parseFiles(array: JSONArray): List<HubFile>? = (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return null
        val path = item.optString("path")
        if (item.optString("type") != "file" || !path.endsWith(GGUF_EXTENSION, ignoreCase = true)) return@mapNotNull null
        if (!isSafePath(path)) return null
        val size = item.optLong("size", -1).takeIf { it >= 0 } ?: return null
        val sha256 = item.optJSONObject("lfs")?.optString("oid")?.lowercase()?.takeIf(SHA256::matches)
        HubFile(path, size, sha256)
    }

    private fun license(cardData: JSONObject?): String? = when (val value = cardData?.opt("license")) {
        is String -> value.takeIf { it.isNotBlank() }
        is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }
            .joinToString(", ").takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun JSONObject.positiveLong(key: String): Long? = optLong(key, -1).takeIf { it > 0 }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val MAX_RESULTS = 100
        private const val MAX_SEARCH_BYTES = 8 * 1024 * 1024
        private const val MAX_TREE_BYTES = 2 * 1024 * 1024
        private const val GGUF_EXTENSION = ".gguf"
        private val SEARCH_EXPANSIONS = listOf("downloads", "gated", "cardData", "gguf")
        private val REPOSITORY_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}/[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
        private val REVISION = Regex("[A-Za-z0-9._-]{1,100}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val CONTENT_RANGE = Regex("bytes \\d+-\\d+/(\\d+)")

        /** Tokenizer vocabularies make GGUF headers several megabytes; 64 MB is the most fetched. */
        private val HEADER_RANGE_STEPS = listOf(2, 8, 32, 64).map { it * 1024 * 1024 }

        fun isRepositoryId(value: String): Boolean = REPOSITORY_ID.matches(value) && ".." !in value

        /** A repository-relative path with no traversal, backslashes or absolute prefix. */
        internal fun isSafePath(path: String): Boolean =
            path.isNotEmpty() && path.length <= 512 && !path.startsWith("/") && '\\' !in path &&
                path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}
