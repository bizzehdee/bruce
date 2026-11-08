package com.bizzeh.bruce.litert

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-hardware check (TASK-119) for whether LiteRT-LM's GPU delegate gives correct output, not
 * just whether it loads. [LiteRtNative.loadModel] "succeeding" is not evidence of correctness —
 * llama.cpp's own Vulkan backend loads and runs on this same Pixel 11's PowerVR GPU but gives
 * garbage output (`.learnings/gpu-backends-on-test-phones.md`). This generates a real reply on
 * both GPU and CPU for the same prompt and model, and prints both so a human can compare them —
 * it does not assert equality, since sampling is not guaranteed deterministic across backends.
 * Needs `files/models/gemma-3-270m-it.litertlm` and/or `files/models/gemma-4-E2B-it.litertlm`
 * already present (both already are, from the Models screen's own downloads):
 * `adb shell am instrument -w -e class com.bizzeh.bruce.litert.LiteRtGpuReferenceOutputDeviceTest
 *  -e model gemma-3-270m-it.litertlm com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner`
 */
@ManualOnly("Generates real output on GPU and CPU for human comparison; needs a model in files/models")
@RunWith(AndroidJUnit4::class)
class LiteRtGpuReferenceOutputDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Separate methods, not one doing both: a GPU-only export (no CPU section) fails the CPU half
     * by design, and failing fast loses the GPU half's already-successful result too. */
    @Test
    fun gpuOutput() {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("model")) { "-e model <file in files/models>" }
        val model = File(File(context.filesDir, "models"), name)
        val reply = generate(model, LiteRtBackendPreference.GPU, PROMPT_MESSAGE)
        Log.i(TAG, "model=$name GPU backend=${reply.backend} reply=${reply.text}")
    }

    @Test
    fun cpuOutput() {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("model")) { "-e model <file in files/models>" }
        val model = File(File(context.filesDir, "models"), name)
        val reply = generate(model, LiteRtBackendPreference.CPU, PROMPT_MESSAGE)
        Log.i(TAG, "model=$name CPU backend=${reply.backend} reply=${reply.text}")
    }

    private fun generate(model: File, preference: LiteRtBackendPreference, prompt: ToolChatMessage): Reply {
        val result = LiteRtNative.loadModel(model, 4096, preference, context.applicationInfo.nativeLibraryDir)
        val loaded = result as? LiteRtLoadResult.Loaded ?: fail("$preference failed to load at all: $result").let { error("unreachable") }
        return try {
            val conversation = loaded.model.converse(
                systemInstruction = "",
                priorMessages = emptyList(),
                latest = prompt,
                tools = emptyList(),
                thinking = false,
                maxTokens = 64,
                temperature = 0f,
                topK = GenerationRequest.DEFAULT_TOP_K,
                topP = GenerationRequest.DEFAULT_TOP_P,
            )
            val events = runBlocking { conversation.send().toList() }
            conversation.close()
            val done = events.filterIsInstance<LiteRtEvent.Done>().firstOrNull()
            Reply(loaded.backend, done?.content ?: "NO Done EVENT; events=$events")
        } finally {
            loaded.model.close()
        }
    }

    private data class Reply(val backend: LiteRtBackendChoice, val text: String)

    private companion object {
        const val TAG = "LiteRtGpuRefTest"
        val PROMPT_MESSAGE = ToolChatMessage(role = ChatRole.USER, content = "What is the capital of France? Answer in one short sentence.")
    }
}
