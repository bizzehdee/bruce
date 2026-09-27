package com.bizzeh.bruce.inference

import com.bizzeh.bruce.gguf.Gguf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

/**
 * llama.cpp contexts are not thread-safe, so every native call runs on [dispatcher],
 * which must be single-threaded, and model state changes are serialised by [mutex].
 * A generation holds [mutex] until it ends, so a model cannot be unloaded mid-generation.
 */
internal class LlamaCppEngine(
    private val llama: LlamaApi,
    private val dispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : InferenceEngine {
    private class Session(val model: Long, val context: Long, val info: ModelInfo)

    private val mutex = Mutex()

    @Volatile
    private var session: Session? = null

    private val stopRequested = AtomicBoolean(false)

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

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        mutex.withLock {
            val current = session
            if (current == null) {
                emit(GenerationEvent.Failed(GenerationError.NO_MODEL_LOADED))
                return@withLock
            }
            stopRequested.set(false)
            val generation = llama.beginGeneration(current.context, request.temperature, request.seed)
            try {
                streamCompletion(generation, request)
            } finally {
                llama.endGeneration(generation)
            }
        }
    }.flowOn(dispatcher)

    override fun stop() {
        stopRequested.set(true)
    }

    private suspend fun FlowCollector<GenerationEvent>.streamCompletion(generation: Long, request: GenerationRequest) {
        val promptStart = timeSource.markNow()
        val promptTokens = llama.evaluatePrompt(generation, request.prompt.toByteArray(Charsets.UTF_8))
        when (promptTokens) {
            LlamaApi.PROMPT_TOO_LONG -> return emit(GenerationEvent.Failed(GenerationError.PROMPT_TOO_LONG))
            LlamaApi.DECODE_FAILED -> return emit(GenerationEvent.Failed(GenerationError.DECODE_FAILED))
        }
        val promptDuration = promptStart.elapsedNow()

        val generationStart = timeSource.markNow()
        val decoder = Utf8PieceDecoder()
        var generatedTokens = 0
        var reason = StopReason.MAX_TOKENS
        while (generatedTokens < request.maxTokens) {
            if (stopRequested.get()) {
                reason = StopReason.STOPPED
                break
            }
            when (llama.nextToken(generation)) {
                LlamaApi.TOKEN -> {
                    generatedTokens++
                    emitText(decoder.decode(llama.takePiece(generation)))
                }
                LlamaApi.END_OF_GENERATION -> {
                    reason = StopReason.END_OF_GENERATION
                    break
                }
                LlamaApi.CONTEXT_FULL -> {
                    reason = StopReason.CONTEXT_FULL
                    break
                }
                else -> return emit(GenerationEvent.Failed(GenerationError.DECODE_FAILED))
            }
        }
        emitText(decoder.finish())
        val stats = GenerationStats(promptTokens, promptDuration, generatedTokens, generationStart.elapsedNow())
        emit(GenerationEvent.Completed(reason, stats))
    }

    private suspend fun FlowCollector<GenerationEvent>.emitText(text: String) {
        if (text.isNotEmpty()) emit(GenerationEvent.Token(text))
    }

    private fun open(file: File, config: LoadConfig): LoadResult {
        val model = llama.loadModel(file.absolutePath)
        if (model == 0L) return LoadResult.Failed(LoadError.MODEL_LOAD_FAILED)

        val context = llama.newContext(model, config.contextLength, config.threads, config.batchSize)
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
