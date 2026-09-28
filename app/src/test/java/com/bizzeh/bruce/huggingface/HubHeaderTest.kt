package com.bizzeh.bruce.huggingface

import com.bizzeh.bruce.gguf.GgufBuilder
import com.bizzeh.bruce.gguf.GgufError
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class HubHeaderTest {
    private val dispatcher = StandardTestDispatcher()
    private val server = RangeServer()
    private var allowed = true
    private val client = HubClient(server, { allowed }, dispatcher, userAgent = "Bruce/test")
    private val fixture = File("src/androidTest/assets/stories260K.gguf")

    @Test
    fun readsHeaderOfARealModelFromTheFirstRange() = runTest(dispatcher) {
        server.file = fixture.readBytes()

        val result = client.ggufHeader("ggml-org/models", "tinyllamas/stories260K.gguf")

        assertEquals(HubResult.Success(GgufReader.read(fixture)), result)
        assertEquals(listOf("bytes=0-2097151"), server.ranges)
        assertEquals(
            "https://huggingface.co/ggml-org/models/resolve/main/tinyllamas/stories260K.gguf",
            server.urls.single(),
        )
    }

    @Test
    fun growsTheRangeUntilTheHeaderFits() = runTest(dispatcher) {
        val vocabulary = Array(300_000) { "token$it" }
        server.file = GgufBuilder().string("general.architecture", "llama").stringArray("tokenizer.ggml.tokens", *vocabulary).build()
        assertTrue(server.file.size > 2 * 1024 * 1024)

        val result = client.ggufHeader("a/b", "m.gguf") as HubResult.Success

        assertEquals("llama", (result.value as GgufReadResult.Read).metadata.architecture)
        assertEquals(listOf("bytes=0-2097151", "bytes=0-8388607"), server.ranges)
    }

    @Test
    fun headerThatNeverEndsWithin64MbIsTooLarge() = runTest(dispatcher) {
        server.claimedTotal = 10L * 1024 * 1024 * 1024
        server.file = GgufBuilder().build(keyValueCountOverride = 1) + longStringKey()

        assertEquals(HubResult.Failure(HubError.RESPONSE_TOO_LARGE), client.ggufHeader("a/b", "m.gguf"))
        assertEquals(4, server.ranges.size)
    }

    @Test
    fun serverIgnoringRangeWithSmallFileStillWorks() = runTest(dispatcher) {
        server.file = fixture.readBytes()
        server.ignoreRange = true

        val result = client.ggufHeader("a/b", "m.gguf") as HubResult.Success

        assertTrue(result.value is GgufReadResult.Read)
    }

    @Test
    fun serverIgnoringRangeWithLargeFileIsRefused() = runTest(dispatcher) {
        server.file = fixture.readBytes()
        server.ignoreRange = true
        server.refuseAboveLimit = true

        assertEquals(HubResult.Failure(HubError.RESPONSE_TOO_LARGE), client.ggufHeader("a/b", "m.gguf"))
    }

    @Test
    fun missingContentRangeIsMalformed() = runTest(dispatcher) {
        server.file = fixture.readBytes()
        server.omitContentRange = true

        assertEquals(HubResult.Failure(HubError.MALFORMED_RESPONSE), client.ggufHeader("a/b", "m.gguf"))
    }

    @Test
    fun unexpectedSuccessStatusIsMalformed() = runTest(dispatcher) {
        server.file = fixture.readBytes()
        server.statusOverride = 204

        assertEquals(HubResult.Failure(HubError.MALFORMED_RESPONSE), client.ggufHeader("a/b", "m.gguf"))
    }

    @Test
    fun nonGgufAndTruncatedFilesAreReportedAsGgufFailures() = runTest(dispatcher) {
        server.file = "PK\u0003\u0004 not a model".toByteArray()
        assertEquals(HubResult.Success(GgufReadResult.Failed(GgufError.NOT_GGUF)), client.ggufHeader("a/b", "m.gguf"))

        server.file = fixture.readBytes().copyOf(1024)
        assertEquals(HubResult.Success(GgufReadResult.Failed(GgufError.MALFORMED)), client.ggufHeader("a/b", "m.gguf"))
    }

    @Test
    fun pathSegmentsAreEncoded() = runTest(dispatcher) {
        server.file = fixture.readBytes()

        client.ggufHeader("a/b", "sub dir/Model Q4.gguf", revision = "v1.0")

        assertEquals("https://huggingface.co/a/b/resolve/v1.0/sub%20dir/Model%20Q4.gguf", server.urls.single())
    }

    @Test
    fun networkAndHttpFailuresAreHubFailures() = runTest(dispatcher) {
        allowed = false
        assertEquals(HubResult.Failure(HubError.NETWORK_DISABLED), client.ggufHeader("a/b", "m.gguf"))
        allowed = true
        server.statusOverride = 401
        assertEquals(HubResult.Failure(HubError.UNAUTHORISED), client.ggufHeader("a/b", "m.gguf"))
        server.statusOverride = null
        server.failWith = IOException("offline")
        assertEquals(HubResult.Failure(HubError.OFFLINE), client.ggufHeader("a/b", "m.gguf"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun onlyGgufPathsAreFetched() = runTest(dispatcher) {
        client.ggufHeader("a/b", "../secrets.txt")
    }

    /** A key/value whose string value is far longer than any range, so the header never ends. */
    private fun longStringKey(): ByteArray = java.io.ByteArrayOutputStream().apply {
        write(byteArrayOf(4, 0, 0, 0, 0, 0, 0, 0))
        write("name".toByteArray())
        write(byteArrayOf(8, 0, 0, 0))
        write(java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(9L * 1024 * 1024 * 1024).array())
    }.toByteArray()

    /** Serves byte ranges of [file] the way the Hub's CDN does. */
    private class RangeServer : HttpTransport {
        var file = ByteArray(0)
        var claimedTotal: Long? = null
        var ignoreRange = false
        var refuseAboveLimit = false
        var omitContentRange = false
        var statusOverride: Int? = null
        var failWith: IOException? = null
        val ranges = mutableListOf<String>()
        val urls = mutableListOf<String>()

        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("not used")

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse? {
            urls += url
            failWith?.let { throw it }
            val range = headers.getValue("Range").also { ranges += it }
            statusOverride?.let { return HttpResponse(it, emptyMap(), ByteArray(0)) }
            if (ignoreRange) {
                return if (refuseAboveLimit) null else HttpResponse(200, emptyMap(), file)
            }
            val end = range.substringAfter('-').toInt()
            val body = file.copyOf(minOf(end + 1, file.size))
            val total = claimedTotal ?: file.size.toLong()
            val headersOut = if (omitContentRange) emptyMap() else mapOf("content-range" to "bytes 0-${body.size - 1}/$total")
            return HttpResponse(206, headersOut, body)
        }
    }
}
