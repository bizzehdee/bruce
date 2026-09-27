package com.bizzeh.bruce.gguf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.inference.deviceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class GgufReaderDeviceTest {
    @Test
    fun parameterCountMatchesLlamaCpp() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val model = File(instrumentation.targetContext.cacheDir, "stories260K.gguf")
        instrumentation.context.assets.open("stories260K.gguf").use { input ->
            model.outputStream().use { input.copyTo(it) }
        }
        val metadata = (GgufReader.read(model) as GgufReadResult.Read).metadata

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { nativeThread ->
            val engine = deviceEngine(nativeThread)
            val loaded = engine.loadModel(model, LoadConfig(contextLength = 128, threads = 2)) as LoadResult.Loaded
            engine.unloadModel()

            assertEquals(loaded.info.parameterCount, metadata.parameterCount)
            assertEquals(loaded.info.trainedContextLength.toLong(), metadata.contextLength)
        }
    }
}
