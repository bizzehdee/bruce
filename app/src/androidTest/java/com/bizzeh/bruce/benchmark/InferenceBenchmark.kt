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
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.time.measureTime

/**
 * Measures load time, prompt and generation speed, and resident memory for every GGUF model
 * in the app's models folder, across thread counts. Results go to logcat tag `BruceBench`.
 * Not part of normal runs; run it with
 * `adb shell am instrument -w -e class com.bizzeh.bruce.benchmark.InferenceBenchmark
 * com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@ManualOnly("benchmark: slow, needs models pushed to the phone")
@RunWith(AndroidJUnit4::class)
class InferenceBenchmark {
    private val modelsDir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, MainActivity.MODELS_DIR)

    @Test
    fun benchmarkModels() {
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
                        val before = residentMemory()
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
                        val after = residentMemory()
                        engine.unloadModel()
                        Log.i(
                            TAG,
                            String.format(
                                Locale.ROOT,
                                "model=%s threads=%d load_ms=%d prompt_tokens=%d pp_tps=%.1f gen_tokens=%d tg_tps=%.2f " +
                                    "rss_delta_mb=%d anon_delta_mb=%d file_delta_mb=%d estimate_mb=%d kv_mb=%d",
                                model.name, threads, loadTime.inWholeMilliseconds, stats.promptTokens,
                                stats.promptTokensPerSecond, stats.generatedTokens, stats.generationTokensPerSecond,
                                (after.total - before.total) shr 20, (after.anonymous - before.anonymous) shr 20,
                                (after.file - before.file) shr 20, estimate.totalBytes shr 20, (estimate.kvCacheBytes ?: 0) shr 20,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Resident memory split into anonymous (heap, buffers) and file-backed (mapped model) pages. */
    private data class ResidentMemory(val total: Long, val anonymous: Long, val file: Long)

    // statm rather than RssAnon/RssFile in /proc/self/status, which kernels before 4.5
    // (the XZ Premium's) do not report. Fields are pages: size, resident, shared (file-backed) ...
    private fun residentMemory(): ResidentMemory {
        val fields = File("/proc/self/statm").readText().trim().split(" ").map(String::toLong)
        val resident = fields[1] * PAGE_BYTES
        val fileBacked = fields[2] * PAGE_BYTES
        return ResidentMemory(total = resident, anonymous = resident - fileBacked, file = fileBacked)
    }

    private companion object {
        const val TAG = "BruceBench"
        val PAGE_BYTES = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
        const val CONTEXT = 2048
        const val TOKENS = 64
        const val PROMPT = "You are a helpful assistant living on a phone. Explain in a few sentences why " +
            "running a language model locally on a phone is good for privacy, and what the trade-offs are " +
            "compared with a cloud service. Mention battery, speed and offline use."
    }
}
