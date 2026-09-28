package com.bizzeh.bruce.models

import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.testing.FakeEngine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ActiveModelTest {
    @TempDir
    lateinit var dir: File

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val info = ModelInfo("qwen3", 1, 1, 40_960)
    private val activeModel by lazy { ActiveModel(engine, dir, dispatcher) }

    @Test
    fun refreshListsGgufFilesAlphabetically() = runTest(dispatcher) {
        listOf("b.gguf", "A.gguf", ".download-x.part", "notes.txt").forEach { File(dir, it).writeText("x") }
        File(dir, "folder.gguf").mkdir()

        activeModel.refresh()

        assertEquals(listOf("A.gguf", "b.gguf"), activeModel.state.value.installed.map { it.name })
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

        assertEquals(ActiveModelState(active = file, info = info, backend = Backend.CPU), activeModel.state.value)
        assertEquals(512, engine.loads.single().second.contextLength)
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
}
