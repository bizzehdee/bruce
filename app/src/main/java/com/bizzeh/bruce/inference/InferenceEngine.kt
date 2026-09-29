package com.bizzeh.bruce.inference

import com.bizzeh.bruce.hardware.CpuTopology
import kotlinx.coroutines.flow.Flow
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit

interface InferenceEngine {
    suspend fun loadModel(file: File, config: LoadConfig = LoadConfig()): LoadResult

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

    /** How many tokens [prompt] takes in the loaded model's context; null with no model loaded. */
    suspend fun countTokens(prompt: String): Int?

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
    /** Constrains tool calls; see [ToolFormat.grammar]. */
    val grammar: ToolGrammar? = null,
    /** Generation ends once the reply ends with one of these (some templates end turns with text). */
    val stops: List<String> = emptyList(),
    /** Keep what the prompt shares with the previous one instead of decoding it again; off for benchmarks. */
    val reusePrompt: Boolean = true,
) {
    init {
        require(maxTokens > 0) { "maxTokens must be positive, was $maxTokens" }
        require(temperature >= 0f) { "temperature must not be negative, was $temperature" }
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.8f
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
    val threads: Int = CpuTopology.performanceCoreCount(),
    /** Tokens decoded per native call while evaluating a prompt. 2048 is llama.cpp's default. */
    val batchSize: Int = 2048,
    val backend: BackendPreference = BackendPreference.AUTO,
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
)

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
