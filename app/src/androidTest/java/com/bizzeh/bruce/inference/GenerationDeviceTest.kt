package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class GenerationDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = LlamaCppEngine(LlamaNative, nativeThread)

    private lateinit var modelFile: File

    @Before
    fun loadFixture() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        modelFile = File(instrumentation.targetContext.cacheDir, "stories260K.gguf")
        instrumentation.context.assets.open("stories260K.gguf").use { input ->
            modelFile.outputStream().use { input.copyTo(it) }
        }
        assertTrue(engine.loadModel(modelFile, LoadConfig(contextLength = 128, threads = 2)) is LoadResult.Loaded)
    }

    @After
    fun tearDown() = runTest {
        engine.unloadModel()
        nativeThread.close()
    }

    private val greedy = GenerationRequest("Once upon a time", maxTokens = 24, temperature = 0f)

    private fun List<GenerationEvent>.text() =
        filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }

    @Test
    fun greedyGenerationStreamsTextAndStats() = runTest {
        val events = engine.generate(greedy).toList()

        val completed = events.last() as GenerationEvent.Completed
        assertTrue(events.text().isNotBlank())
        assertTrue(completed.stats.promptTokens > 0)
        assertTrue(completed.stats.generatedTokens in 1..24)
        assertTrue(completed.stats.generationTokensPerSecond > 0.0)
    }

    @Test
    fun greedyGenerationIsDeterministic() = runTest {
        assertEquals(engine.generate(greedy).toList().text(), engine.generate(greedy).toList().text())
    }

    @Test
    fun cancelledGenerationLeavesEngineUsable() = runTest {
        engine.generate(greedy).take(2).toList()

        assertTrue(engine.generate(greedy).toList().last() is GenerationEvent.Completed)
    }

    @Test
    fun stopEndsGeneration() = runTest {
        val events = mutableListOf<GenerationEvent>()
        engine.generate(greedy.copy(maxTokens = 100)).collect { event ->
            events += event
            if (event is GenerationEvent.Token) engine.stop()
        }

        val completed = events.last() as GenerationEvent.Completed
        assertEquals(StopReason.STOPPED, completed.reason)
        assertTrue(completed.stats.generatedTokens < 100)
    }

    @Test
    fun promptLongerThanContextFails() = runTest {
        val longPrompt = "Once upon a time ".repeat(200)

        assertEquals(
            listOf(GenerationEvent.Failed(GenerationError.PROMPT_TOO_LONG)),
            engine.generate(GenerationRequest(longPrompt)).toList(),
        )
    }

    @Test
    fun promptLongerThanRequestedContextButWithinPaddedContextIsEvaluated() = runTest {
        // llama.cpp pads the 128-token context to 256, so this ~150-token prompt fits.
        val prompt = "Once upon a time there was a dog. ".repeat(15)

        val events = engine.generate(GenerationRequest(prompt, maxTokens = 4, temperature = 0f)).toList()

        val completed = events.last() as GenerationEvent.Completed
        assertTrue(completed.stats.promptTokens > 128)
    }

    @Test
    fun promptLongerThanBatchIsEvaluatedInChunks() = runTest {
        engine.loadModel(modelFile, LoadConfig(contextLength = 256, threads = 2, batchSize = 32))
        val prompt = "Once upon a time there was a dog. ".repeat(15)

        val completed = engine.generate(GenerationRequest(prompt, maxTokens = 4, temperature = 0f)).toList().last()

        assertTrue(completed is GenerationEvent.Completed)
        assertTrue((completed as GenerationEvent.Completed).stats.promptTokens > 32 * 4)
    }

    @Test
    fun generationStopsWhenContextIsFull() = runTest {
        val completed = engine.generate(greedy.copy(maxTokens = 1000)).toList().last() as GenerationEvent.Completed

        assertEquals(StopReason.CONTEXT_FULL, completed.reason)
        assertTrue(completed.stats.promptTokens + completed.stats.generatedTokens >= 128)
    }
}
