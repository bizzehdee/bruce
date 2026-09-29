package com.bizzeh.bruce.inference

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tool formats through the engine (ADR 0001); Robolectric for Android's real org.json. */
@RunWith(RobolectricTestRunner::class)
class LlamaCppEngineToolTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val llama = FakeLlamaApi()
    private val dispatcher = StandardTestDispatcher()
    private val engine = LlamaCppEngine(llama, dispatcher)

    private val clock = ToolDefinition("get_datetime", "Get the date and time.", """{"type":"object","properties":{}}""")
    private val applied = """{"prompt":"<p>","format":7,"parser":"arena","generation_prompt":"<|assistant|>","grammar":"root ::= x",
        "grammar_lazy":true,"grammar_triggers":[{"type":1,"value":"<tool_call>"}],"preserved_tokens":["<tool_call>"],
        "additional_stops":["<|end|>"],"supports_tools":true}"""

    private val model by lazy { temp.newFile("m.gguf").apply { writeBytes("GGUF".toByteArray() + ByteArray(16)) } }

    private suspend fun load() {
        engine.loadModel(model)
    }

    @Test
    fun aFetchedTemplateReplacesTheFilesOwn() = runTest(dispatcher) {
        engine.loadModel(model, LoadConfig(chatTemplate = "{{ tools }}"))
        llama.applyChatReply = applied
        engine.formatToolChat(listOf(ToolChatMessage(ChatRole.USER, "Hi")), listOf(clock))
        assertEquals("{{ tools }}", llama.templateOverride)

        engine.loadModel(model)
        engine.formatToolChat(listOf(ToolChatMessage(ChatRole.USER, "Hi")), listOf(clock))
        assertEquals(null, llama.templateOverride)
    }

    @Test
    fun formatsWithTheModelsTemplateAndReturnsItsFormat() = runTest(dispatcher) {
        load()
        llama.applyChatReply = applied
        val messages = listOf(
            ToolChatMessage(ChatRole.USER, "What time is it?"),
            ToolChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("get_datetime", "{}", "c1"))),
            ToolChatMessage(ChatRole.TOOL, "12:00", toolCallId = "c1", toolName = "get_datetime"),
        )

        val prompt = engine.formatToolChat(messages, listOf(clock))!!
        engine.formatToolChat(messages, listOf(clock))

        assertEquals("<p>", prompt.text)
        assertEquals(7, prompt.format.format)
        assertEquals("arena", prompt.format.parser)
        assertEquals("<|assistant|>", prompt.format.generationPrompt)
        assertTrue(prompt.format.supportsTools)
        assertEquals(listOf("<|end|>"), prompt.format.stops)
        val grammar = JSONObject(prompt.format.grammar!!.json)
        assertEquals("root ::= x", grammar.getString("grammar"))
        assertTrue(grammar.getBoolean("grammar_lazy"))
        assertEquals("<tool_call>", grammar.getJSONArray("grammar_triggers").getJSONObject(0).getString("value"))

        val request = JSONObject(llama.lastApplyChat!!)
        assertFalse(request.getBoolean("enable_thinking"))
        assertEquals("get_datetime", request.getJSONArray("tools").getJSONObject(0).getString("name"))
        val sent = request.getJSONArray("messages")
        assertEquals("tool", sent.getJSONObject(2).getString("role"))
        assertEquals("c1", sent.getJSONObject(2).getString("tool_call_id"))
        assertEquals("c1", sent.getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertFalse(sent.getJSONObject(0).has("tool_calls"))
        assertEquals("templates are made once per model", 1, llama.templateInits)
    }

    @Test
    fun aFormatWithoutGrammarHasNone() = runTest(dispatcher) {
        load()
        llama.applyChatReply = JSONObject(applied).put("grammar", "").put("supports_tools", false).toString()

        val format = engine.formatToolChat(listOf(ToolChatMessage(ChatRole.USER, "hi")), emptyList())!!.format

        assertNull(format.grammar)
        assertFalse(format.supportsTools)
    }

    @Test
    fun noModelUnusableTemplatesOrAFailedApplyGiveNothing() = runTest(dispatcher) {
        val hi = listOf(ToolChatMessage(ChatRole.USER, "hi"))
        assertNull(engine.formatToolChat(hi, emptyList()))

        load()
        assertNull("apply failed", engine.formatToolChat(hi, emptyList()))

        llama.templatesHandle = 0
        engine.unloadModel()
        load()
        llama.applyChatReply = applied
        assertNull("templates unusable", engine.formatToolChat(hi, emptyList()))
    }

    @Test
    fun templatesAreFreedWithTheModel() = runTest(dispatcher) {
        load()
        llama.applyChatReply = applied
        engine.formatToolChat(listOf(ToolChatMessage(ChatRole.USER, "hi")), emptyList())

        engine.unloadModel()

        assertEquals(listOf(40L), llama.freedTemplates)
    }

    @Test
    fun repliesAreParsedWithTheirFormat() {
        llama.parseChatReply = """{"content":"Let me check.","reasoning_content":"","tool_calls":[{"name":"get_datetime","arguments":"{}","id":"x"}]}"""
        val format = ToolFormat(7, "arena", "<|assistant|>", true, null, emptyList())

        val reply = engine.parseReply(format, "Let me check.<tool_call>…", partial = true)!!

        assertEquals("Let me check.", reply.content)
        assertEquals(listOf(ToolCall("get_datetime", "{}", "x")), reply.toolCalls)
        val request = JSONObject(llama.lastParseChat!!)
        assertEquals(7, request.getInt("format"))
        assertEquals("arena", request.getString("parser"))
        assertTrue(request.getBoolean("partial"))
        llama.parseChatReply = null
        assertNull(engine.parseReply(format, "x"))
    }

    @Test
    fun bruceFormatGrammarWrapsTheNativeGrammar() {
        val grammar = JSONObject(engine.bruceToolGrammar(listOf(clock))!!.json)

        assertEquals("root ::= \"x\"", grammar.getString("grammar"))
        assertTrue(grammar.getBoolean("grammar_lazy"))
        assertEquals(BruceToolFormat.OPEN, grammar.getJSONArray("grammar_triggers").getJSONObject(0).getString("value"))
        assertEquals(1, grammar.getJSONArray("grammar_triggers").getJSONObject(0).getInt("type"))
        assertEquals(listOf(BruceToolFormat.OPEN, BruceToolFormat.CLOSE), (0 until 2).map { grammar.getJSONArray("preserved_tokens").getString(it) })
        assertEquals("get_datetime", org.json.JSONArray(llama.lastToolCallGrammar).getJSONObject(0).getString("name"))
        llama.toolCallGrammarReply = null
        assertNull(engine.bruceToolGrammar(listOf(clock)))
    }

    @Test
    fun grammarRequestsUseTheGrammarAndARejectedGrammarFails() = runTest(dispatcher) {
        load()
        val grammar = ToolGrammar("""{"grammar":"root ::= x"}""")

        engine.generate(GenerationRequest("p", grammar = grammar)).toList()
        assertEquals("""{"grammar":"root ::= x"}""", llama.lastGrammar)

        llama.grammarGenerationResult = 0
        val events = engine.generate(GenerationRequest("p", grammar = grammar)).toList()
        assertEquals(listOf(GenerationEvent.Failed(GenerationError.GRAMMAR_REJECTED)), events)
    }

    @Test
    fun aStopStringEndsTheReplyEvenAcrossTokens() = runTest(dispatcher) {
        load()
        listOf("Hi", " there", "<|e", "nd|>", "never").forEach { llama.tokenPiece(it.toByteArray()) }

        val events = engine.generate(GenerationRequest("p", stops = listOf("<|end|>"))).toList()

        assertEquals("Hi there<|end|>", events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text })
        assertEquals(StopReason.END_OF_GENERATION, (events.last() as GenerationEvent.Completed).reason)
    }

    @Test
    fun bruceFormatDescribesToolsAndParsesOneCall() {
        val instructions = BruceToolFormat.instructions(listOf(clock))
        assertTrue(instructions.contains("\"name\":\"get_datetime\""))
        assertTrue(instructions.contains(BruceToolFormat.OPEN))

        assertEquals(
            ParsedReply("Checking.", "", listOf(ToolCall("calculate", """{"expression":"6*7"}"""))),
            BruceToolFormat.parse("""Checking.<tool_call>{"name": "calculate", "arguments": {"expression": "6*7"}}</tool_call>"""),
        )
        assertEquals("unclosed at the end", "get_datetime", BruceToolFormat.parse("""<tool_call>{"name":"get_datetime"}""").toolCalls.single().name)
        assertEquals("{}", BruceToolFormat.parse("""<tool_call>{"name":"get_datetime"}</tool_call>""").toolCalls.single().argumentsJson)
        assertEquals(ParsedReply("Just text.", "", emptyList()), BruceToolFormat.parse(" Just text. "))
        assertEquals("not a call object: left as text", emptyList<ToolCall>(), BruceToolFormat.parse("<tool_call>not json</tool_call>").toolCalls)
    }
}
