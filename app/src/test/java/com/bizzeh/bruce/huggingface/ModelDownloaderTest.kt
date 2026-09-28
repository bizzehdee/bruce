package com.bizzeh.bruce.huggingface

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.random.Random

class ModelDownloaderTest {
    @TempDir
    lateinit var dir: File

    private val content = Random(42).nextBytes(1_000_000)
    private val sha = sha256(content)
    private val server = RangeServer(content)
    private var allowed = true
    private var freeSpace = Long.MAX_VALUE
    private val modelsDir by lazy { File(dir, "models") }
    private val downloader by lazy {
        ModelDownloader(server, modelsDir, { allowed }, Dispatchers.IO, "Bruce/test", { freeSpace })
    }

    private fun download(sha256: String? = sha, path: String = "sub/Model Q4.gguf", onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        runBlocking { downloader.download("a/b", path, content.size.toLong(), sha256, onProgress = onProgress) }

    private fun partial() = File(modelsDir, ".download-$sha.part")

    @Test
    fun downloadsVerifiesAndNamesTheModel() {
        val progress = mutableListOf<Long>()

        val result = download { done, total -> progress += done; assertEquals(content.size.toLong(), total) }

        val file = (result as DownloadResult.Downloaded).file
        assertEquals("Model_Q4.gguf", file.name)
        assertArrayEquals(content, file.readBytes())
        assertEquals(content.size.toLong(), progress.last())
        assertFalse(partial().exists())
        assertEquals("https://huggingface.co/a/b/resolve/main/sub/Model%20Q4.gguf", server.urls.single())
        assertEquals(listOf<String?>(null), server.ranges)
    }

    @Test
    fun interruptedDownloadResumesFromThePartialFile() {
        server.failAfterBytes = 300_000

        assertEquals(DownloadResult.Failed(DownloadError.INTERRUPTED), download())
        val kept = partial().length()
        assertTrue(kept in 1 until content.size)

        server.failAfterBytes = null
        val file = (download() as DownloadResult.Downloaded).file

        assertArrayEquals(content, file.readBytes())
        assertEquals("bytes=$kept-", server.ranges.last())
    }

    @Test
    fun serverIgnoringRangeRestartsCleanly() {
        partial().apply { parentFile.mkdirs(); writeBytes(content.copyOf(1000)) }
        server.ignoreRange = true

        assertArrayEquals(content, (download() as DownloadResult.Downloaded).file.readBytes())
    }

    @Test
    fun alreadyCompletePartialIsVerifiedWithoutARequest() {
        partial().apply { parentFile.mkdirs(); writeBytes(content) }

        assertTrue(download() is DownloadResult.Downloaded)
        assertTrue(server.urls.isEmpty())
    }

    @Test
    fun oversizedPartialIsDiscarded() {
        partial().apply { parentFile.mkdirs(); writeBytes(content + ByteArray(10)) }

        assertArrayEquals(content, (download() as DownloadResult.Downloaded).file.readBytes())
        assertEquals(listOf<String?>(null), server.ranges)
    }

    @Test
    fun checksumMismatchDeletesTheFile() {
        val wrong = sha256(byteArrayOf(1))

        assertEquals(DownloadResult.Failed(DownloadError.VERIFICATION_FAILED), download(sha256 = wrong))
        assertFalse(File(modelsDir, ".download-$wrong.part").exists())
        assertTrue(modelsDir.listFiles()!!.none { it.name.endsWith(".gguf") })
    }

    @Test
    fun filesWithoutAValidChecksumAreRefusedBeforeAnyRequest() {
        assertEquals(DownloadResult.Failed(DownloadError.NO_CHECKSUM), download(sha256 = null))
        assertEquals(DownloadResult.Failed(DownloadError.NO_CHECKSUM), download(sha256 = "abc"))
        assertTrue(server.urls.isEmpty())
    }

    @Test
    fun networkModeAndStorageAreCheckedFirst() {
        allowed = false
        assertEquals(DownloadResult.Failed(DownloadError.NETWORK_DISABLED), download())
        allowed = true
        freeSpace = content.size - 1L
        assertEquals(DownloadResult.Failed(DownloadError.INSUFFICIENT_STORAGE), download())
        assertTrue(server.urls.isEmpty())
    }

    @Test
    fun resumeOnlyNeedsSpaceForTheRemainder() {
        partial().apply { parentFile.mkdirs(); writeBytes(content.copyOf(600_000)) }
        freeSpace = 400_000

        assertTrue(download() is DownloadResult.Downloaded)
    }

    @Test
    fun httpFailuresMapToErrors() {
        val cases = mapOf(
            401 to DownloadError.UNAUTHORISED,
            404 to DownloadError.NOT_FOUND,
            429 to DownloadError.RATE_LIMITED,
            502 to DownloadError.SERVER_ERROR,
            304 to DownloadError.UNEXPECTED_RESPONSE,
        )
        for ((status, error) in cases) {
            server.statusOverride = status
            assertEquals(DownloadResult.Failed(error), download(), "status $status")
        }
        server.statusOverride = null
        server.openFails = true
        assertEquals(DownloadResult.Failed(DownloadError.INTERRUPTED), download())
    }

    @Test
    fun unexpectedRangesAndOverlongBodiesAreRejected() {
        partial().apply { parentFile.mkdirs(); writeBytes(content.copyOf(1000)) }
        server.contentRangeOverride = "bytes 0-999999/1000000"
        assertEquals(DownloadResult.Failed(DownloadError.UNEXPECTED_RESPONSE), download())

        server.contentRangeOverride = null
        partial().delete()
        server.extraBytes = 10
        assertEquals(DownloadResult.Failed(DownloadError.UNEXPECTED_RESPONSE), download())
    }

    @Test
    fun cancellationKeepsThePartialFile() = runBlocking {
        val started = CompletableDeferred<Unit>()
        server.pauseAfterBytes = 500_000
        val job = async(Dispatchers.IO) {
            downloader.download("a/b", "m.gguf", content.size.toLong(), sha) { done, _ ->
                if (done >= 500_000) started.complete(Unit)
            }
        }
        started.await()
        job.cancel()
        server.release()
        runCatching { job.await() }

        assertTrue(partial().length() in 500_000 until content.size.toLong())
    }

    @Test
    fun existingNameGetsASuffix() {
        modelsDir.mkdirs()
        File(modelsDir, "m.gguf").writeText("existing")

        assertEquals("m-1.gguf", (download(path = "m.gguf") as DownloadResult.Downloaded).file.name)
    }

    @Test
    fun invalidArgumentsAreCallerBugs() {
        assertThrows<IllegalArgumentException> { runBlocking { downloader.download("bad", "m.gguf", 1, sha) } }
        assertThrows<IllegalArgumentException> { runBlocking { downloader.download("a/b", "../m.gguf", 1, sha) } }
        assertThrows<IllegalArgumentException> { runBlocking { downloader.download("a/b", "m.gguf", 0, sha) } }
        assertThrows<IllegalArgumentException> { runBlocking { downloader.download("a/b", "m.gguf", 1, sha, revision = "a/b") } }
        assertThrows<IllegalArgumentException> { ModelDownloader(server, dir, { true }, Dispatchers.IO, "x", baseUrl = "http://h") }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Serves [file] with Range support, and can fail or pause part-way through a body. */
    private class RangeServer(private val file: ByteArray) : HttpTransport {
        val urls = mutableListOf<String>()
        val ranges = mutableListOf<String?>()
        var failAfterBytes: Int? = null
        var pauseAfterBytes: Int? = null
        var ignoreRange = false
        var statusOverride: Int? = null
        var contentRangeOverride: String? = null
        var extraBytes = 0
        var openFails = false
        private val paused = java.util.concurrent.CountDownLatch(1)

        fun release() = paused.countDown()

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")

        override fun open(url: String, headers: Map<String, String>): StreamingResponse {
            urls += url
            ranges += headers["Range"]
            if (openFails) throw IOException("connection refused")
            statusOverride?.let { return StreamingResponse(it, emptyMap(), ByteArrayInputStream(ByteArray(0))) {} }
            val start = headers["Range"]?.takeUnless { ignoreRange }?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val body = file.copyOfRange(start, file.size) + ByteArray(extraBytes)
            val status = if (headers["Range"] != null && !ignoreRange) 206 else 200
            val contentRange = contentRangeOverride ?: "bytes $start-${file.size - 1}/${file.size}"
            return StreamingResponse(status, mapOf("content-range" to contentRange), stream(body)) {}
        }

        private fun stream(body: ByteArray) = object : InputStream() {
            private var position = 0

            override fun read(): Int = error("bulk reads only")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (position >= body.size) return -1
                failAfterBytes?.let { if (position >= it) throw IOException("connection reset") }
                pauseAfterBytes?.let { if (position >= it) paused.await() }
                val count = minOf(len, 64 * 1024, body.size - position)
                System.arraycopy(body, position, b, off, count)
                position += count
                return count
            }
        }
    }
}
