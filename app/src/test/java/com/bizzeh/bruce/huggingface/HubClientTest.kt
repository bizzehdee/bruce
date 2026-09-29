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
            model.copy(chatTemplate = null, bosToken = null, eosToken = null),
        )
        assertTrue("the archived response carries the template", model.chatTemplate!!.isNotEmpty())
    }

    @Test
    fun searchKeepsTheReportedChatTemplateUnlessItIsHuge() = runTest(dispatcher) {
        transport.respond(
            200,
            """[
              {"id":"a/tools","gguf":{"chat_template":"{{ tools }}","bos_token":"<s>","eos_token":"</s>"}},
              {"id":"b/huge","gguf":{"chat_template":"${"x".repeat(70_000)}"}},
              {"id":"c/blank","gguf":{"chat_template":"","bos_token":"","eos_token":""}}
            ]""",
        )

        val models = client.search("x").value()

        assertEquals(Triple("{{ tools }}", "<s>", "</s>"), Triple(models[0].chatTemplate, models[0].bosToken, models[0].eosToken))
        assertNull(models[1].chatTemplate)
        assertNull(models[2].chatTemplate)
        assertNull(models[2].bosToken)
    }

    @Test
    fun searchRequestsTextGenerationGgufSortedByDownloadsWithExpansions() = runTest(dispatcher) {
        transport.respond(200, "[]")

        client.search("  qwen3 coder & more ", limit = 5)

        assertEquals(
            "https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=5&search=qwen3+coder+%26+more" +
                "&pipeline_tag=text-generation" + EXPANSIONS,
            transport.urls.single(),
        )
        assertEquals("Bruce/test", transport.headers.single()["User-Agent"])
    }

    @Test
    fun blankSearchListsWithoutANameAndAppliesParameterBounds() = runTest(dispatcher) {
        transport.respond(200, "[]")

        client.search(" ", limit = 100, HubSearchFilter(minParameters = 1_000_000_000, maxParameters = 3_000_000_000))
        client.search("", filter = HubSearchFilter(textGeneration = false, maxParameters = 500))
        client.search("", filter = HubSearchFilter(textGeneration = false))

        assertEquals(
            listOf(
                "https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=100&pipeline_tag=text-generation" +
                    "&num_parameters=min%3A1000000000%2Cmax%3A3000000000" + EXPANSIONS,
                "https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=20&num_parameters=max%3A500" + EXPANSIONS,
                "https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=20" + EXPANSIONS,
            ),
            transport.urls,
        )
    }

    @Test
    fun searchListsOnlyGgufFileNamesFromArchivedResponse() = runTest(dispatcher) {
        transport.respond(200, fixture("search-siblings.json"))

        val model = client.search("").value().single()

        assertEquals("Qwen/Qwen3-0.6B-GGUF", model.id)
        assertEquals(listOf("Qwen3-0.6B-Q8_0.gguf"), model.files)
        assertEquals(596_049_920L, model.parameterCount)
    }

    @Test
    fun unsafeFileNameInSearchIsMalformed() = runTest(dispatcher) {
        transport.respond(200, """[{"id":"a/b","siblings":[{"rfilename":"../evil.gguf"}]}]""")

        assertEquals(HubResult.Failure(HubError.MALFORMED_RESPONSE), client.search("x"))
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
    fun signedInRequestsCarryTheToken() = runTest(dispatcher) {
        val signedIn = HubClient(transport, { true }, dispatcher, "Bruce/test", token = { "tok" })
        transport.respond(200, "[]")

        signedIn.search("x")
        client.search("x")

        assertEquals("Bearer tok", transport.headers[0]["Authorization"])
        assertNull(transport.headers[1]["Authorization"])
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
    fun limitHasAnUpperBound() = runTest(dispatcher) {
        client.search("x", limit = HubClient.MAX_RESULTS + 1)
    }

    @Test
    fun nonObjectFileEntryIsMalformed() = runTest(dispatcher) {
        transport.respond(200, "[42]")

        assertEquals(HubResult.Failure(HubError.MALFORMED_RESPONSE), client.ggufFiles("a/b"))
    }

    @Test
    fun licenceListOfBlanksIsNoLicence() = runTest(dispatcher) {
        transport.respond(200, """[{"id":"a/b","cardData":{"license":["", " "]}},{"id":"c/d","cardData":{"license":42}}]""")

        assertEquals(listOf<String?>(null, null), client.search("x").let { (it as HubResult.Success).value.map(HubModel::license) })
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

        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse? {
            urls += url
            this.headers += headers
            failWith?.let { throw it }
            if (tooLarge) return null
            return HttpResponse(status, emptyMap(), body.toByteArray())
        }
    }

    private companion object {
        const val EXPANSIONS = "&expand[]=downloads&expand[]=gated&expand[]=cardData&expand[]=gguf&expand[]=siblings"
    }
}
