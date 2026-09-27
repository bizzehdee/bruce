package com.bizzeh.bruce.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.MainActivity
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.inference.BackendPreference
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.deviceEngine
import com.bizzeh.bruce.models.ModelMemory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.time.measureTime

/**
 * Measures load time, prompt and generation speed, and resident memory for every GGUF model
 * in the app's models folder, across thread counts. Skipped unless run with
 * `-Pandroid.testInstrumentationRunnerArguments.benchmark=true`; results go to logcat tag
 * `BruceBench`.
 */
@RunWith(AndroidJUnit4::class)
class InferenceBenchmark {
    private val arguments = InstrumentationRegistry.getArguments()
    private val modelsDir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, MainActivity.MODELS_DIR)

    @Test
    fun benchmarkModels() {
        assumeTrue("benchmark not requested", arguments.getString("benchmark") == "true")
        val models = modelsDir.listFiles { file -> file.name.endsWith(".gguf") }.orEmpty().sortedBy { it.length() }
        val cores = Runtime.getRuntime().availableProcessors()
        val threadCounts = listOf(1, 2, 4, 8).filter { it <= cores }

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { nativeThread ->
            val engine = deviceEngine(nativeThread)
            for (model in models) {
                val metadata = (GgufReader.read(model) as GgufReadResult.Read).metadata
                val estimate = ModelMemory.estimate(metadata, CONTEXT)
                for (threads in threadCounts) {
                    runBlocking {
                        val rssBefore = residentBytes()
                        lateinit var loaded: LoadResult
                        val loadTime = measureTime {
                            loaded = engine.loadModel(
                                model,
                                LoadConfig(contextLength = CONTEXT, threads = threads, backend = BackendPreference.CPU),
                            )
                        }
                        check(loaded is LoadResult.Loaded) { "load failed: $loaded" }
                        engine.generate(GenerationRequest(PROMPT, maxTokens = 8, temperature = 0f)).toList()
                        val events = engine.generate(GenerationRequest(PROMPT, maxTokens = TOKENS, temperature = 0f)).toList()
                        val stats = (events.last() as GenerationEvent.Completed).stats
                        val rssAfter = residentBytes()
                        engine.unloadModel()
                        Log.i(
                            TAG,
                            String.format(
                                Locale.ROOT,
                                "model=%s threads=%d load_ms=%d prompt_tokens=%d pp_tps=%.1f gen_tokens=%d tg_tps=%.2f " +
                                    "rss_delta_mb=%d estimate_mb=%d kv_mb=%d",
                                model.name, threads, loadTime.inWholeMilliseconds, stats.promptTokens,
                                stats.promptTokensPerSecond, stats.generatedTokens, stats.generationTokensPerSecond,
                                (rssAfter - rssBefore) shr 20, estimate.totalBytes shr 20, (estimate.kvCacheBytes ?: 0) shr 20,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun residentBytes(): Long = File("/proc/self/status").readLines()
        .first { it.startsWith("VmRSS:") }
        .filter(Char::isDigit).toLong() * 1024

    private companion object {
        const val TAG = "BruceBench"
        const val CONTEXT = 2048
        const val TOKENS = 64
        const val PROMPT = "You are a helpful assistant living on a phone. Explain in a few sentences why " +
            "running a language model locally on a phone is good for privacy, and what the trade-offs are " +
            "compared with a cloud service. Mention battery, speed and offline use."
    }
}
