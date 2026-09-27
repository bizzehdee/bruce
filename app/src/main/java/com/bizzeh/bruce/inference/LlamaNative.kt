package com.bizzeh.bruce.inference

internal interface LlamaApi {
    fun version(): String

    /** Returns a native model handle, or 0 if the model could not be loaded. */
    fun loadModel(path: String): Long

    fun freeModel(model: Long)

    /** Returns a native context handle, or 0 if the context could not be created. */
    fun newContext(model: Long, contextLength: Int, threads: Int, batchSize: Int): Long

    fun freeContext(context: Long)

    fun modelDescription(model: Long): String

    fun modelParameterCount(model: Long): Long

    fun modelSizeBytes(model: Long): Long

    fun modelTrainedContextLength(model: Long): Int

    /** ggml_backend_dev_type values, one per registered device. */
    fun deviceTypes(): IntArray

    /** Clears the context and returns a generation handle; release it with [endGeneration]. */
    fun beginGeneration(context: Long, temperature: Float, seed: Int): Long

    /** Returns the prompt token count, or [PROMPT_TOO_LONG] or [DECODE_FAILED]. */
    fun evaluatePrompt(generation: Long, promptUtf8: ByteArray): Int

    /** Returns [TOKEN], [END_OF_GENERATION], [CONTEXT_FULL] or [DECODE_FAILED]. */
    fun nextToken(generation: Long): Int

    /** UTF-8 bytes of the token from the last [TOKEN] result; may end mid-character. */
    fun takePiece(generation: Long): ByteArray

    fun endGeneration(generation: Long)

    companion object {
        // Must match the status codes in llama_jni.cpp.
        const val PROMPT_TOO_LONG = -1
        const val DECODE_FAILED = -2
        const val TOKEN = 0
        const val END_OF_GENERATION = 1
        const val CONTEXT_FULL = 2
    }
}

internal object LlamaNative : LlamaApi {
    init {
        System.loadLibrary("bruce")
        initBackend()
    }

    private external fun initBackend()

    external override fun version(): String

    external override fun loadModel(path: String): Long

    external override fun freeModel(model: Long)

    external override fun newContext(model: Long, contextLength: Int, threads: Int, batchSize: Int): Long

    external override fun freeContext(context: Long)

    external override fun modelDescription(model: Long): String

    external override fun modelParameterCount(model: Long): Long

    external override fun modelSizeBytes(model: Long): Long

    external override fun modelTrainedContextLength(model: Long): Int

    external override fun deviceTypes(): IntArray

    external override fun beginGeneration(context: Long, temperature: Float, seed: Int): Long

    external override fun evaluatePrompt(generation: Long, promptUtf8: ByteArray): Int

    external override fun nextToken(generation: Long): Int

    external override fun takePiece(generation: Long): ByteArray

    external override fun endGeneration(generation: Long)
}
