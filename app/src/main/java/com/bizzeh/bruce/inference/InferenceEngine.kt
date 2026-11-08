package com.bizzeh.bruce.inference

import com.bizzeh.bruce.hardware.CpuTopology
import kotlinx.coroutines.flow.Flow
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit

interface InferenceEngine {
    suspend fun loadModel(file: File, config: LoadConfig = LoadConfig()): LoadResult

    /** Whether the engine keeps evaluated fixed prompts on storage ([GenerationRequest.savedPrefix], TASK-109). */
    val savesPrompts: Boolean get() = false

    suspend fun unloadModel()

    fun getCapabilities(): EngineCapabilities

    fun getModelInfo(): ModelInfo?

    /**
     * Streams the completion of [request]. Cancelling collection halts generation.
     * Expected failures arrive as [GenerationEvent.Failed]; the flow then completes.
     */
    fun generate(request: GenerationRequest): Flow<GenerationEvent>

    /** Ends the current generation after the token in progress. */
    fun stop()

    /**
     * Formats [messages] as a prompt for the loaded model, ending with the start of an assistant
     * reply. Null when no model is loaded.
     */
    suspend fun formatChat(messages: List<ChatMessage>): ChatPrompt?

    /**
     * Formats [messages] with [tools] in the loaded model's own tool format (ADR 0001). Null when no
     * model is loaded or its template cannot be applied. When the result's format does not
     * [support tools][ToolFormat.supportsTools], use [BruceToolFormat] instead.
     */
    suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean = false): ToolChatPrompt?

    /** Splits a reply generated with [format] into text and tool calls; null if it cannot be parsed. */
    fun parseReply(format: ToolFormat, text: String, partial: Boolean = false): ParsedReply?

    /** The grammar for [BruceToolFormat] over [tools], or null if their schemas cannot be expressed. */
    fun bruceToolGrammar(tools: List<ToolDefinition>): ToolGrammar?

    /**
     * Whether a chat template (as a Hub search reports it) can express tool calls, judged as for a
     * loaded model; null if it cannot be read. Needs no model loaded.
     */
    fun templateSupportsTools(template: String, bosToken: String?, eosToken: String?): Boolean?

    /** How many tokens [prompt] takes in the loaded model's context; null with no model loaded. */
    suspend fun countTokens(prompt: String): Int?

    /** How many context tokens [image] takes with the loaded vision projector; null without one (TASK-082). */
    suspend fun imageTokens(image: RgbImage): Int? = null

    /** The text that stands for an image in a prompt, one per image in order; null without a vision projector. */
    fun mediaMarker(): String? = null

    /** The loaded context's length in tokens; null with no model loaded. */
    fun contextLength(): Int?
}

enum class ChatRole(val wireName: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    /** A tool's result, in a conversation with tools. */
    TOOL("tool"),
}

data class ChatMessage(val role: ChatRole, val content: String)

/** [usedFallbackTemplate] is true when the model had no chat template llama.cpp supports and ChatML was used. */
data class ChatPrompt(val text: String, val usedFallbackTemplate: Boolean)

data class GenerationRequest(
    val prompt: String,
    val maxTokens: Int = 256,
    /** 0 selects greedy decoding. */
    val temperature: Float = DEFAULT_TEMPERATURE,
    val seed: Int = 0,
    /** <= 0 uses the full vocabulary (llama.cpp's own `llama_sampler_init_top_k` convention). */
    val topK: Int = DEFAULT_TOP_K,
    /** 1.0 disables top-p truncation. */
    val topP: Float = DEFAULT_TOP_P,
    /** 1.0 disables the repeat penalty (llama.cpp's own library default). */
    val repeatPenalty: Float = DEFAULT_REPEAT_PENALTY,
    /** Constrains tool calls; see [ToolFormat.grammar]. */
    val grammar: ToolGrammar? = null,
    /** Generation ends once the reply ends with one of these (some templates end turns with text). */
    val stops: List<String> = emptyList(),
    /** Keep what the prompt shares with the previous one instead of decoding it again; off for benchmarks. */
    val reusePrompt: Boolean = true,
    /** The part of [prompt] the next one will share, when a pass branches off the chat (TASK-047). */
    val checkpointPrefix: String? = null,
    /** The fixed start of [prompt] (system prompt and skills), kept on storage once evaluated (TASK-109); null when it holds anything about the user. */
    val savedPrefix: String? = null,
    /** Images for the prompt's media markers, in order (TASK-082); nothing is reused with them. */
    val images: List<RgbImage> = emptyList(),
) {
    init {
        require(maxTokens > 0) { "maxTokens must be positive, was $maxTokens" }
        require(temperature >= 0f) { "temperature must not be negative, was $temperature" }
        require(topP in 0f..1f) { "topP must be 0..1, was $topP" }
        require(repeatPenalty > 0f) { "repeatPenalty must be positive, was $repeatPenalty" }
    }

    companion object {
        // At 0.8 MiniCPM5 2B skipped a needed search one time in five; at 0.5 one in twenty (2026-10-02).
        const val DEFAULT_TEMPERATURE = 0.5f

        // llama.cpp's own library defaults (common/common.h), not a community-recommended value:
        // top_k=40, top_p=0.95 (a real truncation), penalty_repeat=1.0 (disabled by default; 1.1 is
        // a commonly recommended starting point once a user turns it on, not the library's own default).
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_REPEAT_PENALTY = 1.0f

        /**
         * Shared number-stepper bounds for both the global (Settings) and per-model (Models) UI,
         * so the two stay consistent (TASK-126, replacing a row of preset pill chips 2026-11-08: a
         * fixed small list could not express every value a user might want).
         */
        const val MIN_TEMPERATURE = 0f
        const val MAX_TEMPERATURE = 2f
        const val TEMPERATURE_STEP = 0.1f

        const val MIN_TOP_K = 0
        const val MAX_TOP_K = 100
        const val TOP_K_STEP = 1

        const val MIN_TOP_P = 0f
        const val MAX_TOP_P = 1f
        const val TOP_P_STEP = 0.05f

        const val MIN_REPEAT_PENALTY = 0.5f
        const val MAX_REPEAT_PENALTY = 2f
        const val REPEAT_PENALTY_STEP = 0.1f
    }
}

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent

    data class Completed(val reason: StopReason, val stats: GenerationStats) : GenerationEvent

    data class Failed(val error: GenerationError) : GenerationEvent
}

enum class StopReason {
    END_OF_GENERATION,
    MAX_TOKENS,
    CONTEXT_FULL,
    STOPPED,
}

enum class GenerationError {
    NO_MODEL_LOADED,
    PROMPT_TOO_LONG,
    DECODE_FAILED,
    /** The request's grammar or its triggers could not be used with this model. */
    GRAMMAR_REJECTED,
    /** The request has images and the model has no vision projector loaded, or they could not be read. */
    IMAGES_UNUSABLE,
}

data class GenerationStats(
    val promptTokens: Int,
    val promptDuration: Duration,
    val generatedTokens: Int,
    val generationDuration: Duration,
) {
    val promptTokensPerSecond: Double get() = perSecond(promptTokens, promptDuration)

    val generationTokensPerSecond: Double get() = perSecond(generatedTokens, generationDuration)

    private fun perSecond(tokens: Int, duration: Duration): Double {
        val seconds = duration.toDouble(DurationUnit.SECONDS)
        return if (seconds > 0.0) tokens / seconds else 0.0
    }
}

data class LoadConfig(
    val contextLength: Int = 2048,
    val threads: Int = CpuTopology.defaultThreads(),
    /** Tokens decoded per native call while evaluating a prompt. 2048 is llama.cpp's default. */
    val batchSize: Int = 2048,
    val backend: BackendPreference = BackendPreference.AUTO,
    /** A chat template to use instead of the file's own, e.g. one fetched for tool support (TASK-063). */
    val chatTemplate: String? = null,
    /** The model's vision projector (TASK-083), loaded with it so it can see images. */
    val projectorPath: String? = null,
) {
    init {
        require(contextLength > 0) { "contextLength must be positive, was $contextLength" }
        require(threads > 0) { "threads must be positive, was $threads" }
        require(batchSize > 0) { "batchSize must be positive, was $batchSize" }
    }
}

data class ModelInfo(
    val description: String,
    val parameterCount: Long,
    val sizeBytes: Long,
    val trainedContextLength: Int,
    /** A vision projector is loaded: the model can be given images (TASK-082). */
    val vision: Boolean = false,
)

/** An image as the model takes it: [rgb] holds 3 bytes a pixel, row by row. */
class RgbImage(val width: Int, val height: Int, val rgb: ByteArray) {
    init {
        require(width > 0 && height > 0 && rgb.size.toLong() == width.toLong() * height * 3) { "not a ${width}x$height RGB image" }
    }
}

sealed interface LoadResult {
    /**
     * [backend] is where the model runs. [failedBackends] lists GPU backends that were
     * tried first and could not load it, in the order tried.
     */
    data class Loaded(
        val info: ModelInfo,
        val backend: Backend,
        val failedBackends: List<Backend> = emptyList(),
    ) : LoadResult

    data class Failed(val error: LoadError) : LoadResult
}

enum class LoadError {
    FILE_NOT_FOUND,
    NOT_GGUF,
    MODEL_LOAD_FAILED,
    CONTEXT_CREATION_FAILED,
    /** The requested backend has no usable device on this phone. */
    BACKEND_UNAVAILABLE,
}

data class EngineCapabilities(
    val devices: List<ComputeDevice>,
    /** Features of the CPU backend variant chosen for this phone, e.g. "DOTPROD=1". */
    val cpuBackendFeatures: List<String>,
) {
    /** Backends with at least one device Bruce considers safe to run a model on. */
    val usableBackends: Set<Backend> get() = devices.filter { it.usable }.mapTo(mutableSetOf()) { it.backend }
}

data class ComputeDevice(
    /** Position in ggml's device registry. */
    val index: Int,
    val backend: Backend,
    val name: String,
    val description: String,
    val type: DeviceType,
    val memoryBytes: Long,
    /**
     * False when the device is registered but known to be unsafe: ggml requires Vulkan 1.2
     * yet only checks the instance version, so a Vulkan 1.1 GPU can still be registered.
     */
    val usable: Boolean,
)

enum class Backend {
    CPU,
    VULKAN,
    OPENCL,
    OTHER,
}

enum class DeviceType {
    CPU,
    GPU,
    INTEGRATED_GPU,
    ACCELERATOR,
    OTHER,
}
