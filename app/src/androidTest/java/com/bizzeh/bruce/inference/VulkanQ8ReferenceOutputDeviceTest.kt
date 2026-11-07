package com.bizzeh.bruce.inference

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/**
 * Real-hardware check (TASK-050 follow-up) of whether llama.cpp's Vulkan backend gives correct
 * output for a Q8_0 model, not just a 4-bit one. `.learnings/gpu-backends-on-test-phones.md` and
 * `research/experiments/vulkan-powervr-2026-09-29/` already found Q8_0 (Qwen3.5-0.8B, standalone
 * llama-simple, not through Bruce) gave garbage or a different answer from the CPU on most
 * settings, with only a short 16-token prompt matching — this repeats that check through Bruce's
 * own `LlamaCppEngine` with its real default settings, to see whether Bruce's own path (not the
 * raw CLI flags that earlier run manually varied) changes the result. Needs a Q8_0 GGUF model
 * pushed to the app's cache dir first:
 * `adb shell am instrument -w -e class com.bizzeh.bruce.inference.VulkanQ8ReferenceOutputDeviceTest
 *  -e model Qwen3.5-0.8B-Q8_0.gguf com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner`
 */
@ManualOnly("Generates real output on Vulkan and CPU for human comparison; needs a Q8_0 GGUF model in cacheDir")
@RunWith(AndroidJUnit4::class)
class VulkanQ8ReferenceOutputDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)

    @After
    fun tearDown() = runTestBlocking {
        engine.unloadModel()
        nativeThread.close()
    }

    /**
     * CPU and GPU are run in separate `@Test` methods, not one method doing both, so a large model
     * is only ever resident once per process: a large (multi-GB) GGUF loaded twice in the same
     * process without the OS fully reclaiming the first copy's pages first can trip Android's own
     * `MemoryLimiter` (found 2026-11-07, Gemma 4 E2B Q8_0: `killing process ... MemoryLimiter:AnonSwap`).
     * Run both methods (`-e class ...#cpuOutput`, then `...#vulkanOutput`) and compare the logged
     * replies by hand.
     */
    @Test
    fun cpuOutput() = runTestBlocking {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("model")) { "-e model <file in cacheDir>" }
        val model = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        Log.i(TAG, "model=$name")
        Log.i(TAG, "CPU reply=${generate(model, BackendPreference.CPU, PROMPT)}")
    }

    @Test
    fun vulkanOutput() = runTestBlocking {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("model")) { "-e model <file in cacheDir>" }
        val model = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        Log.i(TAG, "model=$name")
        Log.i(TAG, "VULKAN reply=${generate(model, BackendPreference.VULKAN, PROMPT)}")
    }

    private suspend fun generate(model: File, backend: BackendPreference, prompt: String): String {
        val loaded = engine.loadModel(model, LoadConfig(contextLength = 1024, threads = 4, backend = backend))
        assertTrue("$backend failed to load: $loaded", loaded is LoadResult.Loaded)
        val formatted = engine.formatChat(listOf(ChatMessage(ChatRole.USER, prompt)))
        val text = engine.generate(GenerationRequest(formatted?.text ?: prompt, maxTokens = 48, temperature = 0f)).toList()
            .filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        engine.unloadModel()
        return text
    }

    private fun runTestBlocking(block: suspend () -> Unit) = runBlocking { block() }

    private companion object {
        const val TAG = "VulkanQ8RefTest"
        const val PROMPT = "What is the capital of France? Answer in one short sentence."
    }
}
