package com.bizzeh.bruce.inference

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class LlamaCppEngineGenerationTest {
    @TempDir
    lateinit var dir: File

    private val llama = FakeLlamaApi()
    private val dispatcher = StandardTestDispatcher()
    private val clock = TestTimeSource()
    private val engine = LlamaCppEngine(llama, dispatcher, clock)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private suspend fun loadModel() {
        val file = File(dir, "m.gguf").apply { writeBytes("GGUF".toByteArray() + ByteArray(16)) }
        engine.loadModel(file)
    }

    private fun List<GenerationEvent>.text() =
        filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }

    private fun List<GenerationEvent>.completion() = last() as GenerationEvent.Completed

    @Test
    fun streamsTokensUntilEndOfGeneration() = test {
        loadModel()
        llama.script("Once", " upon", " a time")

        val events = engine.generate(GenerationRequest("Tell a story", temperature = 0f, seed = 7)).toList()

        assertEquals(
            listOf("Once", " upon", " a time"),
            events.filterIsInstance<GenerationEvent.Token>().map { it.text },
        )
        assertEquals(StopReason.END_OF_GENERATION, events.completion().reason)
        assertEquals(Triple(20L, 0f, 7), llama.generationRequest)
        assertArrayEquals("Tell a story".toByteArray(), llama.lastPrompt)
        assertEquals(listOf(30L), llama.endedGenerations)
    }

    @Test
    fun stopsAtMaxTokens() = test {
        loadModel()
        llama.script("a", "b", "c", "d")

        val events = engine.generate(GenerationRequest("p", maxTokens = 2)).toList()

        assertEquals("ab", events.text())
        assertEquals(StopReason.MAX_TOKENS, events.completion().reason)
        assertEquals(2, events.completion().stats.generatedTokens)
    }

    @Test
    fun reportsContextFull() = test {
        loadModel()
        llama.script("a", end = LlamaApi.CONTEXT_FULL)

        assertEquals(StopReason.CONTEXT_FULL, engine.generate(GenerationRequest("p")).toList().completion().reason)
    }

    @Test
    fun stopEndsGenerationAfterCurrentToken() = test {
        loadModel()
        llama.script("a", "b", "c")
        var calls = 0
        llama.onNextToken = { if (++calls == 2) engine.stop() }

        val events = engine.generate(GenerationRequest("p")).toList()

        assertEquals("ab", events.text())
        assertEquals(StopReason.STOPPED, events.completion().reason)
    }

    @Test
    fun stopBeforeGenerationDoesNotAffectNextGeneration() = test {
        loadModel()
        engine.stop()
        llama.script("a")

        assertEquals(StopReason.END_OF_GENERATION, engine.generate(GenerationRequest("p")).toList().completion().reason)
    }

    @Test
    fun cancellingCollectionReleasesNativeGeneration() = test {
        loadModel()
        llama.script("a", "b", "c")

        engine.generate(GenerationRequest("p")).take(1).toList()
        engine.unloadModel()

        assertEquals(listOf(30L), llama.endedGenerations)
        assertEquals(listOf(10L), llama.freedModels)
    }

    @Test
    fun characterSplitAcrossTokensIsReassembled() = test {
        loadModel()
        val dog = "🐕".toByteArray()
        llama.tokenPiece(dog.copyOfRange(0, 2))
        llama.tokenPiece(dog.copyOfRange(2, 4))

        assertEquals("🐕", engine.generate(GenerationRequest("p")).toList().text())
    }

    @Test
    fun incompleteCharacterAtEndBecomesReplacementCharacter() = test {
        loadModel()
        llama.tokenPiece("🐕".toByteArray().copyOfRange(0, 2))

        assertEquals("�", engine.generate(GenerationRequest("p")).toList().text())
    }

    @Test
    fun withoutModelFails() = test {
        assertEquals(
            listOf(GenerationEvent.Failed(GenerationError.NO_MODEL_LOADED)),
            engine.generate(GenerationRequest("p")).toList(),
        )
    }

    @Test
    fun promptTooLongFails() = test {
        loadModel()
        llama.promptResult = LlamaApi.PROMPT_TOO_LONG

        assertEquals(
            listOf(GenerationEvent.Failed(GenerationError.PROMPT_TOO_LONG)),
            engine.generate(GenerationRequest("p")).toList(),
        )
        assertEquals(listOf(30L), llama.endedGenerations)
    }

    @Test
    fun promptDecodeFailureFails() = test {
        loadModel()
        llama.promptResult = LlamaApi.DECODE_FAILED

        assertEquals(GenerationEvent.Failed(GenerationError.DECODE_FAILED), engine.generate(GenerationRequest("p")).first())
    }

    @Test
    fun tokenDecodeFailureFailsAfterEarlierTokens() = test {
        loadModel()
        llama.script("a", end = LlamaApi.DECODE_FAILED)

        val events = engine.generate(GenerationRequest("p")).toList()

        assertEquals(listOf(GenerationEvent.Token("a"), GenerationEvent.Failed(GenerationError.DECODE_FAILED)), events)
    }

    @Test
    fun measuresPromptAndGenerationSpeed() = test {
        loadModel()
        llama.promptResult = 10
        llama.script("a", "b", "c", "d")
        var tokenCalls = 0
        llama.onNextToken = { if (tokenCalls++ == 0) clock += 2.seconds }

        val stats = engine.generate(GenerationRequest("p")).toList().completion().stats

        assertEquals(10, stats.promptTokens)
        assertEquals(4, stats.generatedTokens)
        assertEquals(2.seconds, stats.generationDuration)
        assertEquals(2.0, stats.generationTokensPerSecond)
    }

    @Test
    fun formatChatPassesRolesAndUtf8Content() = test {
        loadModel()

        val prompt = engine.formatChat(listOf(ChatMessage(ChatRole.SYSTEM, "Be brief"), ChatMessage(ChatRole.USER, "Héllo 🐕")))

        assertEquals(ChatPrompt("<|im_start|>user", usedFallbackTemplate = false), prompt)
        assertEquals(Triple(10L, listOf("system", "user"), listOf("Be brief", "Héllo 🐕")), llama.chatRequest)
    }

    @Test
    fun formatChatReportsFallbackTemplate() = test {
        loadModel()
        llama.chatResult = byteArrayOf(1) + "x".toByteArray()

        assertEquals(ChatPrompt("x", usedFallbackTemplate = true), engine.formatChat(listOf(ChatMessage(ChatRole.USER, "hi"))))
    }

    @Test
    fun formatChatWithoutModelOrOnFailureIsNull() = test {
        assertNull(engine.formatChat(listOf(ChatMessage(ChatRole.USER, "hi"))))
        loadModel()
        llama.chatResult = null
        assertNull(engine.formatChat(listOf(ChatMessage(ChatRole.USER, "hi"))))
    }

    @Test
    fun statsPerSecondHandlesZeroDuration() {
        val stats = GenerationStats(10, Duration.ZERO, 4, 500.milliseconds)

        assertEquals(0.0, stats.promptTokensPerSecond)
        assertEquals(8.0, stats.generationTokensPerSecond)
    }

    @Test
    fun requestRejectsInvalidValues() {
        assertThrows<IllegalArgumentException> { GenerationRequest("p", maxTokens = 0) }
        assertThrows<IllegalArgumentException> { GenerationRequest("p", temperature = -0.1f) }
        assertTrue(GenerationRequest("p", temperature = 0f).temperature == 0f)
    }
}
