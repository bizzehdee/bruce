package com.bizzeh.bruce.huggingface

import com.bizzeh.bruce.models.ModelImporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface DownloadResult {
    data class Downloaded(val file: File) : DownloadResult

    data class Failed(val error: DownloadError) : DownloadResult
}

enum class DownloadError {
    NETWORK_DISABLED,
    /** The connection failed; the partial file is kept, so trying again resumes. */
    INTERRUPTED,
    UNAUTHORISED,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER_ERROR,
    UNEXPECTED_RESPONSE,
    INSUFFICIENT_STORAGE,
    /** The Hub publishes no SHA-256 for this file, so it cannot be verified. */
    NO_CHECKSUM,
    /** The finished file's size or SHA-256 did not match; it was deleted. */
    VERIFICATION_FAILED,
}

/**
 * Downloads a model file from the Hub into app storage. The partial file is named after the
 * expected SHA-256, so an interrupted download resumes only the same content, even after the
 * app restarts; the result becomes a model only once its size and SHA-256 match.
 */
class ModelDownloader(
    private val transport: HttpTransport,
    private val modelsDir: File,
    private val networkAllowed: suspend () -> Boolean,
    private val dispatcher: CoroutineDispatcher,
    private val userAgent: String,
    private val usableSpace: (File) -> Long = File::getUsableSpace,
    private val baseUrl: String = "https://huggingface.co",
    /** The signed-in user's token; the transport drops it when the download redirects to the CDN. */
    private val token: suspend () -> String? = { null },
) {
    init {
        require(baseUrl.startsWith("https://")) { "Hub base URL must use HTTPS" }
    }

    /** [onProgress] receives bytes on disk and the total, on the download thread. */
    suspend fun download(
        repositoryId: String,
        path: String,
        sizeBytes: Long,
        sha256: String?,
        revision: String = "main",
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(dispatcher) {
        require(HubClient.isRepositoryId(repositoryId)) { "not a repository id: $repositoryId" }
        require(HubClient.isSafePath(path) && path.endsWith(".gguf", ignoreCase = true)) { "not a GGUF path: $path" }
        require(REVISION.matches(revision)) { "not a revision: $revision" }
        require(sizeBytes > 0) { "sizeBytes must be positive, was $sizeBytes" }
        val expectedSha = sha256?.lowercase()?.takeIf(SHA256::matches)
            ?: return@withContext DownloadResult.Failed(DownloadError.NO_CHECKSUM)
        if (!networkAllowed()) return@withContext DownloadResult.Failed(DownloadError.NETWORK_DISABLED)

        modelsDir.mkdirs()
        val partial = File(modelsDir, ".download-$expectedSha.part")
        if (partial.length() > sizeBytes) partial.delete()
        if (sizeBytes - partial.length() > usableSpace(modelsDir)) {
            return@withContext DownloadResult.Failed(DownloadError.INSUFFICIENT_STORAGE)
        }

        val url = "$baseUrl/$repositoryId/resolve/${encode(revision)}/" + path.split('/').joinToString("/", transform = ::encode)
        if (partial.length() < sizeBytes) {
            fetchInto(partial, url, sizeBytes, onProgress)?.let { return@withContext DownloadResult.Failed(it) }
        }
        if (partial.length() != sizeBytes || sha256Of(partial) != expectedSha) {
            partial.delete()
            return@withContext DownloadResult.Failed(DownloadError.VERIFICATION_FAILED)
        }
        val target = ModelImporter.uniqueTarget(modelsDir, ModelImporter.sanitisedFileName(path.substringAfterLast('/')))
        if (!partial.renameTo(target)) return@withContext DownloadResult.Failed(DownloadError.INSUFFICIENT_STORAGE)
        DownloadResult.Downloaded(target)
    }

    /** Appends the rest of the file to [partial]; returns an error, or null when the body was read to the end. */
    private suspend fun fetchInto(partial: File, url: String, sizeBytes: Long, onProgress: (Long, Long) -> Unit): DownloadError? {
        val resumeFrom = partial.length()
        val headers = mapOf("User-Agent" to userAgent) +
            (if (resumeFrom > 0) mapOf("Range" to "bytes=$resumeFrom-") else emptyMap()) +
            (token()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap())
        val response = try {
            transport.open(url, headers)
        } catch (e: IOException) {
            return DownloadError.INTERRUPTED
        }
        response.use {
            val append = when (response.status) {
                206 -> {
                    val range = CONTENT_RANGE.matchEntire(response.headers["content-range"].orEmpty())
                    if (range == null || range.groupValues[1].toLong() != resumeFrom || range.groupValues[2].toLong() != sizeBytes) {
                        return DownloadError.UNEXPECTED_RESPONSE
                    }
                    true
                }
                // The server sent the whole file instead of the requested range: start again.
                200 -> false
                401, 403 -> return DownloadError.UNAUTHORISED
                404 -> return DownloadError.NOT_FOUND
                429 -> return DownloadError.RATE_LIMITED
                in 500..599 -> return DownloadError.SERVER_ERROR
                else -> return DownloadError.UNEXPECTED_RESPONSE
            }
            try {
                FileOutputStream(partial, append).use { out ->
                    var written = if (append) resumeFrom else 0L
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = response.body.read(buffer)
                        if (read < 0) break
                        if (written + read > sizeBytes) return DownloadError.UNEXPECTED_RESPONSE
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written, sizeBytes)
                    }
                }
            } catch (e: IOException) {
                return DownloadError.INTERRUPTED
            }
        }
        return null
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private companion object {
        const val BUFFER_BYTES = 256 * 1024
        val REVISION = Regex("[A-Za-z0-9._-]{1,100}")
        val SHA256 = Regex("[0-9a-f]{64}")
        val CONTENT_RANGE = Regex("bytes (\\d+)-\\d+/(\\d+)")
    }
}
