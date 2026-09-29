package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.HubClient
import com.bizzeh.bruce.huggingface.HubError
import com.bizzeh.bruce.huggingface.StreamingResponse
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Robolectric supplies Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class TemplateFinderTest {
    private val dispatcher = StandardTestDispatcher()
    private var body = "[]"
    private var allowed = true
    private val urls = mutableListOf<String>()
    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse {
            urls += url
            return HttpResponse(200, emptyMap(), body.toByteArray())
        }
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("not used")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
    }
    private val judged = mutableListOf<Triple<String, String?, String?>>()
    private val finder = TemplateFinder(HubClient(transport, { allowed }, dispatcher, "Bruce/test")) { template, bos, eos ->
        judged += Triple(template, bos, eos)
        "tools" in template
    }

    private val metadata = GgufMetadata(3, "llama", "Llama 3.2 1B Instruct", 1_235_814_432, 147, 131_072, 15, "Q4_K_M", 807_690_656)
    private val installed = InstalledModel(File("llama-3.2-1b-instruct-q4_k_m.gguf"), 807_690_656, metadata, ModelFit.assess(
        Candidate("local/model", "m.gguf", 807_690_656, false, "llama", 1_235_814_432, metadata), DeviceProfile(8L shl 30, cpu), 4096,
    ), ModelOverrides(), skills = false)

    private fun repo(id: String, template: String?, architecture: String = "llama", total: Long = 1_235_814_432) =
        """{"id":"$id","gguf":{"architecture":"$architecture","total":$total${template?.let { ",\"chat_template\":\"$it\",\"bos_token\":\"<s>\",\"eos_token\":\"</s>\"" }.orEmpty()}}}"""

    @Test
    fun takesTheMostDownloadedCopyOfTheSameModelWithAToolTemplate() = runTest(dispatcher) {
        body = "[" + listOf(
            repo("a/stripped", "{{ messages }}"),
            repo("b/other-size", "{{ tools }} big", total = 3_000_000_000),
            repo("c/other-arch", "{{ tools }} qwen", architecture = "qwen3"),
            repo("d/none", null),
            repo("e/full", "{{ tools }} meta"),
            repo("f/also", "{{ tools }} later"),
        ).joinToString(",") + "]"

        val found = finder.find(installed) as TemplateSearch.Found

        assertEquals(FetchedTemplate("{{ tools }} meta", "e/full"), found.template)
        assertTrue(urls.single().contains("&search=Llama-3.2-1B-Instruct"))
        assertEquals(Triple("{{ messages }}", "<s>", "</s>"), judged.first())
    }

    @Test
    fun nothingSuitableOrNoMetadataIsNotFound() = runTest(dispatcher) {
        body = "[" + repo("a/stripped", "{{ messages }}") + "]"
        assertEquals(TemplateSearch.NotFound, finder.find(installed))
        assertEquals(TemplateSearch.NotFound, finder.find(installed.copy(metadata = null)))
    }

    @Test
    fun hubFailuresAreReported() = runTest(dispatcher) {
        allowed = false
        assertEquals(TemplateSearch.Failed(HubError.NETWORK_DISABLED), finder.find(installed))
    }

    private companion object {
        val cpu = com.bizzeh.bruce.hardware.CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false)
    }
}
