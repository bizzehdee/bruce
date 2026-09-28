package com.bizzeh.bruce.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.inference.ChatMessage
import com.bizzeh.bruce.inference.ChatPrompt
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.EngineCapabilities
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.ParsedReply
import com.bizzeh.bruce.inference.StopReason
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolChatPrompt
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.inference.ToolFormat
import com.bizzeh.bruce.inference.ToolGrammar
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The agent loop with the real registry, policy engine and skill states (in-memory Room); only the model is scripted. */
@RunWith(RobolectricTestRunner::class)
class BruceRuntimeTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private val states = SkillStateStore(database.policy())
    private val ran = mutableListOf<String>()

    private fun skill(id: String, default: SkillState, vararg parameters: Parameter) = Skill(
        id, 1, "Test skill $id.", InputSchema(parameters.toList()), setOf(Capability.TIME), default,
    ) { arguments ->
        ran += id
        SkillOutcome.Done("$id says ${arguments.string("expression") ?: "hi"}")
    }

    private val clock = skill("get_datetime", SkillState.ACCEPTED)
    private val calculator = skill("calculate", SkillState.ACCEPTED, Parameter("expression", ParameterType.STRING, "Expression"))
    private val secret = skill("read_secret", SkillState.DECLINED)
    private val writer = skill("write_note", SkillState.ASK)
    private val registry = SkillRegistry(listOf(clock, calculator, secret, writer))
    private val policy = PolicyEngine(registry, states, ToolOutput(), permissionGranted = { true }, scope = { ScopeCheck.InScope })
    private val engine = ScriptedEngine()

    private fun runtime(maxToolCalls: Int = 5, maxDuration: kotlin.time.Duration = 10.seconds) =
        BruceRuntime(engine, registry, states, policy, temperature = { 0.3f }, personality = { "You are Milo.\n" }, maxToolCalls = maxToolCalls, maxDuration = maxDuration)

    private val question = listOf(ToolChatMessage(ChatRole.USER, "What time is it?"))

    @After
    fun tearDown() = database.close()

    private fun events(runtime: BruceRuntime = runtime()) = runBlocking { runtime.respond(question).toList() }

    @Test
    fun aToolCallRunsAndTheModelAnswersWithItsResult() {
        engine.steps += Step("<call>", ParsedReply("", "", listOf(ToolCall("get_datetime", "{}"))))
        engine.steps += Step("It is noon.", ParsedReply("It is noon.", "", emptyList()))

        val events = events()

        assertEquals(listOf("get_datetime"), ran)
        val result = events.filterIsInstance<RuntimeEvent.ToolResult>().single()
        assertTrue(result.ran)
        assertEquals("call_1", result.call.id)
        assertEquals("get_datetime says hi", JSONObject(result.resultJson).getString("untrusted_data"))
        val finished = events.last() as RuntimeEvent.Finished
        assertEquals(listOf(ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT), finished.messages.map { it.role })
        assertEquals("call_1", finished.messages[1].toolCallId)
        assertEquals("It is noon.", finished.messages[2].content)
        assertEquals(listOf("<call>", "It is noon."), events.filterIsInstance<RuntimeEvent.Text>().map { it.text })

        val second = engine.formatted[1]
        assertEquals(ChatRole.SYSTEM, second.first().role)
        assertTrue("the personality leads the system prompt", second.first().content.startsWith("You are Milo.\n\n" + BruceRuntime.GUIDANCE))
        assertEquals("the model sees its call and the result", listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL), second.drop(1).map { it.role })
        assertEquals(0.3f, engine.requests.first().temperature)
        assertEquals(engine.grammar, engine.requests.first().grammar)
        assertEquals(listOf("<|end|>"), engine.requests.first().stops)
    }

    @Test
    fun declinedSkillsAreNotOfferedAndAreRefusedIfCalled() {
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("read_secret", "{}", "own-id"))))
        engine.steps += Step("Sorry.", ParsedReply("Sorry.", "", emptyList()))

        val events = events()

        assertEquals(listOf("get_datetime", "calculate", "write_note"), engine.offered.first().map { it.name })
        val refused = events.filterIsInstance<RuntimeEvent.ToolResult>().single()
        assertFalse(refused.ran)
        assertEquals("own-id", refused.call.id)
        assertEquals("CAPABILITY_DISABLED", JSONObject(refused.resultJson).getString("code"))
        assertTrue(ran.isEmpty())
    }

    @Test
    fun invalidArgumentsGoBackToTheModelAsARefusal() {
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("calculate", """{"expression": 42}"""))))
        engine.steps += Step("Oops.", ParsedReply("Oops.", "", emptyList()))

        val refusal = events().filterIsInstance<RuntimeEvent.ToolResult>().single()

        assertEquals("INVALID_ARGUMENTS", JSONObject(refusal.resultJson).getString("code"))
    }

    @Test
    fun askStopsTheTurnWithAPendingConfirmation() {
        engine.steps += Step("", ParsedReply("Saving.", "", listOf(ToolCall("write_note", "{}"))))

        val events = events()

        val pending = events.filterIsInstance<RuntimeEvent.NeedsConfirmation>().single()
        assertEquals("write_note", pending.decision.request.skill.id)
        assertEquals(listOf(ChatRole.ASSISTANT), (events.last() as RuntimeEvent.Finished).messages.map { it.role })
        assertTrue(ran.isEmpty())
    }

    @Test
    fun tooManyToolCallsEndTheTurn() {
        repeat(3) { engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("get_datetime", "{}")))) }

        val failed = events(runtime(maxToolCalls = 2)).last() as RuntimeEvent.Failed

        assertEquals(RuntimeError.TOO_MANY_TOOL_CALLS, failed.error)
        assertEquals(2, ran.size)
    }

    @Test
    fun aTurnThatRunsTooLongIsStopped() {
        engine.hang = true

        val failed = events(runtime(maxDuration = 50.milliseconds)).last() as RuntimeEvent.Failed

        assertEquals(RuntimeError.TIMED_OUT, failed.error)
        assertEquals(1, engine.stops)
    }

    @Test
    fun generationFailuresEndTheTurn() {
        mapOf(
            GenerationError.PROMPT_TOO_LONG to RuntimeError.CONVERSATION_TOO_LONG,
            GenerationError.DECODE_FAILED to RuntimeError.GENERATION_FAILED,
            GenerationError.GRAMMAR_REJECTED to RuntimeError.GENERATION_FAILED,
            GenerationError.NO_MODEL_LOADED to RuntimeError.NO_MODEL_LOADED,
        ).forEach { (error, expected) ->
            engine.steps += Step("", ParsedReply("", "", emptyList()), failure = error)
            assertEquals(expected, (events().last() as RuntimeEvent.Failed).error)
        }
        engine.noModel = true
        assertEquals(RuntimeError.NO_MODEL_LOADED, (events().last() as RuntimeEvent.Failed).error)
    }

    @Test
    fun templatesWithoutToolSupportUseBrucesFormat() {
        engine.nativeTools = false
        engine.steps += Step("""<tool_call>{"name": "calculate", "arguments": {"expression": "6*7"}}</tool_call>""", null)
        engine.steps += Step("42.", null)

        val events = events()

        assertEquals(listOf("calculate"), ran)
        assertEquals("calculate says 6*7", JSONObject(events.filterIsInstance<RuntimeEvent.ToolResult>().single().resultJson).getString("untrusted_data"))
        val system = engine.chats.first().first()
        assertEquals(ChatRole.SYSTEM, system.role)
        assertTrue(system.content.contains("<tools>"))
        assertTrue(system.content.startsWith("You are Milo."))
        val second = engine.chats[1]
        assertTrue(second[2].content.contains("<tool_call>{\"name\": \"calculate\""))
        assertEquals(ChatRole.USER, second[3].role)
        assertTrue(second[3].content.startsWith("<tool_response>"))
        assertEquals(engine.bruceGrammar, engine.requests.first().grammar)
        assertTrue((events.last() as RuntimeEvent.Finished).messages.last().content == "42.")
    }

    @Test
    fun withNoSkillsOfferedTheModelsFormatIsUsedAsIs() = runBlocking {
        listOf(clock, calculator, writer).forEach { states.set(it, SkillState.DECLINED) }
        engine.nativeTools = false
        engine.steps += Step("Hello.", ParsedReply("Hello.", "", emptyList()))

        val events = runtime().respond(question).toList()

        assertTrue(engine.chats.isEmpty())
        assertTrue(engine.offered.first().isEmpty())
        assertEquals("Hello.", (events.last() as RuntimeEvent.Finished).messages.single().content)
    }

    private data class Step(val text: String, val parsed: ParsedReply?, val failure: GenerationError? = null)

    private inner class ScriptedEngine : InferenceEngine {
        val steps = ArrayDeque<Step>()
        val formatted = mutableListOf<List<ToolChatMessage>>()
        val offered = mutableListOf<List<ToolDefinition>>()
        val chats = mutableListOf<List<ChatMessage>>()
        val requests = mutableListOf<GenerationRequest>()
        val grammar = ToolGrammar("native")
        val bruceGrammar = ToolGrammar("bruce")
        var nativeTools = true
        var noModel = false
        var hang = false
        var stops = 0
        private var current: Step? = null

        override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean): ToolChatPrompt? {
            if (noModel) return null
            formatted += messages
            offered += tools
            return ToolChatPrompt("prompt", ToolFormat(3, "p", "", nativeTools, grammar, listOf("<|end|>")))
        }

        override suspend fun formatChat(messages: List<ChatMessage>): ChatPrompt? {
            chats += messages
            return ChatPrompt("chat prompt", false)
        }

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> {
            requests += request
            if (hang) return flow { awaitCancellation() }
            val step = steps.removeFirst().also { current = it }
            step.failure?.let { return flowOf(GenerationEvent.Failed(it)) }
            return flowOf(GenerationEvent.Token(step.text), GenerationEvent.Completed(StopReason.END_OF_GENERATION, GenerationStats(1, 1.seconds, 1, 1.seconds)))
        }

        override fun parseReply(format: ToolFormat, text: String, partial: Boolean): ParsedReply? = current?.parsed

        override fun bruceToolGrammar(tools: List<ToolDefinition>): ToolGrammar = bruceGrammar

        override fun stop() {
            stops++
        }

        override suspend fun loadModel(file: File, config: LoadConfig): LoadResult = error("not used")
        override suspend fun unloadModel() = error("not used")
        override fun getCapabilities(): EngineCapabilities = error("not used")
        override fun getModelInfo(): ModelInfo? = null
    }
}
