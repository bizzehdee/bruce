package com.bizzeh.bruce.runtime

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.deviceEngine
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.policy.PolicyEngine
import com.bizzeh.bruce.policy.ScopeCheck
import com.bizzeh.bruce.policy.SkillStateStore
import com.bizzeh.bruce.skills.ToolOutput
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import kotlin.time.TimeSource

/**
 * TASK-056's measurement: the same three-turn chat through the real runtime and skills, with the
 * previous prompt reused and without. Logs "TIMING" lines per turn. The model is
 * files/test-models/<model argument>, Qwen3.5-0.8B-Q8_0.gguf by default (docs/building.md).
 */
@ManualOnly("needs a real model copied onto the phone; slow")
@RunWith(AndroidJUnit4::class)
class PromptReuseTimingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container = (instrumentation.targetContext.applicationContext as BruceApplication).container

    @Test
    fun reuseMakesTurnsFaster() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("model") ?: "Qwen3.5-0.8B-Q8_0.gguf"
        val model = File(instrumentation.targetContext.filesDir, "test-models/$name")
        assumeTrue("copy $name first", model.exists())
        val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val engine = deviceEngine(nativeThread)
        assertTrue(engine.loadModel(model, LoadConfig(contextLength = 4096, threads = 4, backend = BackendPreference.CPU)) is LoadResult.Loaded)

        val reused = chat(engine, label = "reuse")
        val fresh = chat(NoReuse(engine), label = "fresh")

        engine.unloadModel()
        nativeThread.close()
        Log.i(TAG, "TIMING $name total reuse=${reused.first}ms decoded=${reused.second} fresh=${fresh.first}ms decoded=${fresh.second}")
        assertTrue("decoded ${reused.second} with reuse, ${fresh.second} without", reused.second < fresh.second)
    }

    /** Total milliseconds and prompt tokens decoded over the turns. */
    private suspend fun chat(engine: InferenceEngine, label: String): Pair<Long, Int> {
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PolicyDatabase::class.java).build()
        val states = SkillStateStore(database.policy())
        val skills = container.skills
        val policy = PolicyEngine(skills, states, ToolOutput(), { true }, { ScopeCheck.OutOfScope("No grants in this test.") })
        val runtime = BruceRuntime(engine, skills, states, policy, temperature = { 0f }, personality = container::personalityRules)
        val history = mutableListOf<ToolChatMessage>()
        var totalMs = 0L
        var totalDecoded = 0
        for ((turn, question) in QUESTIONS.withIndex()) {
            history += ToolChatMessage(ChatRole.USER, question)
            val started = TimeSource.Monotonic.markNow()
            val events = runtime.respond(history.toList()).toList()
            val ms = started.elapsedNow().inWholeMilliseconds
            val decoded = events.filterIsInstance<RuntimeEvent.Step>().sumOf { it.stats?.promptTokens ?: 0 }
            val steps = events.count { it is RuntimeEvent.Step }
            Log.i(TAG, "TIMING $label turn=${turn + 1} ms=$ms steps=$steps decoded=$decoded")
            totalMs += ms
            totalDecoded += decoded
            when (val last = events.last()) {
                is RuntimeEvent.Finished -> history += last.messages
                is RuntimeEvent.Failed -> history += last.messages
                else -> Unit
            }
        }
        database.close()
        return totalMs to totalDecoded
    }

    /** The same engine with every prompt decoded in full, as before TASK-056. */
    private class NoReuse(private val engine: InferenceEngine) : InferenceEngine by engine {
        override fun generate(request: GenerationRequest) = engine.generate(request.copy(reusePrompt = false))
    }

    private companion object {
        const val TAG = "BruceTiming"
        val QUESTIONS = listOf("What time is it?", "What is 17 times 23?", "Thanks! Tell me a one-line joke.")
    }
}
