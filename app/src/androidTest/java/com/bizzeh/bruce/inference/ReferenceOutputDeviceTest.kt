package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/** Checks what the model says, not only that it says something. */
@RunWith(AndroidJUnit4::class)
class ReferenceOutputDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)

    @After
    fun tearDown() = runTest {
        engine.unloadModel()
        nativeThread.close()
    }

    @Test
    fun cpuGreedyOutputMatchesLlamaCpp() {
        assertEquals(STORIES_REFERENCE, engine.greedyStoryText(BackendPreference.CPU, threads = 1).take(STORIES_REFERENCE.length))
    }

    /** A prompt sent while the model reloads with other settings must wait for the reload, not reach a half-loaded model. */
    @Test
    fun promptDuringReloadGetsTheReloadedModelsOutput() = runBlocking {
        assertEquals(STORIES_REFERENCE, engine.greedyStoryText(BackendPreference.CPU, threads = 1).take(STORIES_REFERENCE.length))
        val model = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "stories260K.gguf")

        repeat(5) { attempt ->
            val reload = launch(Dispatchers.Default) {
                assertTrue(engine.loadModel(model, LoadConfig(contextLength = 256, threads = 2 + attempt % 2, backend = BackendPreference.CPU)) is LoadResult.Loaded)
            }
            val prompt = engine.formatChat(listOf(ChatMessage(ChatRole.USER, "unused")))
            val text = engine.generate(GenerationRequest("Once upon a time", maxTokens = 24, temperature = 0f)).toList()
                .filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
            reload.join()
            assertEquals("attempt $attempt (prompt formatted: ${prompt != null})", STORIES_REFERENCE, text.take(STORIES_REFERENCE.length))
        }
    }

    @Test
    fun cpuGreedyOutputMatchesLlamaCppWithSeveralThreads() {
        assertEquals(STORIES_REFERENCE, engine.greedyStoryText(BackendPreference.CPU, threads = 4).take(STORIES_REFERENCE.length))
    }
}

/**
 * llama.cpp's own greedy output for stories260K after "Once upon a time", from llama-server built
 * at the pinned revision (armv8.2 CPU kernels) on the Xperia 1 II and Pixel 11, 2026-09-28.
 */
internal const val STORIES_REFERENCE = ", there was a little girl named Lily. She loved to play outside in the p"

internal fun InferenceEngine.greedyStoryText(backend: BackendPreference, threads: Int): String = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val model = File(instrumentation.targetContext.cacheDir, "stories260K.gguf")
    instrumentation.context.assets.open("stories260K.gguf").use { input -> model.outputStream().use { input.copyTo(it) } }
    val loaded = loadModel(model, LoadConfig(contextLength = 256, threads = threads, backend = backend))
    assertTrue(loaded.toString(), loaded is LoadResult.Loaded)
    generate(GenerationRequest("Once upon a time", maxTokens = 24, temperature = 0f)).toList()
        .filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
}
