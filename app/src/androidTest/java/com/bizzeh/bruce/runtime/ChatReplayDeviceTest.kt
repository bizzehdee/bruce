package com.bizzeh.bruce.runtime

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.R
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.SavedPrompts
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.deviceEngine
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.policy.SiteAccess
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.settings.NetworkMode
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/**
 * Replays one question through Bruce's runtime with another phone's model, context and skill states,
 * logging every step with its time as it happens (to files/bins/results/<label>.replay.jsonl and to
 * logcat tag BruceReplay), so a reply that never finishes shows where it stopped. Keeps app data.
 *
 * The automatic skills and the web skills run for real; every other skill answers with a fixed stub,
 * so nothing of the owner's is read or written. Prompts are saved and reused as in the app. Sets this
 * phone's network mode to [network], since the web skills check it.
 *
 * Arguments (`-e name value`): model (file in files/bins), context, accepted (skill ids turned on, comma
 * separated), question, runs (default 2: the first saves the prompt, the next reuses it), temperature
 * (default the app's), seeds (comma separated; default 0, as the app), network (a NetworkMode; default GENERAL), label.
 */
@ManualOnly("replays a phone's chat: needs a model pushed to files/bins and the internet")
@RunWith(AndroidJUnit4::class)
class ChatReplayDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val container = (context.applicationContext as BruceApplication).container
    private val dir = File(context.filesDir, "bins")
    private val started = SystemClock.elapsedRealtime()
    private lateinit var out: File

    @Test
    fun replay() {
        val model = File(dir, requireNotNull(arguments.getString("model")) { "-e model <file in files/bins>" })
        val contextLength = requireNotNull(arguments.getString("context")) { "-e context <tokens>" }.toInt()
        val accepted = arguments.getString("accepted").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val question = arguments.getString("question") ?: "what happened today in history"
        val runs = arguments.getString("runs")?.toInt() ?: 2
        val temperature = arguments.getString("temperature")?.toFloat() ?: GenerationRequest.DEFAULT_TEMPERATURE
        val seeds = arguments.getString("seeds")?.split(",")?.map { it.trim().toInt() } ?: listOf(0)
        out = File(dir, "results/${arguments.getString("label") ?: model.nameWithoutExtension}.replay.jsonl").apply { parentFile!!.mkdirs() }

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { nativeThread ->
            val saved = SavedPrompts(File(context.cacheDir, "replay-prompt-states"), "replay")
            val engine = deviceEngine(nativeThread, saved)
            runBlocking {
                container.networkSettings.setMode(NetworkMode.valueOf(arguments.getString("network") ?: NetworkMode.GENERAL.name))
                log("load", JSONObject().put("model", model.name).put("context", contextLength).put("usable_bytes", container.memoryForModels()))
                val loaded = engine.loadModel(model, LoadConfig(contextLength = contextLength))
                log("loaded", JSONObject().put("result", loaded.toString().take(300)))
                check(loaded is LoadResult.Loaded) { "load failed: $loaded" }
                try {
                    for (seed in seeds) {
                        repeat(runs) { run ->
                            turn(Timed(engine, seed), accepted, question, temperature, JSONObject().put("seed", seed).put("run", run))
                        }
                    }
                } finally {
                    engine.unloadModel()
                }
            }
        }
    }

    private suspend fun turn(engine: InferenceEngine, accepted: Set<String>, question: String, temperature: Float, tags: JSONObject) {
        val database = Room.inMemoryDatabaseBuilder(context, PolicyDatabase::class.java).build()
        try {
            val states = SkillStateStore(database.policy())
            val registry = SkillRegistry(container.skills.skills.map(::stubbed))
            registry.skills.filter { it.id in accepted }.forEach { states.set(it, SkillState.ACCEPTED) }
            // No approved sites, as on the phone being replayed: each new site asks, and the harness says yes.
            val sites = SiteAccess(database.policy(), mode = { container.networkSettings.mode.first() })
            val policy = PolicyEngine(registry, states, ToolOutput(), { true }, { ScopeCheck.InScope() }, requirementMet = ::networkOnly, sites = sites)
            val rules = context.resources.openRawResource(R.raw.personality_bruce).bufferedReader().use { it.readText() }
            val runtime = BruceRuntime(
                engine, registry, states, policy, temperature = { temperature }, thinking = { false },
                personality = { rules }, grantNames = { emptyList() }, requirementMet = ::networkOnly,
            )
            log("turn", JSONObject(tags.toString()).put("question", question).put("temperature", temperature.toDouble()))
            val turnStart = SystemClock.elapsedRealtime()
            val history = mutableListOf(ToolChatMessage(ChatRole.USER, question))
            while (true) {
                var pending: RuntimeEvent.NeedsConfirmation? = null
                val added = mutableListOf<ToolChatMessage>()
                var streamed = ""
                runtime.respond(history).collect { event ->
                    when (event) {
                        is RuntimeEvent.Text -> streamed += event.text
                        is RuntimeEvent.Step -> {
                            streamed = ""
                            log(
                                "step",
                                JSONObject().put("content", event.content.take(600)).put("calls", JSONArray(event.toolCalls.map { "${it.name} ${it.argumentsJson}" }))
                                    .put("prompt_tokens", event.stats?.promptTokens).put("prompt_ms", event.stats?.promptDuration?.inWholeMilliseconds)
                                    .put("gen_tokens", event.stats?.generatedTokens).put("gen_ms", event.stats?.generationDuration?.inWholeMilliseconds),
                            )
                        }
                        is RuntimeEvent.ToolResult -> log("tool", JSONObject().put("name", event.call.name).put("ran", event.ran).put("chars", event.resultJson.length).put("result", event.resultJson))
                        is RuntimeEvent.NeedsConfirmation -> pending = event
                        is RuntimeEvent.Finished -> added += event.messages
                        is RuntimeEvent.Failed -> {
                            added += event.messages
                            log("failed", JSONObject().put("error", event.error.name).put("streamed_chars", streamed.length).put("streamed", streamed))
                        }
                    }
                }
                history += added
                val asked = pending ?: break
                log("confirm", JSONObject().put("name", asked.call.name).put("arguments", asked.call.argumentsJson))
                val answer = runtime.answer(asked.call, asked.decision, approved = true)
                history += ToolChatMessage(ChatRole.TOOL, answer.resultJson, toolCallId = asked.call.id, toolName = asked.call.name)
            }
            log("done", JSONObject(tags.toString()).put("wall_ms", SystemClock.elapsedRealtime() - turnStart).put("reply", history.lastOrNull { it.role == ChatRole.ASSISTANT }?.content?.take(600)))
        } finally {
            database.close()
        }
    }

    /** Logs each generation's start, first token and end, with the request's seed set to [seed]. */
    private inner class Timed(private val engine: InferenceEngine, private val seed: Int) : InferenceEngine by engine {
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> {
            var first = true
            return engine.generate(request.copy(seed = seed))
                .onStart {
                    log("generate", JSONObject().put("prompt_chars", request.prompt.length).put("max_tokens", request.maxTokens).put("reuse", request.reusePrompt)
                        .put("saved_prefix_chars", request.savedPrefix?.length).put("checkpoint_chars", request.checkpointPrefix?.length).put("grammar", request.grammar != null))
                }
                .onEach { event ->
                    if (first && event is GenerationEvent.Token) {
                        first = false
                        log("first_token", JSONObject())
                    }
                }
                .onCompletion { cause -> log("generated", JSONObject().put("cause", cause?.toString())) }
        }
    }

    private fun stubbed(skill: Skill): Skill = if (skill.id in REAL) skill else Skill(
        id = skill.id, version = skill.version, description = skill.description, input = skill.input, capabilities = skill.capabilities,
        defaultState = skill.defaultState, highRisk = skill.highRisk, scope = skill.scope, androidPermissions = skill.androidPermissions,
        optionalPermissions = skill.optionalPermissions, requires = skill.requires, site = skill.site, confirmEveryUse = skill.confirmEveryUse,
        companions = skill.companions,
    ) { SkillOutcome.Done(STUB) }

    /** The network allowed; nothing granted, no search key, no file index. */
    private suspend fun networkOnly(requirement: SkillRequirement): Boolean = requirement == SkillRequirement.NETWORK_ALLOWED

    private fun log(type: String, record: JSONObject) {
        record.put("type", type).put("t_ms", SystemClock.elapsedRealtime() - started)
        Log.i(TAG, record.toString().take(LOGCAT_CHARS))
        out.appendText(record.toString() + "\n")
    }

    private companion object {
        const val TAG = "BruceReplay"
        const val STUB = "Done."
        const val LOGCAT_CHARS = 700
        val REAL = setOf(
            "get_datetime", "calculate", "get_battery_status", "get_device_info", "get_storage_status", "get_network_status",
            "fetch_page", "search_wikipedia", "web_search",
        )
    }
}
