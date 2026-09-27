package com.bizzeh.bruce.inference

import com.bizzeh.bruce.gguf.Gguf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * llama.cpp contexts are not thread-safe, so every native call runs on [dispatcher],
 * which must be single-threaded, and model state changes are serialised by [mutex].
 */
internal class LlamaCppEngine(
    private val llama: LlamaApi,
    private val dispatcher: CoroutineDispatcher,
) : InferenceEngine {
    private class Session(val model: Long, val context: Long, val info: ModelInfo)

    private val mutex = Mutex()

    @Volatile
    private var session: Session? = null

    override suspend fun loadModel(file: File, config: LoadConfig): LoadResult =
        mutex.withLock {
            withContext(dispatcher) {
                when {
                    !file.isFile -> LoadResult.Failed(LoadError.FILE_NOT_FOUND)
                    !Gguf.hasMagic(file) -> LoadResult.Failed(LoadError.NOT_GGUF)
                    else -> {
                        release()
                        open(file, config)
                    }
                }
            }
        }

    override suspend fun unloadModel() {
        mutex.withLock { withContext(dispatcher) { release() } }
    }

    override fun getCapabilities(): EngineCapabilities =
        EngineCapabilities(llama.deviceTypes().map(::toDeviceType))

    override fun getModelInfo(): ModelInfo? = session?.info

    private fun open(file: File, config: LoadConfig): LoadResult {
        val model = llama.loadModel(file.absolutePath)
        if (model == 0L) return LoadResult.Failed(LoadError.MODEL_LOAD_FAILED)

        val context = llama.newContext(model, config.contextLength, config.threads)
        if (context == 0L) {
            llama.freeModel(model)
            return LoadResult.Failed(LoadError.CONTEXT_CREATION_FAILED)
        }

        val info = ModelInfo(
            description = llama.modelDescription(model),
            parameterCount = llama.modelParameterCount(model),
            sizeBytes = llama.modelSizeBytes(model),
            trainedContextLength = llama.modelTrainedContextLength(model),
        )
        session = Session(model, context, info)
        return LoadResult.Loaded(info)
    }

    private fun release() {
        val current = session ?: return
        session = null
        llama.freeContext(current.context)
        llama.freeModel(current.model)
    }

    private fun toDeviceType(ggmlType: Int): DeviceType = when (ggmlType) {
        GGML_DEVICE_CPU -> DeviceType.CPU
        GGML_DEVICE_GPU -> DeviceType.GPU
        GGML_DEVICE_IGPU -> DeviceType.INTEGRATED_GPU
        GGML_DEVICE_ACCEL -> DeviceType.ACCELERATOR
        else -> DeviceType.OTHER
    }

    private companion object {
        // Values of enum ggml_backend_dev_type in ggml-backend.h.
        const val GGML_DEVICE_CPU = 0
        const val GGML_DEVICE_GPU = 1
        const val GGML_DEVICE_IGPU = 2
        const val GGML_DEVICE_ACCEL = 3
    }
}
