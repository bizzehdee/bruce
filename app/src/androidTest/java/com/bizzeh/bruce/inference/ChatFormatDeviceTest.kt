package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ChatFormatDeviceTest {
    @Test
    fun modelWithoutTemplateFallsBackToChatMlAndReplies() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val model = File(instrumentation.targetContext.cacheDir, "stories260K.gguf")
        instrumentation.context.assets.open("stories260K.gguf").use { input -> model.outputStream().use { input.copyTo(it) } }

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { nativeThread ->
            val engine = deviceEngine(nativeThread)
            engine.loadModel(model, LoadConfig(contextLength = 256, threads = 2))

            val prompt = engine.formatChat(listOf(ChatMessage(ChatRole.USER, "Tell me a story about a dog 🐕")))!!
            val events = engine.generate(GenerationRequest(prompt.text, maxTokens = 16, temperature = 0f)).toList()
            engine.unloadModel()

            assertTrue(prompt.usedFallbackTemplate)
            assertEquals("<|im_start|>user\nTell me a story about a dog 🐕<|im_end|>\n<|im_start|>assistant\n", prompt.text)
            assertTrue(events.last() is GenerationEvent.Completed)
        }
    }
}
