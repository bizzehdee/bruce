package com.bizzeh.bruce.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.HubClient
import com.bizzeh.bruce.huggingface.HubError
import com.bizzeh.bruce.huggingface.HubModel
import com.bizzeh.bruce.huggingface.ModelDownloader
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.ui.theme.BruceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = "w400dp-h2000dp")
@RunWith(RobolectricTestRunner::class)
class ModelBrowserTest {
    @get:Rule
    val temp = TemporaryFolder()

    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val stories = File("src/androidTest/assets/stories260K.gguf").readBytes()
    private val storiesSha = MessageDigest.getInstance("SHA-256").digest(stories).joinToString("") { "%02x".format(it) }
    private val hub = FakeHub()
    private var allowed = true
    private var downloaded = 0
    private val cpu = CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(modelsDir: File = temp.newFolder()) = ModelBrowserViewModel(
        hub = HubClient(hub, { allowed }, dispatcher, "Bruce/test"),
        downloader = ModelDownloader(hub, modelsDir, { allowed }, Dispatchers.IO, "Bruce/test"),
        device = { DeviceProfile(4L shl 30, cpu) },
        contextLength = { 4096 },
        onDownloaded = { downloaded++ },
    )

    private val qwen = HubModel("ggml-org/Qwen3-0.6B-GGUF", 1000, false, "apache-2.0", "qwen3", 596_000_000, 40_960)

    @Test
    fun searchShowsResultsOrErrors() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("  qwen3 ")
        vm.search()
        advanceUntilIdle()

        assertEquals(listOf("ISTA-DASLab/Qwen3.8-27B-GSQ-RCO-GGUF"), vm.state.value.results.map { it.id })
        assertTrue(hub.urls.last().contains("search=qwen3&"))

        allowed = false
        vm.search()
        advanceUntilIdle()
        assertEquals(HubError.NETWORK_DISABLED, vm.state.value.error)
    }

    @Test
    fun blankOrRepeatedSearchIsIgnored() = runTest(dispatcher) {
        val vm = viewModel()
        vm.search()
        vm.setQuery("x")
        vm.search()
        vm.search()
        advanceUntilIdle()

        assertEquals(1, hub.urls.size)
    }

    @Test
    fun openingARepositoryRanksItsFilesForThisPhone() = runTest(dispatcher) {
        val vm = viewModel()

        vm.openRepository(qwen)
        advanceUntilIdle()
        vm.openRepository(qwen)
        advanceUntilIdle()

        val ranked = vm.state.value.files.getValue(qwen.id).ranked
        assertEquals("Qwen3-0.6B-Q8_0.gguf", ranked.first().candidate.path)
        assertEquals("da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4", ranked.single { it.candidate.path == "Qwen3-0.6B-Q4_0.gguf" }.candidate.sha256)
        assertEquals("opened once, then cached", 1, hub.urls.size)
    }

    @Test
    fun repositoryErrorsAreShownAndRetried() = runTest(dispatcher) {
        val vm = viewModel()
        hub.treeStatus = 401

        vm.openRepository(qwen)
        advanceUntilIdle()
        assertEquals(HubError.UNAUTHORISED, vm.state.value.files.getValue(qwen.id).error)

        hub.treeStatus = 200
        vm.openRepository(qwen)
        advanceUntilIdle()
        assertNull(vm.state.value.files.getValue(qwen.id).error)
    }

    @Test
    fun downloadVerifiesAndReportsCompletion() = runTest(dispatcher) {
        val dir = temp.newFolder()
        val vm = viewModel(dir)
        val assessment = storiesAssessment()

        vm.download(qwen, assessment)
        vm.state.first { it.downloads.isEmpty() && downloaded == 1 }

        assertTrue(File(dir, "stories260K.gguf").isFile)
    }

    @Test
    fun secondDownloadOfTheSameFileWhileRunningIsIgnored() = runTest(dispatcher) {
        val vm = viewModel()
        val assessment = storiesAssessment()

        vm.download(qwen, assessment)
        vm.download(qwen, assessment)
        vm.state.first { it.downloads.isEmpty() && downloaded >= 1 }

        assertEquals(1, downloaded)
        assertEquals(1, hub.opens)
    }

    @Test
    fun failedDownloadKeepsItsErrorAndCancelClearsIt() = runTest(dispatcher) {
        val vm = viewModel()
        val corrupt = storiesAssessment().let { it.copy(candidate = it.candidate.copy(sha256 = "0".repeat(64))) }

        vm.download(qwen, corrupt)
        val failed = vm.state.first { it.downloads.values.any { d -> d.error != null } }
        assertEquals(DownloadError.VERIFICATION_FAILED, failed.downloads.values.single().error)

        vm.cancel(qwen.id, corrupt.candidate.path)
        assertTrue(vm.state.value.downloads.isEmpty())
        assertEquals(0, downloaded)
    }

    @Test
    fun paneSearchExpandDownloadAndProgress() {
        val calls = mutableListOf<String>()
        val actions = object : BrowseActions {
            override fun setQuery(query: String) { calls += "query $query" }
            override fun search() { calls += "search" }
            override fun openRepository(model: HubModel) { calls += "open ${model.id}" }
            override fun download(model: HubModel, assessment: Assessment) { calls += "download ${assessment.candidate.path}" }
            override fun cancel(repositoryId: String, path: String) { calls += "cancel $path" }
        }
        val assessment = storiesAssessment()
        val gated = qwen.copy(id = "meta/gated", gated = true, architecture = "clip")
        var state by androidx.compose.runtime.mutableStateOf(BrowseState(query = "q", searched = true, results = listOf(qwen, gated)))
        compose.setContent { BruceTheme { BrowsePane(state, actions) } }

        compose.onNodeWithTag("browseQuery").performTextInput("w")
        compose.onNodeWithTag("browseQuery").performImeAction()
        compose.onNodeWithTag("browseSearch").performClick()
        compose.onNodeWithText("Gated").assertIsDisplayed()
        compose.onNodeWithText("Not supported").assertIsDisplayed()
        compose.onNodeWithTag("repo:${qwen.id}").performClick()
        state = state.copy(files = mapOf(qwen.id to RepositoryFiles(loading = false, ranked = listOf(assessment))))
        compose.onNodeWithTag("download:stories260K.gguf").performClick()
        state = state.copy(downloads = mapOf(ModelBrowserViewModel.key(qwen.id, "stories260K.gguf") to DownloadState(500, 1000)))
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithTag("cancel:stories260K.gguf").performClick()
        state = state.copy(downloads = mapOf(ModelBrowserViewModel.key(qwen.id, "stories260K.gguf") to DownloadState(500, 1000, DownloadError.INTERRUPTED)))
        compose.onNodeWithTag("retry:stories260K.gguf").performClick()

        // Typing into a static test state inserts at the cursor, which is at the start; clearing
        // focus then re-reports the unchanged value, so query calls are checked separately.
        assertEquals("query wq", calls.first())
        assertEquals(
            listOf("search", "search", "open ${qwen.id}", "download stories260K.gguf", "cancel stories260K.gguf", "download stories260K.gguf"),
            calls.filterNot { it.startsWith("query") },
        )
    }

    @Test
    fun paneErrorsAndEmptyResults() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        val tooBig = storiesAssessment().copy(fit = Fit.DOES_NOT_FIT)
        compose.setContent {
            BruceTheme {
                androidx.compose.foundation.layout.Column {
                    BrowsePane(BrowseState(searched = true, error = HubError.NETWORK_DISABLED), actions)
                    BrowsePane(BrowseState(searched = true), actions)
                }
            }
        }

        compose.onNodeWithText("Allow Hugging Face in Settings → Network to search and download.").assertIsDisplayed()
        compose.onNodeWithText("No GGUF models found.").assertIsDisplayed()
        assertEquals(com.bizzeh.bruce.R.string.hub_error_unauthorised, BrowseText.hubError(HubError.UNAUTHORISED))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_rate_limited, BrowseText.hubError(HubError.RATE_LIMITED))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_offline, BrowseText.hubError(HubError.OFFLINE))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_not_found, BrowseText.hubError(HubError.NOT_FOUND))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_other, BrowseText.hubError(HubError.SERVER_ERROR))
        assertEquals("qwen3 · 596.0M parameters · 1.0K downloads · apache-2.0", BrowseText.summary(qwen))
        assertFalse(tooBig.fit == Fit.FITS)
    }

    @Test
    fun oversizedFilesCannotBeDownloaded() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = error("must not be called")
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        val tooBig = storiesAssessment().copy(fit = Fit.DOES_NOT_FIT)
        compose.setContent {
            BruceTheme { BrowsePane(BrowseState(results = listOf(qwen), files = mapOf(qwen.id to RepositoryFiles(false, listOf(tooBig)))), actions) }
        }

        compose.onNodeWithTag("repo:${qwen.id}").performClick()
        compose.onNodeWithTag("download:stories260K.gguf").assertIsNotEnabled()
    }

    private fun storiesAssessment() = ModelFit.assess(
        Candidate(qwen.id, "stories260K.gguf", stories.size.toLong(), false, "llama", 292_800, sha256 = storiesSha),
        DeviceProfile(4L shl 30, cpu),
        2048,
    )

    /** Replays archived Hub responses and serves the test model's bytes for downloads. */
    private inner class FakeHub : HttpTransport {
        val urls = mutableListOf<String>()
        var treeStatus = 200

        private fun resource(name: String) = javaClass.getResource("/huggingface/$name")!!.readBytes()

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse {
            urls += url
            return when {
                "/tree/" in url -> HttpResponse(treeStatus, emptyMap(), if (treeStatus == 200) resource("tree-qwen3-0.6b.json") else ByteArray(0))
                else -> HttpResponse(200, emptyMap(), resource("search-expand.json"))
            }
        }

        var opens = 0

        override fun open(url: String, headers: Map<String, String>): StreamingResponse {
            opens++
            return StreamingResponse(200, emptyMap(), ByteArrayInputStream(stories)) {}
        }

        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
    }
}
