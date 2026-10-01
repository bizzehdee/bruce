package com.bizzeh.bruce.inference

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import kotlin.time.measureTime

/**
 * The fixed start of a prompt, saved after it is first evaluated and loaded after the model is
 * loaded again (TASK-109): greedy output must be exactly what a full evaluation gives, with fewer
 * tokens decoded.
 */
@RunWith(AndroidJUnit4::class)
class SavedPromptDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aSavedFixedPromptChangesNothingButTheWork() = runBlocking {
        val prefix = "Once upon a time, there was a little girl named Lily. She loved to play"
        val prompt = "$prefix outside in the park"
        val saved = SavedPrompts(File(context.cacheDir, "test-prompt-states").apply { deleteRecursively() }, "test")
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
            val engine = deviceEngine(thread, saved)
            val model = storiesModel()
            val config = LoadConfig(contextLength = 256, threads = 2, backend = BackendPreference.CPU)

            assertTrue(engine.loadModel(model, config) is LoadResult.Loaded)
            // Saving first: a prompt the context already holds is reused from memory, not saved.
            val first = engine.complete(prompt, prefix)
            val fresh = engine.complete(prompt, null, reuse = false)
            assertTrue("saved after the first evaluation", saved.fileFor(model, 256, prefix).length() > 0)
            engine.unloadModel()

            assertTrue(engine.loadModel(model, config) is LoadResult.Loaded)
            val loaded = engine.complete(prompt, prefix)
            engine.unloadModel()

            assertEquals(fresh.first, first.first)
            assertEquals(fresh.first, loaded.first)
            assertTrue("decoded ${loaded.second} of ${fresh.second}", loaded.second < fresh.second)
        }
    }

    private fun storiesModel(): File {
        val model = File(context.cacheDir, "stories260K.gguf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("stories260K.gguf").use { input -> model.outputStream().use { input.copyTo(it) } }
        return model
    }
}

/**
 * The same with a real model and Bruce's chat template, timed: the first reply after loading, with
 * and without the saved state. Needs `-e model <file in files/bins>`; results in logcat (tag
 * SavedPromptTiming).
 */
@ManualOnly("needs a real model copied onto the phone")
@RunWith(AndroidJUnit4::class)
class SavedPromptTimingDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theFirstReplyAfterLoadingSkipsTheFixedPrompt() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("model")
        assumeTrue("pass -e model", name != null)
        val model = File(context.filesDir, "bins/$name")
        val system = ChatMessage(ChatRole.SYSTEM, context.resources.openRawResource(com.bizzeh.bruce.R.raw.personality_bruce).bufferedReader().use { it.readText() })
        val saved = SavedPrompts(File(context.cacheDir, "test-prompt-states").apply { deleteRecursively() }, "timing")
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
            val engine = deviceEngine(thread, saved)
            val config = LoadConfig(contextLength = 4096, backend = BackendPreference.CPU)
            assertTrue(engine.loadModel(model, config) is LoadResult.Loaded)
            val prompt = engine.formatChat(listOf(system, ChatMessage(ChatRole.USER, "Name a colour.")))!!.text
            val marker = engine.formatChat(listOf(system, ChatMessage(ChatRole.USER, MARKER)))!!.text
            val prefix = marker.substringBefore(MARKER)
            lateinit var first: Pair<String, Int>
            val firstTime = measureTime { first = engine.complete(prompt, prefix) }
            lateinit var fresh: Pair<String, Int>
            val freshTime = measureTime { fresh = engine.complete(prompt, null, reuse = false) }
            engine.unloadModel()
            assertTrue(engine.loadModel(model, config) is LoadResult.Loaded)
            lateinit var loaded: Pair<String, Int>
            val loadedTime = measureTime { loaded = engine.complete(prompt, prefix) }
            engine.unloadModel()
            val file = saved.fileFor(model, 4096, prefix)
            Log.i(
                TAG,
                "model=$name tokens=${fresh.second} full_ms=${freshTime.inWholeMilliseconds} saving_ms=${firstTime.inWholeMilliseconds} " +
                    "loaded_ms=${loadedTime.inWholeMilliseconds} decoded_after_load=${loaded.second} state_bytes=${file.length()}",
            )
            assertEquals(fresh.first, first.first)
            assertEquals(fresh.first, loaded.first)
            assertTrue(loaded.second < fresh.second)
        }
    }

    private companion object {
        const val TAG = "SavedPromptTiming"
        const val MARKER = "BRUCE_FIXED_PROMPT_END_TEST"
    }
}

/** Greedy text of a short completion, and how many prompt tokens were decoded for it. */
private suspend fun InferenceEngine.complete(prompt: String, savedPrefix: String?, reuse: Boolean = true): Pair<String, Int> {
    val events = generate(GenerationRequest(prompt, maxTokens = 16, temperature = 0f, reusePrompt = reuse, savedPrefix = savedPrefix)).toList()
    val text = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
    return text to events.filterIsInstance<GenerationEvent.Completed>().single().stats.promptTokens
}
