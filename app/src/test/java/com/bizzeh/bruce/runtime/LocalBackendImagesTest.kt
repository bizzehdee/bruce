package com.bizzeh.bruce.runtime

import com.bizzeh.bruce.inference.ChatMessage
import com.bizzeh.bruce.inference.ChatPrompt
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.EngineCapabilities
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.ParsedReply
import com.bizzeh.bruce.inference.RgbImage
import com.bizzeh.bruce.inference.StopReason
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolChatPrompt
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.inference.ToolFormat
import com.bizzeh.bruce.inference.ToolGrammar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** Robolectric for Android's Log. */
@RunWith(RobolectricTestRunner::class)
class LocalBackendImagesTest {
    /** Formats a chat as its contents, one per line, and counts a token per character. */
    private class SeeingEngine(var marker: String? = "<img>") : InferenceEngine {
        val requests = mutableListOf<GenerationRequest>()
        var imageTokenCalls = 0

        override suspend fun loadModel(file: File, config: LoadConfig): LoadResult = error("not used")
        override suspend fun unloadModel() = Unit
        override fun getCapabilities() = EngineCapabilities(emptyList(), emptyList())
        override fun getModelInfo() = ModelInfo("vision", 1, 1, 4096, vision = marker != null)
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> {
            requests += request
            return flowOf(GenerationEvent.Token("A moon."), GenerationEvent.Completed(StopReason.END_OF_GENERATION, GenerationStats(1, 1.seconds, 1, 1.seconds)))
        }
        override fun stop() = Unit
        override suspend fun formatChat(messages: List<ChatMessage>): ChatPrompt? = null
        override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean) =
            ToolChatPrompt(messages.joinToString("\n") { it.content }, ToolFormat(0, "", "", supportsTools = true, grammar = null, stops = emptyList()))
        override fun parseReply(format: ToolFormat, text: String, partial: Boolean) = ParsedReply(text, "", emptyList())
        override fun bruceToolGrammar(tools: List<ToolDefinition>): ToolGrammar? = null
        override fun templateSupportsTools(template: String, bosToken: String?, eosToken: String?): Boolean? = null
        override suspend fun countTokens(prompt: String): Int = prompt.length
        override fun contextLength() = 4096
        override fun mediaMarker() = marker
        override suspend fun imageTokens(image: RgbImage): Int? {
            imageTokenCalls++
            return if (image.width == 9) null else 100
        }
    }

    private class Images : ImageSource {
        val stored = mapOf("a.jpg" to RgbImage(1, 1, byteArrayOf(1, 2, 3)), "b.jpg" to RgbImage(2, 1, ByteArray(6)), "odd.jpg" to RgbImage(9, 1, ByteArray(27)))
        override fun rgb(name: String) = stored[name]
        override fun jpeg(name: String): ByteArray? = null
    }

    private val engine = SeeingEngine()
    private val backend = LocalBackend(engine, Images(), Dispatchers.Unconfined)

    private fun chat(vararg messages: ToolChatMessage) = BackendChat("sys", messages.toList(), emptyList())

    private fun run(chat: BackendChat): Pair<Prepared.Ready, List<StepEvent>> = runBlocking {
        val ready = backend.prepare(chat) as Prepared.Ready
        ready to backend.generate(ready, 100, 0f).toList()
    }

    @Test
    fun imagesBecomeMarkersInOrderAndGoWithTheRequest() {
        val (ready, events) = run(
            chat(
                ToolChatMessage(ChatRole.USER, "First <img> sneaky", images = listOf("a.jpg")),
                ToolChatMessage(ChatRole.ASSISTANT, "ok"),
                ToolChatMessage(ChatRole.USER, "and these", images = listOf("b.jpg", "a.jpg")),
            ),
        )

        val request = engine.requests.single()
        assertEquals("sys\n<img>\nFirst  sneaky\nok\n<img><img>\nand these", request.prompt)
        assertEquals(listOf(1, 2, 1), request.images.map { it.width })
        assertEquals(null, request.checkpointPrefix)
        assertEquals(request.prompt.length + 300, ready.tokens)
        assertTrue(events.last() is StepEvent.Done)
        assertTrue(runBlocking { backend.acceptsImages() })
    }

    @Test
    fun imageCountsAreWorkedOutOncePerImage() {
        runBlocking {
            val c = chat(ToolChatMessage(ChatRole.USER, "x", images = listOf("a.jpg")))
            backend.prepare(c)
            backend.prepare(c)
        }
        assertEquals(1, engine.imageTokenCalls)
    }

    @Test
    fun imagesTheModelCannotTakeAreDescribedInstead() {
        run(chat(ToolChatMessage(ChatRole.USER, "look", images = listOf("gone.jpg", "odd.jpg", "a.jpg"))))

        val request = engine.requests.single()
        assertEquals("sys\n<img>\n(2 images were attached that you cannot see.)\nlook", request.prompt)
        assertEquals(1, request.images.size)
    }

    @Test
    fun aModelThatCannotSeeIsToldOfTheImage() {
        engine.marker = null
        run(chat(ToolChatMessage(ChatRole.USER, "look <img>", images = listOf("a.jpg"))))

        val request = engine.requests.single()
        assertEquals("sys\n(An image was attached that you cannot see.)\nlook <img>", request.prompt)
        assertTrue(request.images.isEmpty())
        assertFalse(runBlocking { backend.acceptsImages() })
    }
}
