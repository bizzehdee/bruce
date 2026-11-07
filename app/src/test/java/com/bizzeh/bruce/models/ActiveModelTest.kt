package com.bizzeh.bruce.models

import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.litert.LiteRtPipelineResult
import com.bizzeh.bruce.testing.FakeChatBackend
import com.bizzeh.bruce.testing.FakeEngine
import com.bizzeh.bruce.testing.FakeLiteRtPipeline
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ActiveModelTest {
    @TempDir
    lateinit var dir: File

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val liteRt = FakeLiteRtPipeline()
    private val info = ModelInfo("qwen3", 1, 1, 40_960)
    private val activeModel by lazy { ActiveModel(engine, dir, dispatcher, liteRt) }

    @Test
    fun refreshListsGgufAndLiteRtLmFilesAlphabetically() = runTest(dispatcher) {
        listOf("b.gguf", "A.gguf", "c.litertlm", ".download-x.part", "notes.txt").forEach { File(dir, it).writeText("x") }
        File(dir, "folder.gguf").mkdir()

        activeModel.refresh()

        assertEquals(listOf("A.gguf", "b.gguf", "c.litertlm"), activeModel.state.value.installed.map { it.name })
    }

    @Test
    fun missingModelsFolderIsEmpty() = runTest(dispatcher) {
        ActiveModel(engine, File(dir, "absent"), dispatcher).apply {
            refresh()
            assertEquals(emptyList<File>(), state.value.installed)
        }
    }

    @Test
    fun loadRecordsTheActiveModel() = runTest(dispatcher) {
        val file = File(dir, "m.gguf")
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)

        activeModel.load(file, LoadConfig(contextLength = 512))

        assertEquals(ActiveModelState(active = file, format = ModelFormat.GGUF, info = info, backend = Backend.CPU), activeModel.state.value)
        assertEquals(512, engine.loads.single().second.contextLength)
    }

    @Test
    fun contextIsCappedAtTheModelsTrainedLength() = runTest(dispatcher) {
        val model = File(dir, "stories.gguf").apply { writeBytes(File("src/androidTest/assets/stories260K.gguf").readBytes()) }

        activeModel.load(model, LoadConfig(contextLength = 8192))
        activeModel.load(model, LoadConfig(contextLength = 1024))

        // stories260K was trained with a 2048-token context.
        assertEquals(listOf(2048, 1024), engine.loads.map { it.second.contextLength })
    }

    @Test
    fun theLoadedModelsMemoryIsKeptUntilItIsReleased() = runTest(dispatcher) {
        val model = File(dir, "stories.gguf").apply { writeBytes(File("src/androidTest/assets/stories260K.gguf").readBytes()) }
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)

        activeModel.load(model, LoadConfig(contextLength = 1024))
        val metadata = (com.bizzeh.bruce.gguf.GgufReader.read(model) as com.bizzeh.bruce.gguf.GgufReadResult.Read).metadata
        assertEquals(ModelMemory.estimate(metadata, 1024).totalBytes, activeModel.state.value.memoryBytes)

        activeModel.unload()
        assertEquals(0L, activeModel.state.value.memoryBytes)
        activeModel.load(File(dir, "not-gguf.gguf"), LoadConfig(contextLength = 1024))
        assertEquals(0L, activeModel.state.value.memoryBytes, "no header, no estimate")
    }

    @Test
    fun aServerModelReplacesThePhonesAndLoadingOneClearsIt() = runTest(dispatcher) {
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)
        activeModel.load(File(dir, "a.gguf"))

        activeModel.useRemote(RemoteChoice("s", "Home", "qwen3"))
        assertEquals(1, engine.unloads)
        assertNull(activeModel.state.value.active)
        assertEquals("qwen3 (Home)", activeModel.state.value.remote?.label)

        activeModel.load(File(dir, "a.gguf"))
        assertNull(activeModel.state.value.remote)
    }

    @Test
    fun unloadReleasesTheModel() = runTest(dispatcher) {
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)
        activeModel.load(File(dir, "a.gguf"))

        activeModel.unload()

        assertEquals(1, engine.unloads)
        assertNull(activeModel.state.value.active)
        assertNull(activeModel.state.value.backend)
    }

    @Test
    fun failureThatReleasedThePreviousModelClearsIt() = runTest(dispatcher) {
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)
        activeModel.load(File(dir, "a.gguf"))
        engine.loadResult = LoadResult.Failed(LoadError.MODEL_LOAD_FAILED)
        engine.loadedModel = null

        activeModel.load(File(dir, "b.gguf"))

        val state = activeModel.state.value
        assertNull(state.active)
        assertNull(state.info)
        assertEquals(LoadError.MODEL_LOAD_FAILED, state.error)
        assertFalse(state.loading)
    }

    @Test
    fun failureThatKeptThePreviousModelKeepsIt() = runTest(dispatcher) {
        val first = File(dir, "a.gguf")
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)
        activeModel.load(first)
        engine.loadResult = LoadResult.Failed(LoadError.NOT_GGUF)
        engine.loadedModel = info

        activeModel.load(File(dir, "b.txt"))

        assertEquals(first, activeModel.state.value.active)
        assertEquals(LoadError.NOT_GGUF, activeModel.state.value.error)
    }

    @Test
    fun loadAnyDispatchesGgufToTheEngine() = runTest(dispatcher) {
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)
        val file = File(dir, "m.gguf")

        val result = activeModel.loadAny(file, LoadConfig(contextLength = 512))

        assertEquals(ModelLoadResult.Loaded(ModelFormat.GGUF, info, Backend.CPU), result)
        assertEquals(file, engine.loads.single().first)
        assertEquals(ModelFormat.GGUF, activeModel.state.value.format)
    }

    @Test
    fun loadAnyDispatchesLiteRtLmToThePipeline() = runTest(dispatcher) {
        val file = File(dir, "m.litertlm").apply { writeBytes(ByteArray(1024)) }
        liteRt.result = LiteRtPipelineResult.Loaded(FakeChatBackend(), 8192)

        val result = activeModel.loadAny(file)

        assertEquals(ModelLoadResult.Loaded(ModelFormat.LITERT_LM), result)
        assertEquals(Triple(file, 0, null), liteRt.loads.single())
        assertTrue(engine.loads.isEmpty(), "the llama.cpp engine was not used")
        val state = activeModel.state.value
        assertEquals(file, state.active)
        assertEquals(ModelFormat.LITERT_LM, state.format)
        assertNull(state.info)
        assertNull(state.backend)
        assertEquals(1024L, state.memoryBytes, "memory is the file's size: no detailed estimate exists for this format")
    }

    @Test
    fun loadAnyPassesTheGpuPreferenceThrough() = runTest(dispatcher) {
        val file = File(dir, "m.litertlm").apply { writeBytes(ByteArray(1024)) }
        liteRt.result = LiteRtPipelineResult.Loaded(FakeChatBackend(), 8192)

        activeModel.loadAny(file, liteRtContextTokens = 16384, liteRtPreferGpu = true)

        assertEquals(Triple(file, 16384, true), liteRt.loads.single())
    }

    @Test
    fun liteRtGpuAvailableAsksThePipeline() {
        val file = File(dir, "m.litertlm")
        liteRt.gpuAvailableFiles = setOf("m.litertlm")

        assertTrue(activeModel.liteRtGpuAvailable(file))
        assertFalse(activeModel.liteRtGpuAvailable(File(dir, "other.litertlm")))
    }

    @Test
    fun loadAnyFailsCleanlyWithNoLiteRtPipeline() = runTest(dispatcher) {
        val withoutLiteRt = ActiveModel(engine, dir, dispatcher)
        val file = File(dir, "m.litertlm").apply { writeBytes(ByteArray(1024)) }

        val result = withoutLiteRt.loadAny(file)

        assertEquals(ModelLoadResult.Failed(LoadError.NOT_GGUF), result)
        assertNull(withoutLiteRt.state.value.active)
    }

    @Test
    fun loadAnyFailsForAnUnknownFormat() = runTest(dispatcher) {
        val result = activeModel.loadAny(File(dir, "notes.txt"))
        assertEquals(ModelLoadResult.Failed(LoadError.NOT_GGUF), result)
    }

    @Test
    fun loadAnyLiteRtFailureClosesTheModelAndClearsState() = runTest(dispatcher) {
        val first = File(dir, "a.litertlm").apply { writeBytes(ByteArray(10)) }
        liteRt.result = LiteRtPipelineResult.Loaded(FakeChatBackend(), 8192)
        activeModel.loadAny(first)

        liteRt.result = LiteRtPipelineResult.Failed
        val result = activeModel.loadAny(File(dir, "b.litertlm").apply { writeBytes(ByteArray(10)) })

        assertEquals(ModelLoadResult.Failed(LoadError.MODEL_LOAD_FAILED), result)
        val state = activeModel.state.value
        assertNull(state.active)
        assertEquals(LoadError.MODEL_LOAD_FAILED, state.error)
        assertEquals(0L, state.memoryBytes)
    }

    @Test
    fun unloadReleasesBothPipelines() = runTest(dispatcher) {
        engine.loadResult = LoadResult.Loaded(info, Backend.CPU)
        activeModel.load(File(dir, "a.gguf"))

        activeModel.unload()

        assertEquals(1, engine.unloads)
        assertEquals(1, liteRt.unloads)
    }
}
