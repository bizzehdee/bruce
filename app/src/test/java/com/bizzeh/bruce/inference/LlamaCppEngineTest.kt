package com.bizzeh.bruce.inference

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LlamaCppEngineTest {
    @TempDir
    lateinit var dir: File

    private val llama = FakeLlamaApi()
    private val dispatcher = StandardTestDispatcher()
    private val engine = LlamaCppEngine(llama, dispatcher)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun ggufFile(name: String = "model.gguf") =
        File(dir, name).apply { writeBytes("GGUF".toByteArray() + ByteArray(16)) }

    @Test
    fun loadsModelAndReportsInfo() = test {
        val result = engine.loadModel(ggufFile(), LoadConfig(contextLength = 512, threads = 2, batchSize = 64))

        val expected = ModelInfo("fake model 10", 260_000L, 1_000_000L, 512)
        assertEquals(LoadResult.Loaded(expected), result)
        assertEquals(expected, engine.getModelInfo())
        assertEquals(Triple(512, 2, 64), llama.lastContextRequest)
    }

    @Test
    fun missingFileFailsWithoutNativeCall() = test {
        val result = engine.loadModel(File(dir, "absent.gguf"))

        assertEquals(LoadResult.Failed(LoadError.FILE_NOT_FOUND), result)
        assertTrue(llama.loadedPaths.isEmpty())
    }

    @Test
    fun directoryIsNotAModelFile() = test {
        assertEquals(LoadResult.Failed(LoadError.FILE_NOT_FOUND), engine.loadModel(dir))
    }

    @Test
    fun nonGgufFileFailsWithoutNativeCall() = test {
        val file = File(dir, "notes.gguf").apply { writeText("hello world") }

        assertEquals(LoadResult.Failed(LoadError.NOT_GGUF), engine.loadModel(file))
        assertTrue(llama.loadedPaths.isEmpty())
    }

    @Test
    fun nativeLoadFailureIsReported() = test {
        llama.nextModelHandle = 0L

        assertEquals(LoadResult.Failed(LoadError.MODEL_LOAD_FAILED), engine.loadModel(ggufFile()))
        assertNull(engine.getModelInfo())
    }

    @Test
    fun contextFailureFreesTheModel() = test {
        llama.nextContextHandle = 0L

        assertEquals(LoadResult.Failed(LoadError.CONTEXT_CREATION_FAILED), engine.loadModel(ggufFile()))
        assertEquals(listOf(10L), llama.freedModels)
        assertNull(engine.getModelInfo())
    }

    @Test
    fun loadingAnotherModelReleasesThePreviousOne() = test {
        engine.loadModel(ggufFile("a.gguf"))
        llama.nextModelHandle = 11L
        llama.nextContextHandle = 21L

        engine.loadModel(ggufFile("b.gguf"))

        assertEquals(listOf(10L), llama.freedModels)
        assertEquals(listOf(20L), llama.freedContexts)
        assertEquals("fake model 11", engine.getModelInfo()?.description)
    }

    @Test
    fun unloadReleasesContextThenModelOnce() = test {
        engine.loadModel(ggufFile())

        engine.unloadModel()
        engine.unloadModel()

        assertEquals(listOf(20L), llama.freedContexts)
        assertEquals(listOf(10L), llama.freedModels)
        assertNull(engine.getModelInfo())
    }

    @Test
    fun capabilitiesMapGgmlDeviceTypes() {
        llama.devices = intArrayOf(0, 1, 2, 3, 4)

        assertEquals(
            EngineCapabilities(
                listOf(DeviceType.CPU, DeviceType.GPU, DeviceType.INTEGRATED_GPU, DeviceType.ACCELERATOR, DeviceType.OTHER),
            ),
            engine.getCapabilities(),
        )
    }

    @Test
    fun loadConfigRejectsNonPositiveValues() {
        assertThrows<IllegalArgumentException> { LoadConfig(contextLength = 0) }
        assertThrows<IllegalArgumentException> { LoadConfig(threads = 0) }
        assertThrows<IllegalArgumentException> { LoadConfig(batchSize = 0) }
    }
}
