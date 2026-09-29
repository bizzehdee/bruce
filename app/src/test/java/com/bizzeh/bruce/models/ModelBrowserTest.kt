package com.bizzeh.bruce.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    private val templatesJudged = mutableListOf<String>()

    private fun viewModel(modelsDir: File = temp.newFolder()) = ModelBrowserViewModel(
        hub = HubClient(hub, { allowed }, dispatcher, "Bruce/test"),
        downloader = ModelDownloader(hub, modelsDir, { allowed }, Dispatchers.IO, "Bruce/test"),
        device = { DeviceProfile(4L shl 30, cpu) },
        contextLength = { 4096 },
        onDownloaded = { downloaded++ },
        templateSupportsTools = { template, _, _ -> templatesJudged += template; "tools" in template },
        checkDispatcher = dispatcher,
    )

    private val qwen = HubModel("ggml-org/Qwen3-0.6B-GGUF", 1000, false, "apache-2.0", "qwen3", 596_000_000, 40_960)

    @Test
    fun templatesAreJudgedOncePerTemplateAndMarkTheListings() = runTest(dispatcher) {
        hub.searchBody = """[
          {"id":"a/full","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":500000000,"architecture":"qwen3","chat_template":"{{ tools }}"}},
          {"id":"b/stripped","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":500000000,"architecture":"qwen3","chat_template":"{{ messages }}"}},
          {"id":"c/same","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":500000000,"architecture":"qwen3","chat_template":"{{ tools }}"}},
          {"id":"d/none","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":500000000,"architecture":"qwen3"}}
        ]"""
        val vm = viewModel()
        vm.setQuery("q")
        vm.search()
        advanceUntilIdle()

        assertEquals(listOf("a/full", "c/same", "d/none", "b/stripped"), vm.state.value.listings.map { it.model.id })
        assertEquals(listOf(true, true, null, false), vm.state.value.listings.map { it.skills })
        assertEquals(listOf("{{ tools }}", "{{ messages }}"), templatesJudged)
    }

    @Test
    fun searchShowsResultsOrErrors() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("  qwen3 ")
        vm.search()
        advanceUntilIdle()

        assertEquals(listOf("ISTA-DASLab/Qwen3.8-27B-GSQ-RCO-GGUF"), vm.state.value.results.map { it.id })
        assertTrue(hub.urls.last().contains("&search=qwen3&"))
        assertFalse(vm.state.value.recommended)
        assertTrue("a 27B model does not fit 4 GB", vm.state.value.listings.isEmpty())

        allowed = false
        vm.search()
        advanceUntilIdle()
        assertEquals(HubError.NETWORK_DISABLED, vm.state.value.error)
    }

    @Test
    fun searchWhileSearchingIsIgnored() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("x")
        vm.search()
        vm.search()
        advanceUntilIdle()

        assertEquals(1, hub.urls.size)
    }

    private val small = """{"id":"a/small","downloads":10,"gguf":{"architecture":"qwen3","total":596000000},
        "siblings":[{"rfilename":"m-Q4_0.gguf"},{"rfilename":"m-Q8_0.gguf"},{"rfilename":"mmproj-F16.gguf"},{"rfilename":"README.md"}]}"""
    private val big = """{"id":"b/big","downloads":99,"gguf":{"architecture":"qwen3","total":27000000000},"siblings":[{"rfilename":"b-Q4_K_M.gguf"}]}"""
    private val unknown = """{"id":"c/unknown","downloads":50,"siblings":[{"rfilename":"c-Q4_K_M.gguf"}]}"""

    @Test
    fun recommendationsAskOnceAndListWhatFitsWithTheBestFile() = runTest(dispatcher) {
        hub.searchBody = "[$big,$unknown,$small]"
        val vm = viewModel()

        vm.recommend()
        advanceUntilIdle()
        vm.recommend()
        advanceUntilIdle()

        assertEquals(1, hub.urls.size)
        val url = hub.urls.single()
        assertFalse(url.contains("search="))
        assertTrue(url.contains("&pipeline_tag=text-generation&num_parameters=max%3A${(4L shl 30) * 4}&"))
        val state = vm.state.value
        assertTrue(state.recommended)
        assertEquals(listOf("a/small"), state.listings.map { it.model.id })
        val best = state.listings.single().best!!
        assertEquals("m-Q8_0.gguf", best.candidate.path)
        assertEquals((596_000_000 * 8.52 / 8 * 1.05).toLong(), best.candidate.sizeBytes)
    }

    @Test
    fun phoneSideFiltersRelistWithoutAskingAgain() = runTest(dispatcher) {
        hub.searchBody = "[$big,$unknown,$small]"
        val vm = viewModel()
        vm.recommend()
        advanceUntilIdle()

        vm.setFilters(BrowseFilters(runs = RunsFilter.ANY))
        advanceUntilIdle()
        assertEquals("fits first, then the Hub's order", listOf("a/small", "b/big", "c/unknown"), vm.state.value.listings.map { it.model.id })

        vm.setFilters(BrowseFilters(runs = RunsFilter.ANY, size = SizeBucket.FROM_1GB_TO_2GB))
        advanceUntilIdle()
        assertTrue(vm.state.value.listings.isEmpty())
        assertEquals(1, hub.urls.size)
    }

    @Test
    fun hubSideFiltersAskAgainInTheCurrentMode() = runTest(dispatcher) {
        val vm = viewModel()
        vm.recommend()
        advanceUntilIdle()

        vm.setFilters(BrowseFilters(parameters = ParameterBucket.UNDER_1B, runs = RunsFilter.ANY))
        advanceUntilIdle()
        vm.setQuery("qwen")
        vm.search()
        advanceUntilIdle()
        vm.setFilters(BrowseFilters(parameters = ParameterBucket.UNDER_1B, runs = RunsFilter.ANY, textGeneration = false))
        advanceUntilIdle()

        assertEquals(4, hub.urls.size)
        assertTrue(hub.urls[1].contains("&pipeline_tag=text-generation&num_parameters=max%3A999999999&"))
        assertFalse(hub.urls[1].contains("search="))
        assertTrue(hub.urls[3].contains("&search=qwen&num_parameters=max%3A999999999&"))
        assertFalse(vm.state.value.recommended)
    }

    @Test
    fun blankSearchGoesBackToRecommendations() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("qwen")
        vm.search()
        advanceUntilIdle()
        vm.setQuery("  ")
        vm.search()
        advanceUntilIdle()

        assertTrue(vm.state.value.recommended)
        assertFalse(hub.urls.last().contains("search="))
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
            override fun recommend() { calls += "recommend" }
            override fun setFilters(filters: BrowseFilters) { calls += "filters $filters" }
            override fun openRepository(model: HubModel) { calls += "open ${model.id}" }
            override fun download(model: HubModel, assessment: Assessment) { calls += "download ${assessment.candidate.path}" }
            override fun cancel(repositoryId: String, path: String) { calls += "cancel $path" }
        }
        val assessment = storiesAssessment()
        val gated = qwen.copy(id = "meta/gated", gated = true, architecture = "clip")
        var state by androidx.compose.runtime.mutableStateOf(
            BrowseState(query = "q", searched = true, recommended = false, results = listOf(qwen, gated), listings = listOf(Listing(qwen, assessment), Listing(gated, null))),
        )
        compose.setContent { BruceTheme { BrowsePane(state, actions) } }

        compose.onNodeWithTag("browseQuery").performTextInput("w")
        compose.onNodeWithTag("browseQuery").performImeAction()
        compose.onNodeWithTag("browseSearch").performClick()
        compose.onNodeWithText("Gated").assertIsDisplayed()
        compose.onNodeWithTag("best:${qwen.id}", useUnmergedTree = true).assertExists()
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
        assertEquals("query wq", calls.first { it.startsWith("query") })
        assertEquals(
            listOf("recommend", "search", "search", "open ${qwen.id}", "download stories260K.gguf", "cancel stories260K.gguf", "download stories260K.gguf"),
            calls.filterNot { it.startsWith("query") },
        )
    }

    @Test
    fun paneMarksListingsWithLimitedSkillUse() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        val stripped = qwen.copy(id = "x/stripped")
        compose.setContent {
            BruceTheme {
                BrowsePane(BrowseState(searched = true, results = listOf(qwen, stripped), listings = listOf(Listing(qwen, null, true), Listing(stripped, null, false))), actions)
            }
        }

        compose.onNodeWithTag("limitedSkills:x/stripped", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("limitedSkills:${qwen.id}", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun paneErrorsAndEmptyResults() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
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
        compose.onNodeWithText("Couldn't sniff out a match. Try other filters.").assertIsDisplayed()
        assertEquals(com.bizzeh.bruce.R.string.hub_error_unauthorised, BrowseText.hubError(HubError.UNAUTHORISED))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_rate_limited, BrowseText.hubError(HubError.RATE_LIMITED))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_offline, BrowseText.hubError(HubError.OFFLINE))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_not_found, BrowseText.hubError(HubError.NOT_FOUND))
        assertEquals(com.bizzeh.bruce.R.string.hub_error_other, BrowseText.hubError(HubError.SERVER_ERROR))
        assertEquals("qwen3 · 596.0M parameters · 1.0K downloads · apache-2.0", BrowseText.summary(qwen))
        assertFalse(tooBig.fit == Fit.FITS)
    }

    @Test
    fun recommendationsSaySoWhileLoadingButSearchesDoNot() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        var state by mutableStateOf(BrowseState(searching = true, recommended = true))
        compose.setContent { BruceTheme { BrowsePane(state, actions) } }

        compose.onNodeWithText("Sniffing out models for this phone…").assertIsDisplayed()
        state = BrowseState(searching = true, recommended = false)
        compose.onNodeWithText("Sniffing out models for this phone…").assertDoesNotExist()
    }

    @Test
    fun oversizedFilesCannotBeDownloaded() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = error("must not be called")
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        val tooBig = storiesAssessment().copy(fit = Fit.DOES_NOT_FIT)
        compose.setContent {
            BruceTheme { BrowsePane(BrowseState(results = listOf(qwen), listings = listOf(Listing(qwen, null)), files = mapOf(qwen.id to RepositoryFiles(false, listOf(tooBig)))), actions) }
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
        var searchBody: String? = null

        private fun resource(name: String) = javaClass.getResource("/huggingface/$name")!!.readBytes()

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse {
            urls += url
            return when {
                "/tree/" in url -> HttpResponse(treeStatus, emptyMap(), if (treeStatus == 200) resource("tree-qwen3-0.6b.json") else ByteArray(0))
                else -> HttpResponse(200, emptyMap(), searchBody?.toByteArray() ?: resource("search-expand.json"))
            }
        }

        var opens = 0

        override fun open(url: String, headers: Map<String, String>): StreamingResponse {
            opens++
            return StreamingResponse(200, emptyMap(), ByteArrayInputStream(stories)) {}
        }

        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
    }

    @Test
    fun modelsScreenCanOpenOnTheHuggingFaceTab() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        val models = object : ModelsActions {
            override fun choose(file: java.io.File) = Unit
            override fun importModel() = Unit
            override fun delete(file: java.io.File) = Unit
            override fun setOverrides(file: java.io.File, overrides: ModelOverrides) = Unit
        }
        compose.setContent { BruceTheme { ModelsScreen(ModelsState(), models, {}, browse = BrowseState(), browseActions = actions, startOnHuggingFace = true) } }

        compose.onNodeWithTag("browseQuery").assertIsDisplayed()
    }

    @Test
    fun paneShowsRecommendationsAndSetsFilters() {
        val calls = mutableListOf<String>()
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() { calls += "recommend" }
            override fun setFilters(filters: BrowseFilters) { calls += "$filters" }
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        compose.setContent { BruceTheme { BrowsePane(BrowseState(searched = true, listings = listOf(Listing(qwen, storiesAssessment()))), actions) } }

        compose.onNodeWithTag("browseRecommended").assertIsDisplayed()
        compose.onNodeWithTag("parameters:FROM_1B_TO_3B").performClick()
        compose.onNodeWithTag("size:UNDER_1GB").performClick()
        compose.onNodeWithTag("runs:FITS").performClick()
        compose.onNodeWithTag("task:false").performClick()
        compose.onNodeWithTag("parameters:any").performClick()

        assertEquals(
            listOf(
                "recommend",
                "${BrowseFilters(parameters = ParameterBucket.FROM_1B_TO_3B)}",
                "${BrowseFilters(size = SizeBucket.UNDER_1GB)}",
                "${BrowseFilters(runs = RunsFilter.FITS)}",
                "${BrowseFilters(textGeneration = false)}",
                "${BrowseFilters()}",
            ),
            calls,
        )
    }

    @Test
    fun filterLabels() {
        assertEquals(listOf("< 1B", "1–3B", "3–8B", "8–14B", "14B+"), ParameterBucket.entries.map(BrowseText::parameterLabel))
        assertEquals(listOf("< 1 GB", "1–2 GB", "2–4 GB", "4–8 GB", "8 GB+"), SizeBucket.entries.map(BrowseText::sizeLabel))
        assertNull(BrowseText.parameterLabel(null))
        assertNull(BrowseText.sizeLabel(null))
    }
}
