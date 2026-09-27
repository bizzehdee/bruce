package com.bizzeh.bruce.inference

internal class FakeLlamaApi : LlamaApi {
    var nextModelHandle = 10L
    var nextContextHandle = 20L
    var devices = intArrayOf(0)

    val loadedPaths = mutableListOf<String>()
    val freedModels = mutableListOf<Long>()
    val freedContexts = mutableListOf<Long>()
    var lastContextRequest: Triple<Int, Int, Int>? = null

    override fun version() = "fake"

    override fun loadModel(path: String): Long {
        loadedPaths += path
        return nextModelHandle
    }

    override fun freeModel(model: Long) {
        freedModels += model
    }

    override fun newContext(model: Long, contextLength: Int, threads: Int, batchSize: Int): Long {
        lastContextRequest = Triple(contextLength, threads, batchSize)
        return nextContextHandle
    }

    override fun freeContext(context: Long) {
        freedContexts += context
    }

    override fun modelDescription(model: Long) = "fake model $model"

    override fun modelParameterCount(model: Long) = 260_000L

    override fun modelSizeBytes(model: Long) = 1_000_000L

    override fun modelTrainedContextLength(model: Long) = 512

    override fun deviceTypes() = devices

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

    override fun evaluatePrompt(generation: Long, promptUtf8: ByteArray): Int {
        lastPrompt = promptUtf8
        return promptResult
    }

    override fun nextToken(generation: Long): Int {
        onNextToken()
        val result = tokenResults.removeFirstOrNull() ?: LlamaApi.END_OF_GENERATION
        if (result == LlamaApi.TOKEN) currentPiece = pieces.removeFirst()
        return result
    }

    override fun takePiece(generation: Long) = currentPiece

    override fun endGeneration(generation: Long) {
        endedGenerations += generation
    }
}
