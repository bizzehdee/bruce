package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/** Greedy output with the previous prompt reused must be exactly the output without reuse. */
@RunWith(AndroidJUnit4::class)
class PromptReuseDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)

    @After
    fun tearDown() = runTest {
        engine.unloadModel()
        nativeThread.close()
    }

    /** stories260K has plain attention memory, so the shared prefix is kept and the rest decoded. */
    @Test
    fun reusingAPromptChangesNothingButTheWork() = runBlocking {
        val first = engine.greedyStoryText(BackendPreference.CPU, threads = 2)
        val continued = "Once upon a time$first One day"
        val diverged = "Once upon a time, there was a big dog"

        for (prompt in listOf(continued, diverged, continued)) {
            val reused = engine.greedy(prompt, reuse = true)
            val fresh = engine.greedy(prompt, reuse = false)
            assertEquals(prompt, fresh.first, reused.first)
            assertTrue("$prompt: decoded ${reused.second} of ${fresh.second}", reused.second < fresh.second)
        }
    }
}

/**
 * The same with Qwen3.5, whose hybrid memory cannot be cut back and is reused through a checkpoint.
 * Needs the model in files/test-models (docs/building.md).
 */
@ManualOnly("needs a real model copied onto the phone")
@RunWith(AndroidJUnit4::class)
class PromptReuseModelDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)

    @After
    fun tearDown() = runTest {
        engine.unloadModel()
        nativeThread.close()
    }

    @Test
    fun aHybridModelReusesTheLastPromptThroughACheckpoint() = runBlocking {
        val model = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "test-models/Qwen3.5-0.8B-Q8_0.gguf")
        assumeTrue("copy the model first", model.exists())
        assertTrue(engine.loadModel(model, LoadConfig(contextLength = 4096, threads = 4, backend = BackendPreference.CPU)) is LoadResult.Loaded)
        val system = ChatMessage(ChatRole.SYSTEM, "You are a helpful assistant. Keep replies to one sentence.")
        val question = ChatMessage(ChatRole.USER, "Name a colour.")

        val firstPrompt = engine.formatChat(listOf(system, question))!!.text
        val (answer, _) = engine.greedy(firstPrompt, reuse = true)
        val secondPrompt = engine.formatChat(listOf(system, question, ChatMessage(ChatRole.ASSISTANT, answer), ChatMessage(ChatRole.USER, "Now name an animal.")))!!.text

        val reused = engine.greedy(secondPrompt, reuse = true)
        val fresh = engine.greedy(secondPrompt, reuse = false)
        assertEquals(fresh.first, reused.first)
        assertTrue("decoded ${reused.second} of ${fresh.second}", reused.second < fresh.second)
    }
}

/** Greedy text and how many prompt tokens were decoded for it. */
private fun InferenceEngine.greedy(prompt: String, reuse: Boolean): Pair<String, Int> = runBlocking {
    val events = generate(GenerationRequest(prompt, maxTokens = 24, temperature = 0f, reusePrompt = reuse)).toList()
    val text = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
    val decoded = events.filterIsInstance<GenerationEvent.Completed>().single().stats.promptTokens
    text to decoded
}
