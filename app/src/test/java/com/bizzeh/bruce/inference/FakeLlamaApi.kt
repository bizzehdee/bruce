package com.bizzeh.bruce.inference

internal class FakeLlamaApi : LlamaApi {
    var nextModelHandle = 10L
    var nextContextHandle = 20L
    /** backend, name, description, ggml type, memory */
    var devices = listOf(Device("CPU", "CPU", "Cortex", 0, 8L shl 30))
    var cpuFeatures = arrayOf("NEON=1")

    data class Device(val backend: String, val name: String, val description: String, val type: Int, val memory: Long)

    val loadedPaths = mutableListOf<String>()
    val freedModels = mutableListOf<Long>()
    val freedContexts = mutableListOf<Long>()
    var lastContextRequest: Triple<Int, Int, Int>? = null

    override fun version() = "fake"

    /** When non-empty, each loadModel call takes its result from here instead of [nextModelHandle]. */
    val modelHandles = ArrayDeque<Long>()
    val contextHandles = ArrayDeque<Long>()
    val loadRequests = mutableListOf<Pair<List<Int>, Int>>()

    override fun loadModel(path: String, deviceIndices: IntArray, gpuLayers: Int): Long {
        loadedPaths += path
        loadRequests += deviceIndices.toList() to gpuLayers
        return modelHandles.removeFirstOrNull() ?: nextModelHandle
    }

    override fun freeModel(model: Long) {
        freedModels += model
    }

    /** One token per word, so tests can reason about counts. */
    override fun countTokens(model: Long, textUtf8: ByteArray): Int = String(textUtf8).split(' ').size

    override fun newContext(model: Long, contextLength: Int, threads: Int, batchSize: Int): Long {
        lastContextRequest = Triple(contextLength, threads, batchSize)
        return contextHandles.removeFirstOrNull() ?: nextContextHandle
    }

    override fun freeContext(context: Long) {
        freedContexts += context
    }

    override fun modelDescription(model: Long) = "fake model $model"

    override fun modelParameterCount(model: Long) = 260_000L

    override fun modelSizeBytes(model: Long) = 1_000_000L

    override fun modelTrainedContextLength(model: Long) = 512

    override fun deviceCount() = devices.size

    override fun deviceStrings(index: Int) = devices[index].let { arrayOf(it.backend, it.name, it.description) }

    override fun deviceType(index: Int) = devices[index].type

    override fun deviceMemoryBytes(index: Int) = devices[index].memory

    var vulkanApiVersions = mapOf<String, Int>()

    override fun vulkanDeviceApiVersion(deviceName: String) = vulkanApiVersions[deviceName] ?: 0

    override fun cpuBackendFeatures() = cpuFeatures

    var promptResult = 3
    /** Scripted nextToken results; each TOKEN consumes the next entry of [pieces]. */
    val tokenResults = ArrayDeque<Int>()
    val pieces = ArrayDeque<ByteArray>()
    var onNextToken: () -> Unit = {}
    var generationRequest: Triple<Long, Float, Int>? = null
    var lastPrompt: ByteArray? = null
    val endedGenerations = mutableListOf<Long>()
    private var currentPiece = ByteArray(0)

    fun script(vararg texts: String, end: Int = LlamaApi.END_OF_GENERATION) {
        texts.forEach { tokenPiece(it.toByteArray()) }
        tokenResults += end
    }

    fun tokenPiece(bytes: ByteArray) {
        tokenResults += LlamaApi.TOKEN
        pieces += bytes
    }

    override fun beginGeneration(context: Long, temperature: Float, seed: Int): Long {
        generationRequest = Triple(context, temperature, seed)
        return 30L
    }

    var lastReuse: Boolean? = null

    var templateAnswer = 1
    override fun templateSupportsTools(templateUtf8: ByteArray, bosUtf8: ByteArray, eosUtf8: ByteArray): Int = templateAnswer

    var lastCheckpointPrefix: String? = null

    override fun evaluatePrompt(generation: Long, promptUtf8: ByteArray, reuse: Boolean, checkpointPrefixUtf8: ByteArray): Int {
        lastPrompt = promptUtf8
        lastReuse = reuse
        lastCheckpointPrefix = String(checkpointPrefixUtf8).ifEmpty { null }
        return promptResult
    }

    override fun nextToken(generation: Long): Int {
        onNextToken()
        val result = tokenResults.removeFirstOrNull() ?: LlamaApi.END_OF_GENERATION
        if (result == LlamaApi.TOKEN) currentPiece = pieces.removeFirst()
        return result
    }

    override fun takePiece(generation: Long) = currentPiece

    var chatResult: ByteArray? = byteArrayOf(0) + "<|im_start|>user".toByteArray()
    var chatRequest: Triple<Long, List<String>, List<String>>? = null

    override fun formatChat(model: Long, roles: Array<String>, contents: Array<ByteArray>, addAssistant: Boolean): ByteArray? {
        chatRequest = Triple(model, roles.toList(), contents.map { it.toString(Charsets.UTF_8) })
        return chatResult
    }

    override fun endGeneration(generation: Long) {
        endedGenerations += generation
    }

    var grammarGenerationResult = 31L
    var lastGrammar: String? = null

    override fun beginGenerationWithGrammar(context: Long, temperature: Float, seed: Int, grammarJson: ByteArray): Long {
        generationRequest = Triple(context, temperature, seed)
        lastGrammar = grammarJson.toString(Charsets.UTF_8)
        return grammarGenerationResult
    }

    var templatesHandle = 40L
    var templateInits = 0
    val freedTemplates = mutableListOf<Long>()

    var templateOverride: String? = null
    override fun chatTemplatesInit(model: Long, overrideUtf8: ByteArray): Long {
        templateOverride = String(overrideUtf8).ifEmpty { null }
        templateInits++
        return templatesHandle
    }

    override fun chatTemplatesFree(templates: Long) {
        freedTemplates += templates
    }

    var applyChatReply: String? = null
    var lastApplyChat: String? = null

    override fun applyChat(templates: Long, request: ByteArray): ByteArray? {
        lastApplyChat = request.toString(Charsets.UTF_8)
        return applyChatReply?.toByteArray(Charsets.UTF_8)
    }

    var parseChatReply: String? = null
    var lastParseChat: String? = null

    override fun parseChat(request: ByteArray): ByteArray? {
        lastParseChat = request.toString(Charsets.UTF_8)
        return parseChatReply?.toByteArray(Charsets.UTF_8)
    }

    var toolCallGrammarReply: String? = "root ::= \"x\""
    var lastToolCallGrammar: String? = null

    override fun toolCallGrammar(request: ByteArray): ByteArray? {
        lastToolCallGrammar = request.toString(Charsets.UTF_8)
        return toolCallGrammarReply?.toByteArray(Charsets.UTF_8)
    }
}
