package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/**
 * Tool calls with a real model, in its own format and in Bruce's (ADR 0001). Needs
 * Qwen3.5-0.8B-Q8_0.gguf (TASK-033's model, SHA-256 37ae482d…814f) in the app's
 * files/test-models, copied there with `run-as`; see docs/building.md.
 */
@ManualOnly("needs a real model copied onto the phone")
@RunWith(AndroidJUnit4::class)
class ToolCallDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)
    private val clock = ToolDefinition("get_datetime", "Get the current date, time, day of the week and time zone on this phone.", """{"type":"object","properties":{}}""")
    private val calculator = ToolDefinition(
        "calculate",
        "Evaluate an arithmetic expression exactly. Supports + - * / ^ %, parentheses and decimals.",
        """{"type":"object","properties":{"expression":{"type":"string","description":"The arithmetic expression"}},"required":["expression"]}""",
    )
    private val tools = listOf(clock, calculator)
    private val system = "You are Bruce, a helpful assistant running on the user's Android phone. Be brief."

    @Before
    fun load() = runBlocking {
        val model = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "test-models/Qwen3.5-0.8B-Q8_0.gguf")
        assertTrue("copy the model first: $model", model.isFile)
        assertTrue(engine.loadModel(model, LoadConfig(contextLength = 4096, threads = 4, backend = BackendPreference.CPU)) is LoadResult.Loaded)
    }

    @After
    fun unload() = runBlocking {
        engine.unloadModel()
        nativeThread.close()
    }

    private fun reply(prompt: String, format: ToolFormat): String = runBlocking {
        engine.generate(GenerationRequest(prompt, maxTokens = 160, temperature = 0f, grammar = format.grammar, stops = format.stops)).toList()
            .filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
    }

    private fun native(question: String): ParsedReply = runBlocking {
        val prompt = engine.formatToolChat(
            listOf(ToolChatMessage(ToolChatRole.SYSTEM, system), ToolChatMessage(ToolChatRole.USER, question)),
            tools,
        )
        assertNotNull(prompt)
        assertTrue("Qwen3.5's template supports tools", prompt!!.format.supportsTools)
        engine.parseReply(prompt.format, reply(prompt.text, prompt.format))!!
    }

    @Test
    fun ownFormatCallsTheRightToolWithArguments() {
        assertEquals("get_datetime", native("What time is it?").toolCalls.single().name)
        val call = native("What is 1234 multiplied by 5678?").toolCalls.single()
        assertEquals("calculate", call.name)
        assertTrue(call.argumentsJson, call.argumentsJson.contains("1234") && call.argumentsJson.contains("5678"))
    }

    @Test
    fun ownFormatAnswersDirectlyWhenNoToolIsNeeded() {
        val reply = native("Who wrote Pride and Prejudice?")
        assertTrue(reply.toolCalls.isEmpty())
        assertTrue(reply.content, reply.content.contains("Austen"))
    }

    @Test
    fun bruceFormatWithItsGrammarCallsTheTool() = runBlocking {
        val grammar = engine.bruceToolGrammar(tools)
        assertNotNull(grammar)
        val prompt = engine.formatChat(
            listOf(ChatMessage(ChatRole.SYSTEM, system + "\n\n" + BruceToolFormat.instructions(tools)), ChatMessage(ChatRole.USER, "What time is it?")),
        )!!
        val format = ToolFormat(0, "", "", supportsTools = false, grammar = grammar, stops = emptyList())

        val parsed = BruceToolFormat.parse(reply(prompt.text, format))

        assertEquals("get_datetime", parsed.toolCalls.single().name)
    }
}
