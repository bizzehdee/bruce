package com.bizzeh.bruce.benchmark

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.R
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.hardware.CpuTopology
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.BruceToolFormat
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolChatPrompt
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.inference.deviceEngine
import com.bizzeh.bruce.models.ModelMemory
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.runtime.BruceRuntime
import com.bizzeh.bruce.runtime.ContextBudget
import com.bizzeh.bruce.runtime.RuntimeEvent
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors

/**
 * Model binning harness (TASK-104): runs research/experiments/model-bins-2026-09-30/cases.json
 * through Bruce's own runtime with one model, and writes one JSON line per case to
 * files/bins/results/<label>.<mode>.jsonl. Keeps app data: the model and cases are pushed to
 * files/bins, and skill states live in an in-memory database.
 *
 * Skills on: the skills a fresh install offers (offline, nothing granted), with the three phone
 * status skills a fresh install declines turned on, since cases ask for them. The automatic skills
 * run for real; every other skill answers with a fixed stub, so nothing of the owner's is read or
 * written. Skills off: none offered.
 *
 * Arguments (`-e name value`): model (file name in files/bins), label, mode (cases or sweep),
 * context (cases; default 4096), contexts (sweep; default 2048,4096,8192,16384), skills (on, off
 * or both; default both), cases (cases: ids to run, default all), needle (sweep: false skips the long
 * chat), threads (default: CpuTopology.defaultThreads), onscreen (true puts
 * BenchmarkScreenActivity in front first, as in real use; default false). mode=speed loads the
 * model at context 2048 for each of threads_list (default 1 to the core count) and measures prompt
 * and generation speed, repeats times each (default REPEATS). cool_wait_s caps the wait for a hot
 * battery before each measurement (default MAX_COOL_DOWN_MS); a phone whose battery stays hot on
 * USB power would otherwise wait the whole cap before every case.
 */
@ManualOnly("model binning: slow, needs a model pushed to files/bins")
@RunWith(AndroidJUnit4::class)
class ModelBinDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val container = (context.applicationContext as BruceApplication).container
    private val dir = File(context.filesDir, "bins")

    @Test
    fun run() {
        val model = File(dir, requireNotNull(arguments.getString("model")) { "-e model <file in files/bins>" })
        val label = arguments.getString("label") ?: model.nameWithoutExtension
        val mode = arguments.getString("mode") ?: "cases"
        val threads = arguments.getString("threads")?.toInt() ?: CpuTopology.defaultThreads()
        val out = File(dir, "results/$label.$mode.jsonl").apply { parentFile!!.mkdirs() }
        val screen = if (onScreen) {
            instrumentation.startActivitySync(Intent(context, BenchmarkScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            null
        }
        try {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { nativeThread ->
                val engine = deviceEngine(nativeThread)
                runBlocking {
                    when (mode) {
                        "cases" -> cases(engine, model, label, threads, out)
                        "sweep" -> sweep(engine, model, label, threads, out)
                        "speed" -> speed(engine, model, label, out)
                        else -> error("mode must be cases, sweep or speed")
                    }
                }
            }
        } finally {
            screen?.finish()
        }
    }

    private val onScreen get() = arguments.getString("onscreen") == "true"

    /** Prompt and generation speed by thread count, without the runtime: what the CPU and Android's core placement give. */
    private suspend fun speed(engine: InferenceEngine, model: File, label: String, out: File) {
        val cores = Runtime.getRuntime().availableProcessors()
        val counts = arguments.getString("threads_list")?.split(",")?.map { it.trim().toInt() } ?: (1..cores).toList()
        for (threads in counts) {
            val loaded = engine.loadModel(model, LoadConfig(contextLength = SPEED_CONTEXT, threads = threads, backend = BackendPreference.CPU))
            check(loaded is LoadResult.Loaded) { "load failed: $loaded" }
            try {
                engine.generate(GenerationRequest(SPEED_PROMPT, maxTokens = 8, temperature = 0f, reusePrompt = false)).toList()
                repeat(arguments.getString("repeats")?.toInt() ?: REPEATS) { attempt ->
                    val record = run(label, model, SPEED_CONTEXT, threads, false).put("type", "speed").put("attempt", attempt).put("battery_c", coolDown())
                    val events = engine.generate(GenerationRequest(SPEED_PROMPT, maxTokens = SPEED_TOKENS, temperature = 0f, reusePrompt = false)).toList()
                    val stats = (events.last() as GenerationEvent.Completed).stats
                    append(out, record.put("prompt_tokens", stats.promptTokens).put("prompt_tps", stats.promptTokensPerSecond)
                        .put("gen_tokens", stats.generatedTokens).put("gen_tps", stats.generationTokensPerSecond))
                }
            } finally {
                engine.unloadModel()
            }
        }
    }

    private suspend fun cases(engine: InferenceEngine, model: File, label: String, threads: Int, out: File) {
        val contextLength = arguments.getString("context")?.toInt() ?: DEFAULT_CONTEXT
        val skills = when (val chosen = arguments.getString("skills") ?: "both") {
            "both" -> listOf(true, false)
            else -> listOf(chosen == "on")
        }
        val chosen = arguments.getString("cases")?.split(",")?.map { it.trim() }?.toSet()
        val all = JSONObject(File(dir, "cases.json").readText()).getJSONArray("cases")
        val cases = JSONArray((0 until all.length()).map { all.getJSONObject(it) }.filter { chosen == null || it.getString("id") in chosen })
        // The app refuses to download such a file; loading it anyway can get the test process killed on a small phone.
        val estimate = ModelMemory.estimate((GgufReader.read(model) as GgufReadResult.Read).metadata, contextLength)
        val usable = container.memoryForModels()
        if (estimate.totalBytes > usable) {
            append(out, run(label, model, contextLength, threads, true).put("type", "skipped").put("reason", "does not fit").put("estimate_bytes", estimate.totalBytes).put("usable_bytes", usable))
            return
        }
        val loaded = engine.loadModel(model, LoadConfig(contextLength = contextLength, threads = threads, backend = BackendPreference.CPU))
        check(loaded is LoadResult.Loaded) { "load failed: $loaded" }
        try {
            for (on in skills) {
                val captured = Captured(engine)
                val harness = Harness(captured, on)
                val fixed = harness.runtime.fixedPromptTokens()
                val prompt = run(label, model, contextLength, threads, on).put("type", "prompt").put("fixed_prompt_tokens", fixed)
                    .put("system", captured.system).put("tools", captured.tools)
                // What the app sends instead when the model's template cannot express tools (ADR 0001), for the host screen.
                captured.prompt?.second?.takeIf { it.isNotEmpty() }?.let { tools ->
                    prompt.put("bruce_format", JSONObject().put("instructions", BruceToolFormat.instructions(tools)).put("grammar", engine.bruceToolGrammar(tools)?.let { JSONObject(it.json) }))
                }
                append(out, prompt)
                for (i in 0 until cases.length()) {
                    val case = cases.getJSONObject(i)
                    val turns = case.getJSONArray("turns")
                    val record = run(label, model, contextLength, threads, on).put("type", "case").put("case", case.getString("id")).put("battery_c", coolDown())
                    val done = JSONArray()
                    var history = listOf<ToolChatMessage>()
                    for (t in 0 until turns.length()) {
                        history = history + ToolChatMessage(ChatRole.USER, turns.getString(t))
                        val (added, turn) = harness.turn(history, turns.getString(t))
                        history = history + added
                        done.put(turn)
                    }
                    append(out, record.put("turns", done))
                }
                harness.dispose()
            }
        } finally {
            engine.unloadModel()
        }
    }

    /** Load, memory and speed per context length, and one long chat that asks for a fact from its start. */
    private suspend fun sweep(engine: InferenceEngine, model: File, label: String, threads: Int, out: File) {
        val contexts = (arguments.getString("contexts") ?: "2048,4096,8192,16384").split(",").map { it.trim().toInt() }
        val metadata = (GgufReader.read(model) as GgufReadResult.Read).metadata
        val needle = JSONObject(File(dir, "cases.json").readText()).getJSONObject("needle")
        for (contextLength in contexts) {
            val estimate = ModelMemory.estimate(metadata, contextLength)
            val usable = container.memoryForModels()
            val record = run(label, model, contextLength, threads, true).put("type", "sweep")
                .put("estimate_bytes", estimate.totalBytes).put("usable_bytes", usable).put("battery_c", coolDown())
            if (estimate.totalBytes > usable) {
                append(out, record.put("loaded", false).put("reason", "does not fit"))
                continue
            }
            val before = residentBytes()
            val started = SystemClock.elapsedRealtime()
            val loaded = engine.loadModel(model, LoadConfig(contextLength = contextLength, threads = threads, backend = BackendPreference.CPU))
            record.put("load_ms", SystemClock.elapsedRealtime() - started)
            if (loaded !is LoadResult.Loaded) {
                append(out, record.put("loaded", false).put("reason", loaded.toString()))
                continue
            }
            try {
                val harness = Harness(Captured(engine), skills = true)
                var history = listOf(ToolChatMessage(ChatRole.USER, "Hi!"))
                val (firstAdded, cold) = harness.turn(history, "Hi!")
                history = history + firstAdded + ToolChatMessage(ChatRole.USER, "Thanks. What is the capital of France?")
                val (_, warm) = harness.turn(history, "Thanks. What is the capital of France?")
                record.put("loaded", true).put("cold", cold).put("warm", warm)
                if (arguments.getString("needle") != "false") record.put("needle", needleChat(harness, needle, contextLength))
                record.put("resident_delta_bytes", residentBytes() - before)
                harness.dispose()
            } finally {
                engine.unloadModel()
            }
            append(out, record)
        }
    }

    private suspend fun needleChat(harness: Harness, needle: JSONObject, contextLength: Int): JSONObject {
        val filler = needle.getJSONArray("filler")
        val history = mutableListOf(ToolChatMessage(ChatRole.USER, needle.getString("fact")), ToolChatMessage(ChatRole.ASSISTANT, "Got it."))
        // Below the runtime's prompt limit, so no message is dropped and the fact stays in the chat.
        val target = minOf((contextLength * NEEDLE_SHARE).toInt(), ContextBudget.promptLimit(contextLength) - QUESTION_ROOM)
        var used = 0
        var n = 0
        // Several notes at a time while far from the target: measuring formats and tokenises the whole chat.
        while (used < target - NOTE_TOKENS) {
            repeat(if (target - used > FAR_TOKENS) NOTES_PER_MEASURE else 1) {
                history += ToolChatMessage(ChatRole.USER, "Note ${n + 1}: ${filler.getString(n % filler.length())}")
                history += ToolChatMessage(ChatRole.ASSISTANT, "Noted.")
                n++
            }
            used = harness.runtime.measure(history)?.used ?: break
        }
        val question = needle.getString("question")
        val (_, turn) = harness.turn(history + ToolChatMessage(ChatRole.USER, question), question)
        val use = harness.runtime.measure(history + ToolChatMessage(ChatRole.USER, question))
        return turn.put("notes", n).put("prompt_tokens_measured", use?.used).put("dropped_messages", use?.dropped)
    }

    /** Records the first system prompt and tools the runtime formats, as the app would send them. */
    private class Captured(private val engine: InferenceEngine) : InferenceEngine by engine {
        var prompt: Pair<List<ToolChatMessage>, List<ToolDefinition>>? = null
        val system: String get() = prompt?.first?.firstOrNull { it.role == ChatRole.SYSTEM }?.content.orEmpty()
        val tools: JSONArray get() = JSONArray(prompt?.second.orEmpty().map { JSONObject().put("name", it.name).put("description", it.description).put("parameters", JSONObject(it.parametersJson)) })

        override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean): ToolChatPrompt? =
            engine.formatToolChat(messages, tools, enableThinking).also { if (prompt == null) prompt = messages to tools }
    }

    private inner class Harness(engine: InferenceEngine, skills: Boolean) {
        private val database = Room.inMemoryDatabaseBuilder(context, PolicyDatabase::class.java).build()
        private val states = SkillStateStore(database.policy())
        private val registry = SkillRegistry(if (skills) container.skills.skills.map(::stubbed) else emptyList())
        val runtime: BruceRuntime

        init {
            runBlocking { registry.skills.filter { it.id in TURNED_ON }.forEach { states.set(it, SkillState.ACCEPTED) } }
            val policy = PolicyEngine(registry, states, ToolOutput(), { true }, { ScopeCheck.InScope() }, requirementMet = ::freshInstall)
            val rules = context.resources.openRawResource(R.raw.personality_bruce).bufferedReader().use { it.readText() }
            runtime = BruceRuntime(engine, registry, states, policy, temperature = { 0f }, personality = { rules }, grantNames = { emptyList() }, requirementMet = ::freshInstall)
        }

        /** One user turn, approving anything that asks, as a user who says yes would. */
        suspend fun turn(history: List<ToolChatMessage>, user: String): Pair<List<ToolChatMessage>, JSONObject> {
            val added = mutableListOf<ToolChatMessage>()
            val steps = JSONArray()
            val results = JSONArray()
            var error: String? = null
            val started = SystemClock.elapsedRealtime()
            var firstText: Long? = null
            while (true) {
                var pending: RuntimeEvent.NeedsConfirmation? = null
                runtime.respond(history + added).collect { event ->
                    when (event) {
                        is RuntimeEvent.Text -> if (firstText == null) firstText = SystemClock.elapsedRealtime() - started
                        is RuntimeEvent.Step -> steps.put(
                            JSONObject().put("content", event.content).put("reasoning", event.reasoning)
                                .put("calls", JSONArray(event.toolCalls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson) }))
                                .put("prompt_tokens", event.stats?.promptTokens).put("prompt_ms", event.stats?.promptDuration?.inWholeMilliseconds)
                                .put("gen_tokens", event.stats?.generatedTokens).put("gen_ms", event.stats?.generationDuration?.inWholeMilliseconds),
                        )
                        is RuntimeEvent.ToolResult -> results.put(JSONObject().put("name", event.call.name).put("result", event.resultJson).put("ran", event.ran))
                        is RuntimeEvent.NeedsConfirmation -> pending = event
                        is RuntimeEvent.Finished -> added += event.messages
                        is RuntimeEvent.Failed -> {
                            added += event.messages
                            error = event.error.name
                        }
                    }
                }
                val asked = pending ?: break
                val answer = runtime.answer(asked.call, asked.decision, approved = true)
                added += ToolChatMessage(ChatRole.TOOL, answer.resultJson, toolCallId = asked.call.id, toolName = asked.call.name)
                results.put(JSONObject().put("name", asked.call.name).put("result", answer.resultJson).put("ran", answer.ran).put("approved", true))
            }
            val reply = added.lastOrNull { it.role == ChatRole.ASSISTANT }?.content.orEmpty()
            return added to JSONObject().put("user", user).put("reply", reply).put("steps", steps).put("results", results)
                .put("error", error).put("ttft_ms", firstText).put("wall_ms", SystemClock.elapsedRealtime() - started)
        }

        fun dispose() = database.close()
    }

    private fun stubbed(skill: Skill): Skill = if (skill.id in AUTOMATIC) skill else Skill(
        id = skill.id, version = skill.version, description = skill.description, input = skill.input, capabilities = skill.capabilities,
        defaultState = skill.defaultState, highRisk = skill.highRisk, scope = skill.scope, androidPermissions = skill.androidPermissions,
        optionalPermissions = skill.optionalPermissions, requires = skill.requires, site = skill.site, confirmEveryUse = skill.confirmEveryUse,
    ) { SkillOutcome.Done(STUB) }

    /** A fresh install: offline, nothing granted, no search key, no file index. */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun freshInstall(requirement: SkillRequirement): Boolean = false

    private fun run(label: String, model: File, contextLength: Int, threads: Int, skills: Boolean) = JSONObject()
        .put("label", label).put("file", model.name).put("file_bytes", model.length()).put("platform", "phone")
        .put("device", Build.MODEL).put("context", contextLength).put("threads", threads).put("skills", if (skills) "on" else "off")
        .put("onscreen", onScreen).put("cpuset", File("/proc/self/cpuset").readText().trim())
        .put("cpus_allowed", File("/proc/self/status").readLines().firstOrNull { it.startsWith("Cpus_allowed_list") }?.substringAfter(":")?.trim())

    /** Waits while the battery is hot, so heat does not throttle one model more than another; returns its temperature. */
    private fun coolDown(): Double {
        val limit = SystemClock.elapsedRealtime() + (arguments.getString("cool_wait_s")?.toLong()?.times(1000) ?: MAX_COOL_DOWN_MS)
        var celsius = batteryCelsius()
        while (celsius > HOT_CELSIUS && SystemClock.elapsedRealtime() < limit) {
            Thread.sleep(COOL_POLL_MS)
            celsius = batteryCelsius()
        }
        return celsius
    }

    private fun batteryCelsius(): Double =
        (context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

    private fun residentBytes(): Long = File("/proc/self/statm").readText().trim().split(" ")[1].toLong() * PAGE_BYTES

    private fun append(out: File, record: JSONObject) = out.appendText(record.toString() + "\n")

    private companion object {
        const val DEFAULT_CONTEXT = 4096
        const val SPEED_CONTEXT = 2048
        const val SPEED_TOKENS = 128
        const val REPEATS = 3
        const val SPEED_PROMPT = "You are a helpful assistant living on a phone. Explain in a few sentences why " +
            "running a language model locally on a phone is good for privacy, and what the trade-offs are " +
            "compared with a cloud service. Mention battery, speed and offline use."
        const val NEEDLE_SHARE = 0.7
        const val NOTES_PER_MEASURE = 5
        const val NOTE_TOKENS = 70
        const val FAR_TOKENS = 800
        const val QUESTION_ROOM = 64
        const val HOT_CELSIUS = 40.0
        const val COOL_POLL_MS = 30_000L
        const val MAX_COOL_DOWN_MS = 20 * 60_000L
        const val STUB = "Done."
        val PAGE_BYTES = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
        val AUTOMATIC = setOf("get_datetime", "calculate", "get_battery_status", "get_device_info", "get_storage_status", "get_network_status")
        val TURNED_ON = setOf("get_battery_status", "get_device_info", "get_storage_status")
    }
}
