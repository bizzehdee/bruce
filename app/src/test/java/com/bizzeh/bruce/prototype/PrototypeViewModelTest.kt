package com.bizzeh.bruce.prototype

import android.app.ActivityManager
import android.net.Uri
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.testing.FakeEngine
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.StopReason
import com.bizzeh.bruce.models.ActiveModel
import com.bizzeh.bruce.models.ImportError
import com.bizzeh.bruce.models.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PrototypeViewModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val cpu = CpuFeatures(arm64 = true, neon = true, fp16 = false, dotProd = false, i8mm = false)
    private lateinit var modelsDir: File
    private var importResult: ImportResult = ImportResult.Failed(ImportError.UNREADABLE)
    private val memory = ActivityManager.MemoryInfo().apply {
        availMem = 8L shl 30
        threshold = 0
    }

    private val fixture = File("src/androidTest/assets/stories260K.gguf")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        modelsDir = temp.newFolder("models")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = PrototypeViewModel(
        engine = engine,
        activeModel = ActiveModel(engine, modelsDir, dispatcher),
        modelsDir = modelsDir,
        importModel = { importResult },
        detectCpuFeatures = { cpu },
        memoryInfo = { memory },
        ioDispatcher = dispatcher,
    ).also { advanceUntilIdle() }

    private fun model(name: String) = File(modelsDir, name).apply { writeBytes(fixture.readBytes()) }

    @Test
    fun startupReportsHardwareAndListsOnlyGgufFiles() = runTest(dispatcher) {
        model("b.gguf")
        model("a.gguf")
        File(modelsDir, ".a.gguf.partial").writeText("x")
        File(modelsDir, "notes.txt").writeText("x")

        val state = viewModel().state.value

        assertEquals(cpu, state.cpuFeatures)
        assertEquals(engine.reportedCapabilities, state.capabilities)
        assertEquals(listOf("a.gguf", "b.gguf"), state.models.map { it.name })
    }

    @Test
    fun selectingReadsMetadataAndMemoryEstimate() = runTest(dispatcher) {
        val file = model("stories.gguf")
        val vm = viewModel()

        vm.select(file)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(file, state.selected)
        assertEquals("llama", state.metadata?.architecture)
        assertTrue(state.memory!!.fits)
    }

    @Test
    fun selectingAnUnreadableFileReportsTheError() = runTest(dispatcher) {
        val file = File(modelsDir, "broken.gguf").apply { writeText("not a model") }
        val vm = viewModel()

        vm.select(file)
        advanceUntilIdle()

        assertNull(vm.state.value.metadata)
        assertNull(vm.state.value.memory)
        assertEquals(com.bizzeh.bruce.gguf.GgufError.NOT_GGUF, vm.state.value.metadataError)
    }

    @Test
    fun successfulImportRefreshesAndSelectsTheModel() = runTest(dispatcher) {
        val vm = viewModel()
        importResult = ImportResult.Imported(model("imported.gguf"))

        vm.import(Uri.parse("content://docs/1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.importing)
        assertNull(state.importError)
        assertEquals(listOf("imported.gguf"), state.models.map { it.name })
        assertEquals("imported.gguf", state.selected?.name)
    }

    @Test
    fun failedImportIsReported() = runTest(dispatcher) {
        val vm = viewModel()
        importResult = ImportResult.Failed(ImportError.NOT_GGUF)

        vm.import(Uri.parse("content://docs/1"))
        advanceUntilIdle()

        assertEquals(ImportError.NOT_GGUF, vm.state.value.importError)
        assertNull(vm.state.value.selected)
    }

    @Test
    fun loadUsesChosenBackendAndReportsResult() = runTest(dispatcher) {
        val file = model("m.gguf")
        val vm = viewModel()
        engine.loadResult = LoadResult.Loaded(ModelInfo("m", 1, 1, 512), Backend.CPU, listOf(Backend.VULKAN))

        vm.select(file)
        advanceUntilIdle()
        vm.setBackend(BackendPreference.VULKAN)
        vm.load()
        advanceUntilIdle()

        assertEquals(BackendPreference.VULKAN, engine.loads.single().second.backend)
        assertEquals(LoadState.Loaded(ModelInfo("m", 1, 1, 512), Backend.CPU, listOf(Backend.VULKAN)), vm.state.value.load)
    }

    @Test
    fun loadWithoutSelectionDoesNothing() = runTest(dispatcher) {
        val vm = viewModel()

        vm.load()
        advanceUntilIdle()

        assertTrue(engine.loads.isEmpty())
        assertEquals(LoadState.Unloaded, vm.state.value.load)
    }

    @Test
    fun failedLoadIsReported() = runTest(dispatcher) {
        val vm = viewModel()
        engine.loadResult = LoadResult.Failed(LoadError.MODEL_LOAD_FAILED)

        vm.select(model("m.gguf"))
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()

        assertEquals(LoadState.Failed(LoadError.MODEL_LOAD_FAILED), vm.state.value.load)
    }

    @Test
    fun generateStreamsOutputAndStats() = runTest(dispatcher) {
        val vm = loadedViewModel()
        val stats = GenerationStats(3, 1.seconds, 2, 1.seconds)
        engine.events = listOf(
            GenerationEvent.Token("Once"),
            GenerationEvent.Token(" upon"),
            GenerationEvent.Completed(StopReason.MAX_TOKENS, stats),
        )

        vm.setPrompt("Tell a story")
        vm.generate()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Tell a story", engine.requests.single().prompt)
        assertEquals("Once upon", state.output)
        assertEquals(stats, state.stats)
        assertEquals(StopReason.MAX_TOKENS, state.stopReason)
        assertFalse(state.generating)
    }

    @Test
    fun generationFailureIsReported() = runTest(dispatcher) {
        val vm = loadedViewModel()
        engine.events = listOf(GenerationEvent.Failed(GenerationError.PROMPT_TOO_LONG))

        vm.generate()
        advanceUntilIdle()

        assertEquals(GenerationError.PROMPT_TOO_LONG, vm.state.value.generationError)
    }

    @Test
    fun generateIsIgnoredUntilAModelIsLoaded() = runTest(dispatcher) {
        val vm = viewModel()

        vm.generate()
        advanceUntilIdle()

        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun generateIsIgnoredWhileGenerating() = runTest(dispatcher) {
        val vm = loadedViewModel()

        vm.generate()
        vm.generate()
        advanceUntilIdle()

        assertEquals(1, engine.requests.size)
    }

    @Test
    fun stopIsForwardedToTheEngine() = runTest(dispatcher) {
        viewModel().stop()

        assertEquals(1, engine.stops)
    }

    private fun TestScope.loadedViewModel(): PrototypeViewModel {
        val vm = viewModel()
        vm.select(model("m.gguf"))
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        return vm
    }
}
