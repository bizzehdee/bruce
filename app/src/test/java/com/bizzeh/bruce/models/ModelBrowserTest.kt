package com.bizzeh.bruce.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
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
import com.bizzeh.bruce.huggingface.HubTask
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

    private var usableMemory = 4L shl 30

    private fun viewModel(modelsDir: File = temp.newFolder()) = ModelBrowserViewModel(
        hub = HubClient(hub, { allowed }, dispatcher, "Bruce/test"),
        downloader = ModelDownloader(hub, modelsDir, { allowed }, Dispatchers.IO, "Bruce/test"),
        device = { DeviceProfile(usableMemory, cpu) },
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

    private val copies = """[
      {"id":"unsloth/Qwen3.5-0.8B-GGUF","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":752393024,"architecture":"qwen35"}},
      {"id":"Hcompany/Holo-35B-GGUF","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":34660610688,"architecture":"qwen35"}}
    ]"""

    @Test
    fun aModelWithoutGgufFilesListsItsGgufCopies() = runTest(dispatcher) {
        hub.modelBody = """{"id":"Qwen/Qwen3.5-0.8B","siblings":[{"rfilename":"model.safetensors"}]}"""
        hub.searchBody = copies
        val vm = viewModel()
        vm.setFilters(BrowseFilters(task = HubTask.TEXT_GENERATION))
        advanceUntilIdle()
        hub.urls.clear()
        vm.setQuery("https://huggingface.co/qwen/qwen3.5-0.8b/tree/main")
        vm.search()
        advanceUntilIdle()

        assertEquals("Qwen/Qwen3.5-0.8B", vm.state.value.copiesOf)
        assertEquals(listOf("unsloth/Qwen3.5-0.8B-GGUF"), vm.state.value.listings.map { it.model.id })
        assertTrue(hub.urls[0], hub.urls[0].startsWith("https://huggingface.co/api/models/qwen/qwen3.5-0.8b?"))
        assertTrue(hub.urls[1], "filter=base_model%3Aquantized%3AQwen%2FQwen3.5-0.8B" in hub.urls[1])
        assertFalse("copies are listed whatever their task", "pipeline_tag" in hub.urls[1])
        assertFalse("&search=" in hub.urls[1])

        vm.setQuery("qwen3")
        vm.search()
        advanceUntilIdle()

        assertNull(vm.state.value.copiesOf)
    }

    @Test
    fun aNamedRepositoryWithGgufFilesIsShownItself() = runTest(dispatcher) {
        hub.modelBody = """{"id":"unsloth/Qwen3.5-0.8B-GGUF","siblings":[{"rfilename":"m-Q4_0.gguf"}],"gguf":{"total":752393024,"architecture":"qwen35"}}"""
        val vm = viewModel()
        vm.setQuery("unsloth/Qwen3.5-0.8B-GGUF")
        vm.search()
        advanceUntilIdle()

        assertNull(vm.state.value.copiesOf)
        assertEquals(listOf("unsloth/Qwen3.5-0.8B-GGUF"), vm.state.value.listings.map { it.model.id })
        assertEquals(1, hub.urls.size)
    }

    @Test
    fun aNameTheHubDoesNotKnowIsSearchedFor() = runTest(dispatcher) {
        hub.modelStatus = 401
        hub.searchBody = copies
        val vm = viewModel()
        vm.setQuery("unsloth/qwen")
        vm.search()
        advanceUntilIdle()

        assertNull(vm.state.value.copiesOf)
        assertNull(vm.state.value.error)
        assertTrue(hub.urls[1], "&search=unsloth%2Fqwen" in hub.urls[1])
        assertEquals(listOf("unsloth/Qwen3.5-0.8B-GGUF"), vm.state.value.listings.map { it.model.id })
    }

    @Test
    fun aFailedSearchForCopiesShowsTheError() = runTest(dispatcher) {
        hub.modelBody = """{"id":"Qwen/Qwen3.5-0.8B","siblings":[]}"""
        hub.searchBody = "not json"
        val vm = viewModel()
        vm.setQuery("Qwen/Qwen3.5-0.8B")
        vm.search()
        advanceUntilIdle()

        assertEquals(HubError.MALFORMED_RESPONSE, vm.state.value.error)
        assertNull(vm.state.value.copiesOf)
    }

    private fun recordingActions(calls: MutableList<String>) = object : BrowseActions {
        override fun setQuery(query: String) { calls += "query $query" }
        override fun search() { calls += "search" }
        override fun recommend() = Unit
        override fun setFilters(filters: BrowseFilters) = Unit
        override fun openRepository(model: HubModel) = Unit
        override fun download(model: HubModel, assessment: Assessment) = Unit
        override fun cancel(repositoryId: String, path: String) = Unit
    }

    @Test
    fun paneSaysTheModelCannotRunAndShowsItsCopies() {
        val state = BrowseState(
            searched = true, recommended = false, copiesOf = "Qwen/Qwen3.5-0.8B",
            results = listOf(qwen), listings = listOf(Listing(qwen, storiesAssessment())),
        )
        compose.setContent { BruceTheme { BrowsePane(state, recordingActions(mutableListOf())) } }

        compose.onNodeWithTag("browseCopies").assertTextEquals("Bruce can't run Qwen/Qwen3.5-0.8B as it is published. These are copies of it in a format Bruce can run.")
        compose.onNodeWithTag("repo:${qwen.id}").assertIsDisplayed()
        compose.onNodeWithTag("browseNoCopies").assertDoesNotExist()
    }

    @Test
    fun paneOffersANameSearchWhenThereAreNoCopies() {
        val calls = mutableListOf<String>()
        compose.setContent { BruceTheme { BrowsePane(BrowseState(searched = true, recommended = false, copiesOf = "Qwen/Qwen3.5-0.8B"), recordingActions(calls)) } }

        compose.onNodeWithTag("browseNoCopies").assertTextEquals("Bruce can't run Qwen/Qwen3.5-0.8B as it is published, and Hugging Face lists no copy in a format Bruce can run.")
        compose.onNodeWithTag("browseCopies").assertDoesNotExist()
        compose.onNodeWithTag("browseSearchName").performClick()

        assertEquals(listOf("query Qwen3.5-0.8B", "search"), calls)
    }

    @Test
    fun paneSaysWhenFiltersHideEveryCopy() {
        val state = BrowseState(searched = true, recommended = false, copiesOf = "Qwen/Qwen3.5-0.8B", results = listOf(qwen))
        compose.setContent { BruceTheme { BrowsePane(state, recordingActions(mutableListOf())) } }

        compose.onNodeWithTag("browseCopies").assertIsDisplayed()
        compose.onNodeWithText("Couldn't sniff out a match. Try other filters.").assertIsDisplayed()
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
        assertFalse("every task by default", url.contains("pipeline_tag"))
        assertTrue(url.contains("&num_parameters=max%3A${(4L shl 30) * 4}&"))
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
        vm.setFilters(BrowseFilters(parameters = ParameterBucket.UNDER_1B, runs = RunsFilter.ANY, task = HubTask.IMAGE_TEXT_TO_TEXT))
        advanceUntilIdle()

        assertEquals(4, hub.urls.size)
        assertTrue(hub.urls[1].contains("&limit=100&num_parameters=max%3A999999999&"))
        assertFalse(hub.urls[1].contains("search="))
        assertTrue(hub.urls[3].contains("&search=qwen&pipeline_tag=image-text-to-text&num_parameters=max%3A999999999&"))
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

    private fun treeEntry(path: String, size: Long, sha: String) = """{"type":"file","size":$size,"lfs":{"oid":"$sha","size":$size},"path":"$path"}"""

    @Test
    fun aVisionModelsProjectorIsDownloadedWithItAndAddedOnRetry() = runTest(dispatcher) {
        val dir = temp.newFolder()
        val vm = viewModel(dir)
        val size = stories.size.toLong()
        fun tree(projectorSha: String) = "[" + listOf(
            treeEntry("stories-Q8_0.gguf", size, storiesSha),
            treeEntry("mmproj-Q8_0.gguf", size - 1, storiesSha),
            treeEntry("mmproj-model-f16.gguf", size, projectorSha),
        ).joinToString(",") + "]"
        hub.treeBody = tree("0".repeat(64))

        vm.openRepository(qwen)
        advanceUntilIdle()
        val files = vm.state.value.files.getValue(qwen.id)
        assertEquals("mmproj-model-f16.gguf", files.projector?.path)
        val assessment = files.ranked.single()
        assertEquals(size, assessment.candidate.projectorBytes)
        assertEquals(size, assessment.estimate.projectorBytes)

        vm.download(qwen, assessment)
        val failed = vm.state.first { it.downloads.values.any { d -> d.error != null } }.downloads.values.single()
        assertEquals(DownloadError.VERIFICATION_FAILED, failed.error)
        assertTrue("the model is kept", failed.modelInstalled)
        assertTrue(File(dir, "stories-Q8_0.gguf").isFile)
        assertEquals(1, downloaded)

        hub.treeBody = tree(storiesSha)
        val retry = viewModel(dir)
        retry.openRepository(qwen)
        advanceUntilIdle()
        val opens = hub.opens
        retry.download(qwen, retry.state.value.files.getValue(qwen.id).ranked.single())
        retry.state.first { it.downloads.isEmpty() && downloaded == 2 }

        assertEquals("only the projector is fetched again", opens + 1, hub.opens)
        assertEquals(listOf("stories-Q8_0.gguf"), dir.listFiles { f -> f.isFile && !f.name.startsWith(".") }!!.map { it.name })
        assertTrue(File(dir, "projectors/stories-Q8_0.gguf").isFile)
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
    fun aNamedFileIsLookedUpThenDownloadedAndVerified() = runTest(dispatcher) {
        hub.modelBody = """{"id":"a/stories","siblings":[{"rfilename":"stories-Q8_0.gguf"}],"gguf":{"total":260000,"architecture":"llama"}}"""
        hub.treeBody = "[" + treeEntry("stories-Q8_0.gguf", stories.size.toLong(), storiesSha) + "]"
        val dir = temp.newFolder()
        val vm = viewModel(dir)

        vm.downloadFile("a/stories", "stories-Q8_0.gguf")
        vm.downloadFile("a/stories", "stories-Q8_0.gguf")
        assertEquals(DownloadState(0, 0), vm.state.value.downloads.getValue("a/stories/stories-Q8_0.gguf"))
        vm.state.first { it.downloads.isEmpty() && downloaded == 1 }

        assertTrue(File(dir, "stories-Q8_0.gguf").isFile)
        assertEquals(1, hub.opens)
    }

    @Test
    fun aNamedFileTheHubCannotFindFailsWithoutDownloading() = runTest(dispatcher) {
        val vm = viewModel()
        hub.modelStatus = 401
        vm.downloadFile("a/gated", "m-Q4_0.gguf")
        advanceUntilIdle()
        assertEquals(DownloadError.UNAUTHORISED, vm.state.value.downloads.getValue("a/gated/m-Q4_0.gguf").error)

        hub.modelStatus = 200
        hub.modelBody = """{"id":"a/stories","siblings":[{"rfilename":"other-Q8_0.gguf"}],"gguf":{"total":260000,"architecture":"llama"}}"""
        hub.treeBody = "[" + treeEntry("other-Q8_0.gguf", stories.size.toLong(), storiesSha) + "]"
        vm.downloadFile("a/stories", "stories-Q8_0.gguf")
        advanceUntilIdle()
        assertEquals(DownloadError.NOT_FOUND, vm.state.value.downloads.getValue("a/stories/stories-Q8_0.gguf").error)

        hub.treeStatus = 500
        vm.downloadFile("a/stories", "other-Q8_0.gguf")
        advanceUntilIdle()
        assertEquals(DownloadError.SERVER_ERROR, vm.state.value.downloads.getValue("a/stories/other-Q8_0.gguf").error)
        assertEquals(0, hub.opens)
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
    fun theHeaderDecidesAndATooLargeModelIsNotDownloaded() = runTest(dispatcher) {
        usableMemory = 1000
        val vm = viewModel()

        vm.download(qwen, storiesAssessment())
        val refused = vm.state.first { it.downloads.values.any { d -> d.error != null } }.downloads.values.single()

        assertEquals(DownloadError.TOO_LARGE, refused.error)
        assertEquals(1000L, refused.freeBytes)
        assertTrue(refused.neededBytes!! > 1000)
        assertEquals(0, hub.opens)
        assertTrue(hub.urls.any { "/resolve/" in it })
    }

    @Test
    fun aFileThatFitsIsJudgedAgainFromItsHeaderAndDownloaded() = runTest(dispatcher) {
        val vm = viewModel()
        hub.treeStatus = 200
        vm.openRepository(qwen)
        advanceUntilIdle()
        val listed = vm.state.value.files.getValue(qwen.id).ranked.first()

        vm.download(qwen, listed)
        // The fake Hub serves stories260K for every file, so this download then fails verification; the judgement is what counts here.
        vm.state.first { state -> state.downloads.values.any { it.error != null } }

        val judged = vm.state.value.files.getValue(qwen.id).ranked.single { it.candidate.path == listed.candidate.path }
        assertTrue("judged from the header", judged.estimate.complete)
    }

    @Test
    fun aTooLargeRefusalSaysWhatIsNeeded() {
        val actions = object : BrowseActions {
            override fun setQuery(query: String) = Unit
            override fun search() = Unit
            override fun recommend() = Unit
            override fun setFilters(filters: BrowseFilters) = Unit
            override fun openRepository(model: HubModel) = Unit
            override fun download(model: HubModel, assessment: Assessment) = Unit
            override fun cancel(repositoryId: String, path: String) = Unit
        }
        val refused = DownloadState(0, 100, DownloadError.TOO_LARGE, neededBytes = 3L shl 30, freeBytes = 2L shl 30)
        compose.setContent {
            BruceTheme {
                BrowsePane(
                    BrowseState(
                        results = listOf(qwen), listings = listOf(Listing(qwen, null)),
                        files = mapOf(qwen.id to RepositoryFiles(false, listOf(storiesAssessment()))),
                        downloads = mapOf(ModelBrowserViewModel.key(qwen.id, "stories260K.gguf") to refused),
                    ),
                    actions,
                )
            }
        }

        compose.onNodeWithTag("repo:${qwen.id}").performClick()
        compose.onNodeWithTag("tooLarge:stories260K.gguf", useUnmergedTree = true).assertTextEquals("Not downloaded: this model needs about 3.00 GB of memory, and about 2.00 GB is free on this phone.")
    }

    @Test
    fun filesTooLargeBySizeAloneCanStillBeDownloaded() {
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
            BruceTheme { BrowsePane(BrowseState(results = listOf(qwen), listings = listOf(Listing(qwen, null)), files = mapOf(qwen.id to RepositoryFiles(false, listOf(tooBig)))), actions) }
        }

        compose.onNodeWithTag("repo:${qwen.id}").performClick()
        compose.onNodeWithTag("download:stories260K.gguf").assertIsEnabled()
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
        var treeBody: String? = null
        var modelStatus = 200
        var modelBody = ""

        private fun resource(name: String) = javaClass.getResource("/huggingface/$name")!!.readBytes()

        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse {
            urls += url
            return when {
                "/tree/" in url -> HttpResponse(treeStatus, emptyMap(), if (treeStatus == 200) treeBody?.toByteArray() ?: resource("tree-qwen3-0.6b.json") else ByteArray(0))
                // A GGUF header read: the start of the stories260K file.
                "/resolve/" in url -> HttpResponse(206, mapOf("content-range" to "bytes 0-${minOf(maxBytes, stories.size) - 1}/${stories.size}"), stories.copyOf(minOf(maxBytes, stories.size)))
                // One repository by name; the search is "/api/models?".
                "/api/models/" in url -> HttpResponse(modelStatus, emptyMap(), modelBody.toByteArray())
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
    fun theServersTabListsServersAndAddsOne() {
        val calls = mutableListOf<String>()
        val servers = object : com.bizzeh.bruce.remote.ServersActions {
            override fun add(name: String, address: String, key: String) { calls += "add $name $address $key" }
            override fun check(server: com.bizzeh.bruce.remote.RemoteServer) { calls += "check ${server.id}" }
            override fun remove(server: com.bizzeh.bruce.remote.RemoteServer) { calls += "remove ${server.id}" }
            override fun use(server: com.bizzeh.bruce.remote.RemoteServer, model: String) { calls += "use ${server.id} $model" }
        }
        val browse = object : BrowseActions {
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
            override fun findCopies(query: String) = Unit
            override fun getTemplate(file: java.io.File) = Unit
            override fun removeTemplate(file: java.io.File) = Unit
        }
        val home = com.bizzeh.bruce.remote.RemoteServer("a", "Home", "http://192.168.1.20:8080", true, false)
        val cloud = com.bizzeh.bruce.remote.RemoteServer("b", "Cloud", "https://llm.example.com", false, true)
        val state = com.bizzeh.bruce.remote.ServersState(
            listOf(home, cloud),
            mapOf("a" to com.bizzeh.bruce.remote.ServerStatus.Ready(listOf("qwen3-8b", "gemma-4")), "b" to com.bizzeh.bruce.remote.ServerStatus.Failed("The server refused the API key.")),
            addError = "Unencrypted http:// is only allowed on your local network. Use https:// for this server.",
            active = RemoteChoice("a", "Home", "gemma-4"),
        )
        compose.setContent { BruceTheme { ModelsScreen(ModelsState(), models, {}, browseActions = browse, servers = state, serversActions = servers) } }

        compose.onNodeWithTag("tab:servers").performClick()
        compose.onNodeWithText("qwen3-8b").assertExists()
        compose.onNodeWithTag("use:a:gemma-4").assertDoesNotExist()
        compose.onNodeWithTag("use:a:qwen3-8b").performClick()
        compose.onNodeWithTag("plain:a", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("plain:b", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("The server refused the API key.").assertExists()
        compose.onNodeWithText("only allowed on your local network", substring = true).assertExists()
        compose.onNodeWithTag("check:b").performClick()
        compose.onNodeWithTag("remove:a").performClick()
        compose.onNodeWithTag("serverName").performTextInput("Box")
        compose.onNodeWithTag("serverAddress").performTextInput("10.0.0.2:8080")
        compose.onNodeWithTag("serverKey").performTextInput("k1")
        compose.onNodeWithTag("addServer").performClick()

        assertEquals(listOf("use a qwen3-8b", "check b", "remove a", "add Box 10.0.0.2:8080 k1"), calls)
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
            override fun findCopies(query: String) = Unit
            override fun getTemplate(file: java.io.File) = Unit
            override fun removeTemplate(file: java.io.File) = Unit
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
        compose.onNodeWithTag("task:TEXT_GENERATION").performClick()
        compose.onNodeWithTag("task:IMAGE_TEXT_TO_TEXT").performClick()
        compose.onNodeWithText("Image and text").assertIsDisplayed()
        compose.onNodeWithTag("parameters:any").performClick()

        assertEquals(
            listOf(
                "recommend",
                "${BrowseFilters(parameters = ParameterBucket.FROM_1B_TO_3B)}",
                "${BrowseFilters(size = SizeBucket.UNDER_1GB)}",
                "${BrowseFilters(runs = RunsFilter.FITS)}",
                "${BrowseFilters(task = HubTask.TEXT_GENERATION)}",
                "${BrowseFilters(task = HubTask.IMAGE_TEXT_TO_TEXT)}",
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
