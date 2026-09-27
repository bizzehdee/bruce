package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class LlamaCppEngineDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)
    private val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    @After
    fun tearDown() = runTest {
        engine.unloadModel()
        nativeThread.close()
    }

    private fun fixture(name: String = "stories260K.gguf"): File {
        val target = File(cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("stories260K.gguf").use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    @Test
    fun loadsRealModelAndReportsInfo() = runTest {
        val result = engine.loadModel(fixture(), LoadConfig(contextLength = 128, threads = 2))

        assertTrue(result is LoadResult.Loaded)
        val info = (result as LoadResult.Loaded).info
        assertTrue(info.parameterCount in 200_000L..300_000L)
        assertTrue(info.description.isNotBlank())
        assertEquals(info, engine.getModelInfo())
    }

    @Test
    fun unloadClearsModel() = runTest {
        engine.loadModel(fixture(), LoadConfig(contextLength = 128, threads = 2))

        engine.unloadModel()

        assertNull(engine.getModelInfo())
    }

    @Test
    fun truncatedGgufFailsToLoad() = runTest {
        val truncated = fixture("truncated.gguf").apply { writeBytes(readBytes().copyOf(1024)) }

        assertEquals(LoadResult.Failed(LoadError.MODEL_LOAD_FAILED), engine.loadModel(truncated))
    }

    @Test
    fun cpuBackendIsAvailable() {
        assertTrue(Backend.CPU in engine.getCapabilities().usableBackends)
    }
}
