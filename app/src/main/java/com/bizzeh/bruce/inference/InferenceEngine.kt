package com.bizzeh.bruce.inference

import java.io.File

interface InferenceEngine {
    suspend fun loadModel(file: File, config: LoadConfig = LoadConfig()): LoadResult

    suspend fun unloadModel()

    fun getCapabilities(): EngineCapabilities

    fun getModelInfo(): ModelInfo?
}

data class LoadConfig(
    val contextLength: Int = 2048,
    val threads: Int = Runtime.getRuntime().availableProcessors(),
) {
    init {
        require(contextLength > 0) { "contextLength must be positive, was $contextLength" }
        require(threads > 0) { "threads must be positive, was $threads" }
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
