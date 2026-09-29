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
import org.json.JSONArray
import org.json.JSONObject
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
    private class Session(val model: Long, val context: Long, val info: ModelInfo, val contextLength: Int, val chatTemplate: String?) {
        /** The model's chat templates for tool formats, made the first time they are needed; 0 if unusable. */
        var templates: Long? = null
    }

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

    override fun getCapabilities(): EngineCapabilities {
        val devices = (0 until llama.deviceCount()).map { index ->
            val (registryName, name, description) = llama.deviceStrings(index)
            val backend = toBackend(registryName)
            ComputeDevice(
                index = index,
                backend = backend,
                name = name,
                description = description,
                type = toDeviceType(llama.deviceType(index)),
                memoryBytes = llama.deviceMemoryBytes(index),
                usable = backend != Backend.VULKAN || vulkanUsable(description),
            )
        }
        return EngineCapabilities(devices, llama.cpuBackendFeatures().toList())
    }

    /**
     * ggml needs Vulkan 1.2 on the device, not just the loader. PowerVR GPUs are refused: on the
     * Pixel 11 their driver gives wrong output and can crash the GPU firmware, with no setting that
     * makes it reliable (TASK-050, .learnings/gpu-backends-on-test-phones.md).
     */
    private fun vulkanUsable(deviceName: String): Boolean =
        llama.vulkanDeviceApiVersion(deviceName) >= VULKAN_1_2 && llama.vulkanDeviceVendorId(deviceName) != IMAGINATION_VENDOR_ID

    override fun getModelInfo(): ModelInfo? = session?.info

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        mutex.withLock {
            val current = session
            if (current == null) {
                emit(GenerationEvent.Failed(GenerationError.NO_MODEL_LOADED))
                return@withLock
            }
            stopRequested.set(false)
            val generation = if (request.grammar == null) {
                llama.beginGeneration(current.context, request.temperature, request.seed)
            } else {
                llama.beginGenerationWithGrammar(current.context, request.temperature, request.seed, request.grammar.json.toByteArray(Charsets.UTF_8))
            }
            if (generation == 0L) {
                emit(GenerationEvent.Failed(GenerationError.GRAMMAR_REJECTED))
                return@withLock
            }
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

    override suspend fun formatChat(messages: List<ChatMessage>): ChatPrompt? = mutex.withLock {
        withContext(dispatcher) {
            val current = session ?: return@withContext null
            val result = llama.formatChat(
                current.model,
                messages.map { it.role.wireName }.toTypedArray(),
                messages.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                addAssistant = true,
            ) ?: return@withContext null
            ChatPrompt(
                text = String(result, 1, result.size - 1, Charsets.UTF_8),
                usedFallbackTemplate = result[0] == 1.toByte(),
            )
        }
    }

    override suspend fun countTokens(prompt: String): Int? = mutex.withLock {
        withContext(dispatcher) {
            session?.let { llama.countTokens(it.model, prompt.toByteArray(Charsets.UTF_8)) }
        }
    }

    override fun contextLength(): Int? = session?.contextLength

    override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean): ToolChatPrompt? =
        mutex.withLock {
            withContext(dispatcher) {
                val current = session ?: return@withContext null
                val templates = current.templates ?: llama.chatTemplatesInit(current.model, current.chatTemplate.orEmpty().toByteArray(Charsets.UTF_8)).also { current.templates = it }
                if (templates == 0L) return@withContext null
                val request = JSONObject()
                    .put("messages", JSONArray(messages.map(::messageJson)))
                    .put("tools", JSONArray(tools.map { JSONObject().put("name", it.name).put("description", it.description).put("parameters", JSONObject(it.parametersJson)) }))
                    .put("enable_thinking", enableThinking)
                val reply = llama.applyChat(templates, request.toString().toByteArray(Charsets.UTF_8)) ?: return@withContext null
                toolChatPrompt(JSONObject(String(reply, Charsets.UTF_8)))
            }
        }

    override fun parseReply(format: ToolFormat, text: String, partial: Boolean): ParsedReply? {
        val request = JSONObject()
            .put("text", text)
            .put("partial", partial)
            .put("format", format.format)
            .put("parser", format.parser)
            .put("generation_prompt", format.generationPrompt)
        val reply = llama.parseChat(request.toString().toByteArray(Charsets.UTF_8)) ?: return null
        val json = JSONObject(String(reply, Charsets.UTF_8))
        val calls = json.getJSONArray("tool_calls")
        return ParsedReply(
            content = json.getString("content"),
            reasoning = json.getString("reasoning_content"),
            toolCalls = (0 until calls.length()).map { calls.getJSONObject(it) }.map { ToolCall(it.getString("name"), it.getString("arguments"), it.getString("id")) },
        )
    }

    override fun bruceToolGrammar(tools: List<ToolDefinition>): ToolGrammar? {
        val request = JSONArray(tools.map { JSONObject().put("name", it.name).put("parameters", JSONObject(it.parametersJson)) })
        val gbnf = llama.toolCallGrammar(request.toString().toByteArray(Charsets.UTF_8)) ?: return null
        return ToolGrammar.bruceFormat(String(gbnf, Charsets.UTF_8))
    }

    override fun templateSupportsTools(template: String, bosToken: String?, eosToken: String?): Boolean? =
        when (llama.templateSupportsTools(template.toByteArray(Charsets.UTF_8), bosToken.orEmpty().toByteArray(Charsets.UTF_8), eosToken.orEmpty().toByteArray(Charsets.UTF_8))) {
            1 -> true
            0 -> false
            else -> null
        }

    private fun messageJson(message: ToolChatMessage): JSONObject = JSONObject()
        .put("role", message.role.wireName)
        .put("content", message.content)
        .put("tool_call_id", message.toolCallId)
        .put("tool_name", message.toolName)
        .apply {
            if (message.toolCalls.isNotEmpty()) {
                put("tool_calls", JSONArray(message.toolCalls.map { JSONObject().put("name", it.name).put("arguments", it.argumentsJson).put("id", it.id) }))
            }
        }

    private fun toolChatPrompt(json: JSONObject): ToolChatPrompt {
        val grammarText = json.getString("grammar")
        val grammar = if (grammarText.isEmpty()) null else ToolGrammar(
            JSONObject()
                .put("grammar", grammarText)
                .put("grammar_lazy", json.getBoolean("grammar_lazy"))
                .put("grammar_triggers", json.getJSONArray("grammar_triggers"))
                .put("preserved_tokens", json.getJSONArray("preserved_tokens"))
                .toString(),
        )
        val stops = json.getJSONArray("additional_stops")
        return ToolChatPrompt(
            text = json.getString("prompt"),
            format = ToolFormat(
                format = json.getInt("format"),
                parser = json.getString("parser"),
                generationPrompt = json.getString("generation_prompt"),
                supportsTools = json.getBoolean("supports_tools"),
                grammar = grammar,
                stops = (0 until stops.length()).map(stops::getString),
            ),
        )
    }

    private suspend fun FlowCollector<GenerationEvent>.streamCompletion(generation: Long, request: GenerationRequest) {
        val promptStart = timeSource.markNow()
        val promptTokens = llama.evaluatePrompt(generation, request.prompt.toByteArray(Charsets.UTF_8), request.reusePrompt, request.checkpointPrefix.orEmpty().toByteArray(Charsets.UTF_8))
        when (promptTokens) {
            LlamaApi.PROMPT_TOO_LONG -> return emit(GenerationEvent.Failed(GenerationError.PROMPT_TOO_LONG))
            LlamaApi.DECODE_FAILED -> return emit(GenerationEvent.Failed(GenerationError.DECODE_FAILED))
        }
        val promptDuration = promptStart.elapsedNow()

        val generationStart = timeSource.markNow()
        val decoder = Utf8PieceDecoder()
        var generatedTokens = 0
        var reason = StopReason.MAX_TOKENS
        // Enough recent text to see a stop string that arrives across several tokens.
        val longestStop = request.stops.maxOfOrNull { it.length } ?: 0
        val recent = StringBuilder()
        while (generatedTokens < request.maxTokens) {
            if (stopRequested.get()) {
                reason = StopReason.STOPPED
                break
            }
            when (llama.nextToken(generation)) {
                LlamaApi.TOKEN -> {
                    generatedTokens++
                    val text = decoder.decode(llama.takePiece(generation))
                    emitText(text)
                    if (longestStop > 0) {
                        recent.append(text)
                        if (request.stops.any { recent.contains(it) }) {
                            reason = StopReason.END_OF_GENERATION
                            break
                        }
                        if (recent.length > 2 * longestStop) recent.delete(0, recent.length - longestStop)
                    }
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
        val plan = BackendSelection.plan(config.backend, getCapabilities())
        if (plan is BackendPlan.Unavailable) return LoadResult.Failed(LoadError.BACKEND_UNAVAILABLE)

        val failedBackends = mutableListOf<Backend>()
        var lastError = LoadError.MODEL_LOAD_FAILED
        for (attempt in (plan as BackendPlan.Attempts).attempts) {
            when (val result = openOn(file, config, attempt)) {
                is AttemptResult.Failed -> {
                    lastError = result.error
                    if (attempt.backend != Backend.CPU) failedBackends += attempt.backend
                }
                is AttemptResult.Opened -> {
                    session = result.session
                    return LoadResult.Loaded(result.session.info, attempt.backend, failedBackends)
                }
            }
        }
        return LoadResult.Failed(lastError)
    }

    private sealed interface AttemptResult {
        data class Opened(val session: Session) : AttemptResult

        data class Failed(val error: LoadError) : AttemptResult
    }

    private fun openOn(file: File, config: LoadConfig, attempt: LoadAttempt): AttemptResult {
        val gpuLayers = if (attempt.devices.isEmpty()) 0 else ALL_LAYERS
        val model = llama.loadModel(file.absolutePath, attempt.devices.map { it.index }.toIntArray(), gpuLayers)
        if (model == 0L) return AttemptResult.Failed(LoadError.MODEL_LOAD_FAILED)

        val context = llama.newContext(model, config.contextLength, config.threads, config.batchSize)
        if (context == 0L) {
            llama.freeModel(model)
            return AttemptResult.Failed(LoadError.CONTEXT_CREATION_FAILED)
        }

        val info = ModelInfo(
            description = llama.modelDescription(model),
            parameterCount = llama.modelParameterCount(model),
            sizeBytes = llama.modelSizeBytes(model),
            trainedContextLength = llama.modelTrainedContextLength(model),
        )
        return AttemptResult.Opened(Session(model, context, info, config.contextLength, config.chatTemplate))
    }

    private fun release() {
        val current = session ?: return
        session = null
        current.templates?.takeIf { it != 0L }?.let(llama::chatTemplatesFree)
        llama.freeContext(current.context)
        llama.freeModel(current.model)
    }

    private fun toBackend(registryName: String): Backend = when (registryName) {
        "CPU" -> Backend.CPU
        "Vulkan" -> Backend.VULKAN
        "OpenCL" -> Backend.OPENCL
        else -> Backend.OTHER
    }

    private fun toDeviceType(ggmlType: Int): DeviceType = when (ggmlType) {
        GGML_DEVICE_CPU -> DeviceType.CPU
        GGML_DEVICE_GPU -> DeviceType.GPU
        GGML_DEVICE_IGPU -> DeviceType.INTEGRATED_GPU
        GGML_DEVICE_ACCEL -> DeviceType.ACCELERATOR
        else -> DeviceType.OTHER
    }

    companion object {
        /** An engine backed by the native library, with every runnable backend loaded. */
        fun create(nativeLibraryDir: String, dispatcher: CoroutineDispatcher): LlamaCppEngine {
            LlamaNative.loadBackends(nativeLibraryDir)
            return LlamaCppEngine(LlamaNative, dispatcher)
        }

        // Values of enum ggml_backend_dev_type in ggml-backend.h.
        // More than any model has; llama.cpp clamps it to the model's layer count.
        private const val ALL_LAYERS = 999

        // VK_MAKE_API_VERSION(0, 1, 2, 0)
        private const val VULKAN_1_2 = (1 shl 22) or (2 shl 12)

        /** Imagination Technologies' PCI vendor ID, which PowerVR GPUs report. */
        private const val IMAGINATION_VENDOR_ID = 0x1010

        private const val GGML_DEVICE_CPU = 0
        private const val GGML_DEVICE_GPU = 1
        private const val GGML_DEVICE_IGPU = 2
        private const val GGML_DEVICE_ACCEL = 3
    }
}
