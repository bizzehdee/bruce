package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.litert.LiteRtPipeline
import com.bizzeh.bruce.litert.LiteRtPipelineResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/** A model on a remote server, chosen instead of one on the phone (TASK-077). */
data class RemoteChoice(val serverId: String, val serverName: String, val model: String) {
    /** How the chat title and switcher name it. */
    val label: String get() = "$model ($serverName)"
}

data class ActiveModelState(
    val installed: List<File> = emptyList(),
    val active: File? = null,
    /** Which pipeline [active] loads on (TASK-120); null when nothing is loaded. */
    val format: ModelFormat? = null,
    /** llama.cpp/GGUF model details; null for a LiteRT-LM model, which has no equivalent metadata (TASK-120). */
    val info: ModelInfo? = null,
    /** llama.cpp/GGUF's backend; null for a LiteRT-LM model (TASK-120), which has no user-facing backend setting yet (TASK-119's GPU path has no UI). */
    val backend: Backend? = null,
    val loading: Boolean = false,
    val error: LoadError? = null,
    /**
     * The loaded model's estimated memory; free memory plus this is what a model may use. For a
     * LiteRT-LM model this is the file's size on disk (TASK-120), a coarse proxy: no detailed
     * weights/KV-cache estimate exists for this format yet.
     */
    val memoryBytes: Long = 0,
    /** When the chosen model runs on a server; then no model is loaded on the phone. */
    val remote: RemoteChoice? = null,
)

/**
 * Which model is loaded, shared by every screen: the chat title, the quick model switcher,
 * model management and diagnostics all read and change it here. [liteRt] is null in F-Droid builds
 * and when the host app wires nothing in (TASK-120); loading a `.litertlm` file then fails cleanly
 * with [LoadError.NOT_GGUF], the same as any other unsupported format.
 */
class ActiveModel(
    private val engine: InferenceEngine,
    private val modelsDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val liteRt: LiteRtPipeline? = null,
) {
    private val mutableState = MutableStateFlow(ActiveModelState())
    val state: StateFlow<ActiveModelState> = mutableState.asStateFlow()

    suspend fun refresh() {
        val installed = withContext(ioDispatcher) { ModelFiles.installedModels(modelsDir) }.sortedBy { it.name.lowercase() }
        mutableState.update { it.copy(installed = installed) }
    }

    suspend fun unload() {
        engine.unloadModel()
        liteRt?.unload()
        mutableState.update { it.copy(active = null, format = null, info = null, backend = null, error = null, memoryBytes = 0) }
    }

    /** Uses a server's model: the phone's model is unloaded, as it is not needed and its memory is freed. */
    suspend fun useRemote(choice: RemoteChoice) {
        engine.unloadModel()
        liteRt?.unload()
        mutableState.update { it.copy(active = null, format = null, info = null, backend = null, error = null, memoryBytes = 0, loading = false, remote = choice) }
    }

    /** Loads [file]; the context is capped at the length the model was trained for. GGUF only — [PrototypeViewModel]'s debug screen uses this directly. Prefer [loadAny] elsewhere. */
    suspend fun load(file: File, config: LoadConfig = LoadConfig()): LoadResult {
        mutableState.update { it.copy(loading = true, error = null) }
        val metadata = withContext(ioDispatcher) { (GgufReader.read(file) as? GgufReadResult.Read)?.metadata }
        val trained = metadata?.contextLength
        val projector = withContext(ioDispatcher) { Projectors.installedFor(file) }
        val capped = (if (trained != null && trained in 1 until config.contextLength) config.copy(contextLength = trained.toInt()) else config)
            .copy(projectorPath = projector?.absolutePath)
        val result = engine.loadModel(file, capped)
        val projectorBytes = projector?.length() ?: 0
        val memory = metadata?.let { ModelMemory.estimate(it, capped.contextLength, projectorBytes).totalBytes } ?: 0
        mutableState.update {
            when (result) {
                is LoadResult.Loaded -> it.copy(active = file, format = ModelFormat.GGUF, info = result.info, backend = result.backend, loading = false, memoryBytes = memory, remote = null)
                // Some failures (missing file, not GGUF) keep the previous model loaded; others
                // release it first. Ask the engine rather than assume.
                is LoadResult.Failed -> if (engine.getModelInfo() == null) {
                    it.copy(active = null, format = null, info = null, backend = null, loading = false, error = result.error, memoryBytes = 0)
                } else {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
        return result
    }

    /**
     * Loads [file] on whichever pipeline its format uses (TASK-120): llama.cpp/GGUF through
     * [load], or LiteRT-LM through [liteRt]. [liteRtContextTokens] non-positive asks LiteRT-LM for
     * the model's own default context (no Auto-context sizing exists for this format yet).
     */
    suspend fun loadAny(file: File, config: LoadConfig = LoadConfig(), liteRtContextTokens: Int = 0): ModelLoadResult =
        when (ModelFiles.formatOf(file)) {
            ModelFormat.GGUF -> when (val result = load(file, config)) {
                is LoadResult.Loaded -> ModelLoadResult.Loaded(ModelFormat.GGUF, result.info, result.backend)
                is LoadResult.Failed -> ModelLoadResult.Failed(result.error)
            }
            ModelFormat.LITERT_LM -> loadLiteRt(file, liteRtContextTokens)
            null -> ModelLoadResult.Failed(LoadError.NOT_GGUF)
        }

    private suspend fun loadLiteRt(file: File, contextTokens: Int): ModelLoadResult {
        val pipeline = liteRt ?: run {
            mutableState.update { it.copy(loading = false, error = LoadError.NOT_GGUF) }
            return ModelLoadResult.Failed(LoadError.NOT_GGUF)
        }
        mutableState.update { it.copy(loading = true, error = null) }
        return when (val result = pipeline.load(file, contextTokens)) {
            is LiteRtPipelineResult.Loaded -> {
                val memory = withContext(ioDispatcher) { file.length() }
                mutableState.update { it.copy(active = file, format = ModelFormat.LITERT_LM, info = null, backend = null, loading = false, memoryBytes = memory, remote = null) }
                ModelLoadResult.Loaded(ModelFormat.LITERT_LM)
            }
            // LiteRtPipelineImpl.load() always closes the previous model before trying the new
            // one, unlike llama.cpp's engine (some of its failures keep the old model); a failure
            // here always means nothing is loaded now.
            LiteRtPipelineResult.Failed -> {
                mutableState.update { it.copy(active = null, format = null, info = null, backend = null, loading = false, error = LoadError.MODEL_LOAD_FAILED, memoryBytes = 0) }
                ModelLoadResult.Failed(LoadError.MODEL_LOAD_FAILED)
            }
        }
    }
}
