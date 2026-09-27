package com.bizzeh.bruce.inference

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
}

data class GenerationRequest(
    val prompt: String,
    val maxTokens: Int = 256,
    /** 0 selects greedy decoding. */
    val temperature: Float = 0.8f,
    val seed: Int = 0,
) {
    init {
        require(maxTokens > 0) { "maxTokens must be positive, was $maxTokens" }
        require(temperature >= 0f) { "temperature must not be negative, was $temperature" }
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
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    /** Tokens decoded per native call while evaluating a prompt. 2048 is llama.cpp's default. */
    val batchSize: Int = 2048,
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
    data class Loaded(val info: ModelInfo) : LoadResult

    data class Failed(val error: LoadError) : LoadResult
}

enum class LoadError {
    FILE_NOT_FOUND,
    NOT_GGUF,
    MODEL_LOAD_FAILED,
    CONTEXT_CREATION_FAILED,
}

data class EngineCapabilities(val devices: List<DeviceType>)

enum class DeviceType {
    CPU,
    GPU,
    INTEGRATED_GPU,
    ACCELERATOR,
    OTHER,
}
