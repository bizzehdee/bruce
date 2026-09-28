package com.bizzeh.bruce.huggingface

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** Robolectric supplies Android's org.json implementation. */
@RunWith(RobolectricTestRunner::class)
class HubClientTest {
    private val dispatcher = StandardTestDispatcher()
    private val transport = FakeTransport()
    private var allowed = true
    private val client = HubClient(transport, { allowed }, dispatcher, userAgent = "Bruce/test")

    private fun fixture(name: String) = javaClass.getResource("/huggingface/$name")!!.readText()

    private fun <T> HubResult<T>.value() = (this as HubResult.Success).value

    @Test
    fun searchParsesArchivedHubResponse() = runTest(dispatcher) {
        transport.respond(200, fixture("search-expand.json"))

        val model = client.search("qwen3").value().single()

        assertEquals(
            HubModel(
                id = "ISTA-DASLab/Qwen3.8-27B-GSQ-RCO-GGUF",
                downloads = 0,
                gated = false,
                license = "apache-2.0",
                architecture = "qwen35",
                parameterCount = 26_895_998_464,
                contextLength = 262_144,
            ),
            model,
        )
    }

    @Test
    fun searchRequestsGgufSortedByDownloadsWithExpansions() = runTest(dispatcher) {
        transport.respond(200, "[]")

        client.search("  qwen3 coder & more ", limit = 5)

        assertEquals(
            "https://huggingface.co/api/models?search=qwen3+coder+%26+more&filter=gguf&sort=downloads&direction=-1" +
                "&limit=5&expand[]=downloads&expand[]=gated&expand[]=cardData&expand[]=gguf",
            transport.urls.single(),
        )
        assertEquals("Bruce/test", transport.headers.single()["User-Agent"])
    }

    @Test
    fun searchHandlesGatedLicenceListsAndMissingDetails() = runTest(dispatcher) {
        transport.respond(
            200,
            """[
              {"id":"a/gated-auto","gated":"auto","downloads":5,"cardData":{"license":["mit","apache-2.0"]}},
              {"id":"b/plain","gated":null,"cardData":{"license":""},"gguf":{"architecture":"","total":-1}},
              {"id":"c/none"}
            ]""",
        )

        val models = client.search("x").value()

        assertTrue(models[0].gated)
        assertEquals(5L, models[0].downloads)
        assertEquals("mit, apache-2.0", models[0].license)
        assertFalse(models[1].gated)
        assertNull(models[1].license)
        assertNull(models[1].architecture)
        assertNull(models[1].parameterCount)
        assertNull(models[2].license)
        assertNull(models[2].contextLength)
    }

    @Test
    fun ggufFilesListsOnlyGgufWithHashes() = runTest(dispatcher) {
        transport.respond(200, fixture("tree-qwen3-0.6b.json"))

        val files = client.ggufFiles("ggml-org/Qwen3-0.6B-GGUF").value()

        assertEquals(
            listOf("Qwen3-0.6B-BF16.gguf", "Qwen3-0.6B-Q4_0.gguf", "Qwen3-0.6B-Q8_0.gguf", "Qwen3-0.6B-f16.gguf"),
            files.map { it.path },
        )
        val q4 = files.single { it.path == "Qwen3-0.6B-Q4_0.gguf" }
        assertEquals(428_970_080L, q4.sizeBytes)
        assertEquals("da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4", q4.sha256)
        assertEquals(
            "https://huggingface.co/api/models/ggml-org/Qwen3-0.6B-GGUF/tree/main?recursive=true",
            transport.urls.single(),
        )
    }

    @Test
    fun ggufFilesIncludesSubfolders() = runTest(dispatcher) {
        transport.respond(200, fixture("tree-recursive-models.json"))

        val files = client.ggufFiles("ggml-org/models").value()

        assertEquals(23, files.size)
        assertTrue(files.any { it.path == "tinyllamas/stories260K.gguf" })
    }

    @Test
    fun fileWithoutLfsHashHasNoSha() = runTest(dispatcher) {
        transport.respond(200, """[{"type":"file","path":"m.gguf","size":10,"lfs":{"oid":"not-a-hash"}},{"type":"file","path":"n.gguf","size":10}]""")

        assertEquals(listOf(null, null), client.ggufFiles("a/b").value().map { it.sha256 })
    }

    @Test
    fun unsafeOrInvalidEntriesMakeTheResponseMalformed() = runTest(dispatcher) {
        val bad = listOf(
            """[{"type":"file","path":"../escape.gguf","size":1}]""",
            """[{"type":"file","path":"m.gguf","size":-1}]""",
            """[{"id":"not a repo"}]""",
            """[42]""",
            """{"error":"not a list"}""",
            """not json""",
        )
        for (body in bad) {
            transport.respond(200, body)
            val result = if (body.contains("path")) client.ggufFiles("a/b") else client.search("x")
            assertEquals(body, HubResult.Failure(HubError.MALFORMED_RESPONSE), result)
        }
    }

    @Test
    fun networkModeIsCheckedBeforeAnyRequest() = runTest(dispatcher) {
        allowed = false

        assertEquals(HubResult.Failure(HubError.NETWORK_DISABLED), client.search("x"))
        assertTrue(transport.urls.isEmpty())
    }

    @Test
    fun transportFailuresAndStatusCodesMapToErrors() = runTest(dispatcher) {
        val cases = mapOf(
            401 to HubError.UNAUTHORISED,
            403 to HubError.UNAUTHORISED,
            404 to HubError.NOT_FOUND,
            429 to HubError.RATE_LIMITED,
            503 to HubError.SERVER_ERROR,
            302 to HubError.MALFORMED_RESPONSE,
        )
        for ((status, error) in cases) {
            transport.respond(status, "{}")
            assertEquals("status $status", HubResult.Failure(error), client.search("x"))
        }
        transport.failWith = IOException("no route")
        assertEquals(HubResult.Failure(HubError.OFFLINE), client.search("x"))
        transport.failWith = null
        transport.tooLarge = true
        assertEquals(HubResult.Failure(HubError.RESPONSE_TOO_LARGE), client.search("x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRepositoryIdIsACallerBug() = runTest(dispatcher) {
        client.ggufFiles("../../api/whoami")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRevisionIsACallerBug() = runTest(dispatcher) {
        client.ggufFiles("a/b", revision = "main/../x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun limitIsBounded() = runTest(dispatcher) {
        client.search("x", limit = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun baseUrlMustBeHttps() {
        HubClient(transport, { true }, dispatcher, "Bruce/test", baseUrl = "http://huggingface.co")
    }

    @Test
    fun repositoryIdsAndPaths() {
        assertTrue(HubClient.isRepositoryId("ggml-org/Qwen3-0.6B-GGUF"))
        assertFalse(HubClient.isRepositoryId("ggml-org"))
        assertFalse(HubClient.isRepositoryId("a/b/c"))
        assertFalse(HubClient.isRepositoryId("a/b..c"))
        assertTrue(HubClient.isSafePath("tinyllamas/stories260K.gguf"))
        listOf("", "/abs.gguf", "a/../b.gguf", "./a.gguf", "a//b.gguf", "a\\b.gguf", "x".repeat(513)).forEach {
            assertFalse(it, HubClient.isSafePath(it))
        }
    }

    private class FakeTransport : HttpTransport {
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        private var status = 200
        private var body = ""
        var failWith: IOException? = null
        var tooLarge = false

        fun respond(status: Int, body: String) {
            this.status = status
            this.body = body
        }

        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("not used")

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse? {
            urls += url
            this.headers += headers
            failWith?.let { throw it }
            if (tooLarge) return null
            return HttpResponse(status, emptyMap(), body.toByteArray())
        }
    }
}
