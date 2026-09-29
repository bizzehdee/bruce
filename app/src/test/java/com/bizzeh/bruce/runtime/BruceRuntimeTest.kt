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
import com.bizzeh.bruce.skills.ResourceScope
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
import org.junit.Assert.assertNull
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
    private val policy = PolicyEngine(registry, states, ToolOutput(), permissionGranted = { true }, scope = { ScopeCheck.InScope() })
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
    fun callsAfterOneAwaitingApprovalGetAResultButDoNotRun() {
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("write_note", "{}", "a"), ToolCall("get_datetime", "{}", "b"))))

        val events = events()

        val skipped = events.filterIsInstance<RuntimeEvent.ToolResult>().single()
        assertEquals("b", skipped.call.id)
        assertEquals("CONFIRMATION_REQUIRED", JSONObject(skipped.resultJson).getString("code"))
        assertTrue(ran.isEmpty())
    }

    @Test
    fun approvingRunsTheCallAndDecliningDoesNot() = runBlocking {
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("write_note", "{}", "a"))))
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("write_note", "{}", "b"))))
        val runtime = runtime()
        val first = runtime.respond(question).toList().filterIsInstance<RuntimeEvent.NeedsConfirmation>().single()
        val second = runtime.respond(question).toList().filterIsInstance<RuntimeEvent.NeedsConfirmation>().single()

        val approved = runtime.answer(first.call, first.decision, approved = true)
        val declined = runtime.answer(second.call, second.decision, approved = false)

        assertTrue(approved.ran)
        assertEquals(listOf("write_note"), ran)
        assertEquals(false, declined.ran)
        assertEquals("USER_DENIED", JSONObject(declined.resultJson).getString("code"))
    }

    @Test
    fun fileSkillsAreOfferedWithTheGrantNamesOnlyOnceSomethingIsGranted() = runBlocking {
        val reader = Skill("read_file", 1, "Read.", InputSchema(), setOf(Capability.FILE_READ), SkillState.ACCEPTED, scope = ResourceScope.GRANTED_FILES) { SkillOutcome.Done("") }
        val files = SkillRegistry(listOf(clock, reader))
        var names = listOf("Documents", "report.pdf")
        fun system(registry: SkillRegistry): String {
            engine.steps += Step("", ParsedReply("Hi.", "", emptyList()))
            val runtime = BruceRuntime(engine, registry, states, policy, temperature = { 0f }, personality = { "P" }, grantNames = { names })
            runBlocking { runtime.respond(question).toList() }
            return engine.formatted.last().first().content
        }

        assertTrue(system(files).endsWith("every path starts with one of these names): Documents, report.pdf."))
        assertEquals(listOf("get_datetime", "read_file"), engine.offered.last().map { it.name })
        names = emptyList()
        assertTrue(system(files).endsWith(BruceRuntime.NO_GRANTS))
        assertEquals("with nothing granted, file skills are not offered", listOf("get_datetime"), engine.offered.last().map { it.name })
        assertEquals("P\n\n" + BruceRuntime.GUIDANCE, system(SkillRegistry(listOf(clock))))
    }

    /** Ten exchanges before the newest question, each message costing 100 "tokens". */
    private fun longChat(): List<ToolChatMessage> {
        engine.tokensPerMessage = { 100 }
        engine.contextLength = 1000
        return (1..10).flatMap { listOf(ToolChatMessage(ChatRole.USER, "q$it"), ToolChatMessage(ChatRole.ASSISTANT, "a$it")) } +
            ToolChatMessage(ChatRole.USER, "newest")
    }

    @Test
    fun theOldestMessagesAreDroppedUntilThePromptFits() = runBlocking {
        engine.steps += Step("", ParsedReply("Hi.", "", emptyList()))
        val history = longChat()

        val events = runtime().respond(history).toList()

        assertTrue(events.last() is RuntimeEvent.Finished)
        val sent = engine.formatted.last()
        // 1,000 tokens less a 250-token reply reserve is 750: the system prompt and five messages fit,
        // and cuts fall only where a question starts.
        assertEquals(listOf("q9", "a9", "q10", "a10", "newest"), sent.drop(1).map { it.content })
        assertEquals(ChatRole.SYSTEM, sent.first().role)
    }

    @Test
    fun measuringReportsUseAndHowManyWereDropped() = runBlocking {
        val use = runtime().measure(longChat())!!

        assertEquals(ContextUse(used = 600, total = 1000, dropped = 16, limit = 750), use)
        assertTrue(use.nearlyFull)
        assertEquals(false, ContextUse(used = 600, total = 1000, dropped = 0, limit = 750).nearlyFull)
        assertTrue(ContextUse(used = 640, total = 1000, dropped = 0, limit = 750).nearlyFull)
        engine.noModel = true
        assertNull(runtime().measure(longChat()))
    }

    @Test
    fun anEmptyChatIsMeasuredWithABlankRequestSinceSomeTemplatesNeedOne() = runBlocking {
        val use = runtime().measure(emptyList())!!
        assertEquals(use.used, runtime().fixedPromptTokens())

        assertEquals(listOf(ChatRole.SYSTEM, ChatRole.USER), engine.formatted.last().map { it.role })
        assertEquals(2, use.used)
    }

    @Test
    fun aCutNeverLeavesAToolResultWithoutItsCall() = runBlocking {
        engine.tokensPerMessage = { 100 }
        engine.contextLength = 500
        val history = listOf(
            ToolChatMessage(ChatRole.USER, "old"),
            ToolChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("get_datetime", "{}", "c1"))),
            ToolChatMessage(ChatRole.TOOL, "12:00", toolCallId = "c1", toolName = "get_datetime"),
            ToolChatMessage(ChatRole.ASSISTANT, "Noon."),
            ToolChatMessage(ChatRole.USER, "newest"),
        )

        val use = runtime().measure(history)!!

        assertEquals(4, use.dropped)
        assertEquals(listOf(ChatRole.SYSTEM, ChatRole.USER), engine.formatted.last().map { it.role })
    }

    @Test
    fun aRequestTooLongOnItsOwnStillFails() = runBlocking {
        engine.tokensPerMessage = { if (it.content == "huge") 5_000 else 1 }
        engine.contextLength = 1000

        val failed = runtime().respond(listOf(ToolChatMessage(ChatRole.USER, "hi"), ToolChatMessage(ChatRole.USER, "huge"))).toList().last() as RuntimeEvent.Failed

        assertEquals(RuntimeError.CONVERSATION_TOO_LONG, failed.error)
    }

    @Test
    fun aSummaryIsWrittenFromATranscriptOfTheMessages() = runBlocking {
        engine.steps += Step("<think>hmm</think>  They planned a picnic.  ", ParsedReply("  They planned a picnic.  ", "hmm", emptyList()))
        val messages = listOf(
            ToolChatMessage(ChatRole.SYSTEM, "Met at noon."),
            ToolChatMessage(ChatRole.USER, "Plan a picnic"),
            ToolChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("get_datetime", "{}", "c1"))),
            ToolChatMessage(ChatRole.TOOL, "12:00", toolCallId = "c1", toolName = "get_datetime"),
            ToolChatMessage(ChatRole.ASSISTANT, "Sure."),
        )

        assertEquals("They planned a picnic.", runtime().summarise(messages))
        val (system, transcript) = engine.formatted.last()
        assertEquals(emptyList<ToolDefinition>(), engine.offered.last())
        assertEquals(BruceRuntime.SUMMARY_INSTRUCTIONS, system.content)
        assertEquals(
            "Earlier summary: Met at noon.\n\nUser: Plan a picnic\n\nAssistant: (used get_datetime)\n\nResult of get_datetime: 12:00\n\nAssistant: Sure.",
            transcript.content,
        )
        assertEquals(BruceRuntime.SUMMARY_TOKENS, engine.requests.last().maxTokens)
    }

    @Test
    fun aSummaryIsAskedForWithoutThinkingAndReasoningAloneIsNoSummary() = runBlocking {
        engine.steps += Step("<think>only thoughts</think>", ParsedReply("", "only thoughts", emptyList()))

        assertNull(runtime().summarise(listOf(ToolChatMessage(ChatRole.USER, "hi"))))
        assertEquals(listOf(false), engine.thinking)
    }

    @Test
    fun aSummaryCannotBeMadeWithoutAModelOrWhenGenerationFails() = runBlocking {
        engine.steps += Step("", null, failure = GenerationError.DECODE_FAILED)
        assertNull(runtime().summarise(listOf(ToolChatMessage(ChatRole.USER, "hi"))))
        engine.noModel = true
        assertNull(runtime().summarise(listOf(ToolChatMessage(ChatRole.USER, "hi"))))
    }

    @Test
    fun anOverlongTranscriptLosesItsOldestPartAndTooLittleGivesUp() = runBlocking {
        engine.steps += Step("Short.", null)
        engine.contextLength = 1000
        var calls = 0
        engine.tokensPerMessage = { if (calls++ < 2) 2_500 else 5 }
        val long = listOf(ToolChatMessage(ChatRole.USER, "x".repeat(1000)))

        assertEquals("Short.", runtime().summarise(long))
        assertTrue(engine.formatted.last()[1].content.length < 1000)

        engine.tokensPerMessage = { 5_000 }
        assertNull(runtime().summarise(long))
    }

    @Test
    fun summariesGoIntoTheSystemPromptAndAreNeverDropped() = runBlocking {
        engine.steps += Step("", ParsedReply("Hi.", "", emptyList()))
        val history = listOf(ToolChatMessage(ChatRole.SYSTEM, "They met.")) + longChat()

        runtime().respond(history).toList()

        val sent = engine.formatted.last()
        assertTrue(sent.first().content.endsWith(BruceRuntime.SUMMARY_LEAD + "They met."))
        assertEquals(1, sent.count { it.role == ChatRole.SYSTEM })
        assertEquals("newest", sent.last().content)
    }

    @Test
    fun aCallWithBrokenArgumentsGoesBackToTheModelAsEmptyArguments() = runBlocking {
        engine.steps += Step("", ParsedReply("Done.", "", emptyList()))
        val history = listOf(
            ToolChatMessage(ChatRole.USER, "What time is it?"),
            ToolChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("get_datetime", "{\"}}", "c1"), ToolCall("calculate", "{\"expression\":\"1+1\"}", "c2"))),
            ToolChatMessage(ChatRole.TOOL, "{\"code\":\"INVALID_ARGUMENTS\"}", toolCallId = "c1", toolName = "get_datetime"),
            ToolChatMessage(ChatRole.TOOL, "2", toolCallId = "c2", toolName = "calculate"),
            ToolChatMessage(ChatRole.USER, "And now?"),
        )

        runtime().respond(history).toList()

        val calls = engine.formatted.last().single { it.toolCalls.isNotEmpty() }.toolCalls
        assertEquals(listOf("{}", "{\"expression\":\"1+1\"}"), calls.map { it.argumentsJson })
    }

    @Test
    fun aTemplateFailureIsAGenerationFailureNotAMissingModel() = runBlocking {
        engine.templateFails = true

        val failed = runtime().respond(question).toList().last() as RuntimeEvent.Failed

        assertEquals(RuntimeError.GENERATION_FAILED, failed.error)
        assertNull(runtime().measure(question))
    }

    @Test
    fun aRepeatedCallGetsTheEarlierResultWithANoteAndKeepsTheSkills() = runBlocking {
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("get_datetime", "{}", "a"))))
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("get_datetime", " {} ", "b"))))
        engine.steps += Step("", ParsedReply("It is noon.", "", emptyList()))

        val events = events()

        assertEquals(listOf("get_datetime"), ran)
        val (first, second) = events.filterIsInstance<RuntimeEvent.ToolResult>().map { JSONObject(it.resultJson) }
        assertEquals(first.getString("untrusted_data"), second.getString("untrusted_data"))
        assertEquals(BruceRuntime.REPEAT_NOTE, second.getString("note"))
        assertEquals("the prompt keeps its skills, so the prompt cache still applies", listOf(true, true, true), engine.offered.map { it.isNotEmpty() })
        assertTrue(events.last() is RuntimeEvent.Finished)
    }

    @Test
    fun aRepeatedRefusedCallIsGuardedToo() = runBlocking {
        repeat(3) { engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("calculate", "{\"wrong\":1}")))) }
        engine.steps += Step("", ParsedReply("I could not work it out.", "", emptyList()))

        val events = events()

        assertEquals(listOf(true, true, true, false), engine.offered.map { it.isNotEmpty() })
        assertEquals(BruceRuntime.REPEAT_NOTE, JSONObject(events.filterIsInstance<RuntimeEvent.ToolResult>()[1].resultJson).getString("note"))
        assertTrue(events.last() is RuntimeEvent.Finished)
    }

    @Test
    fun aSecondRepeatTakesTheSkillsAwaySoTheModelMustAnswer() = runBlocking {
        repeat(3) { engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("get_datetime", "{}")))) }
        engine.steps += Step("", ParsedReply("It is noon.", "", emptyList()))

        val events = events()

        assertEquals(listOf("get_datetime"), ran)
        assertEquals(listOf(true, true, true, false), engine.offered.map { it.isNotEmpty() })
        assertTrue(events.last() is RuntimeEvent.Finished)
    }

    @Test
    fun recalledFactsGoIntoTheSystemPromptAsNotes() = runBlocking {
        engine.steps += Step("", ParsedReply("Hi.", "", emptyList()))

        runtime().respond(question, memory = listOf("I live in Leeds", "My dog is Bruce")).toList()

        assertTrue("first in the prompt, about the user", engine.formatted.last().first().content.startsWith(BruceRuntime.MEMORY_LEAD + "\n- The user lives in Leeds\n- The user's dog is Bruce\n\nYou are Milo."))
    }

    @Test
    fun theFactPassContinuesTheChatAndMarksWhereTheChatEnds() = runBlocking {
        engine.steps += Step("", ParsedReply("<think>\n\n</think>\n\n- My name is Sam\n* I live in Leeds\n- I like hiking and adventures\n- You are from the North\n- \"I like tea.\"\n- The user likes tea\n</think>\nNONE", "", emptyList()))
        val history = listOf(ToolChatMessage(ChatRole.USER, "I'm Sam from Leeds and like tea"), ToolChatMessage(ChatRole.ASSISTANT, "Hi Sam!"))

        val facts = runtime().extractFacts(history, memory = listOf("I like tea"))

        assertEquals("markup and facts the user never stated are dropped", listOf("My name is Sam", "I live in Leeds"), facts)
        val asked = engine.formatted[engine.formatted.size - 2]
        assertEquals(history, asked.drop(1).dropLast(1))
        assertEquals(BruceRuntime.FACTS_INSTRUCTIONS, asked.last().content)
        assertTrue("the chat's memory stays in the system prompt", asked.first().content.contains("- The user likes tea"))
        assertEquals("prompt", engine.requests.last().checkpointPrefix)
        assertEquals(0f, engine.requests.last().temperature)
    }

    @Test
    fun theFactPassFindsNothingInNoneOrACallAndFailsOnAGenerationFailure() = runBlocking {
        val history = listOf(ToolChatMessage(ChatRole.USER, "What time is it?"))
        engine.steps += Step("", ParsedReply("NONE.", "", emptyList()))
        assertEquals(emptyList<String>(), runtime().extractFacts(history))
        engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("get_datetime", "{}"))))
        assertEquals(emptyList<String>(), runtime().extractFacts(history))
        engine.steps += Step("", null, failure = GenerationError.DECODE_FAILED)
        assertNull(runtime().extractFacts(history))
        engine.noModel = true
        assertNull(runtime().extractFacts(history))
    }

    @Test
    fun tooManyToolCallsEndTheTurn() {
        repeat(3) { i -> engine.steps += Step("", ParsedReply("", "", listOf(ToolCall("calculate", "{\"expression\":\"$i+1\"}")))) }

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
        var templateFails = false
        var hang = false
        var stops = 0
        var lastCount = 0
        var tokensPerMessage: (ToolChatMessage) -> Int = { 1 }
        private var current: Step? = null

        val thinking = mutableListOf<Boolean>()

        override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean): ToolChatPrompt? {
            if (noModel || templateFails) return null
            thinking += enableThinking
            formatted += messages
            offered += tools
            lastCount = messages.sumOf { tokensPerMessage(it) }
            return ToolChatPrompt("prompt", ToolFormat(3, "p", "", nativeTools, grammar, listOf("<|end|>")))
        }

        override suspend fun formatChat(messages: List<ChatMessage>): ChatPrompt? {
            chats += messages
            lastCount = messages.size
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
        override fun templateSupportsTools(template: String, bosToken: String?, eosToken: String?): Boolean? = error("not used")

        /** Prompts are counted as one token per message they were formatted from. */
        var contextLength = 1_000_000
        override suspend fun countTokens(prompt: String): Int? = if (noModel) null else lastCount
        override fun contextLength(): Int? = if (noModel) null else contextLength

        override fun stop() {
            stops++
        }

        override suspend fun loadModel(file: File, config: LoadConfig): LoadResult = error("not used")
        override suspend fun unloadModel() = error("not used")
        override fun getCapabilities(): EngineCapabilities = error("not used")
        override fun getModelInfo(): ModelInfo? = null
    }
}
