package com.bizzeh.bruce.inference

internal interface LlamaApi {
    fun version(): String

    /**
     * Loads a model onto the ggml devices at [deviceIndices], offloading [gpuLayers] layers;
     * an empty array keeps it on the CPU. Returns a native model handle, or 0 on failure.
     */
    fun loadModel(path: String, deviceIndices: IntArray, gpuLayers: Int): Long

    fun freeModel(model: Long)

    /** Returns a native context handle, or 0 if the context could not be created. */
    fun newContext(model: Long, contextLength: Int, threads: Int, batchSize: Int): Long

    fun freeContext(context: Long)

    fun modelDescription(model: Long): String

    fun modelParameterCount(model: Long): Long

    fun modelSizeBytes(model: Long): Long

    fun modelTrainedContextLength(model: Long): Int

    fun deviceCount(): Int

    /** {backend name, device name, device description} for device [index]. */
    fun deviceStrings(index: Int): Array<String>

    /** A ggml_backend_dev_type value. */
    fun deviceType(index: Int): Int

    fun deviceMemoryBytes(index: Int): Long

    /** The Vulkan API version of the physical device named [deviceName], or 0 if unknown. */
    fun vulkanDeviceApiVersion(deviceName: String): Int

    /** Features the loaded CPU backend variant was compiled with, as "NAME=value". */
    fun cpuBackendFeatures(): Array<String>

    /**
     * Formats a conversation with the model's chat template (ChatML if it has none usable).
     * Returns the fallback flag byte followed by UTF-8 prompt bytes, or null on failure.
     */
    fun formatChat(model: Long, roles: Array<String>, contents: Array<ByteArray>, addAssistant: Boolean): ByteArray?

    /** Clears the context and returns a generation handle; release it with [endGeneration]. */
    fun beginGeneration(context: Long, temperature: Float, seed: Int): Long

    /** As [beginGeneration], constrained by [grammarJson] ([ToolGrammar.json]); 0 if the grammar is unusable. */
    fun beginGenerationWithGrammar(context: Long, temperature: Float, seed: Int, grammarJson: ByteArray): Long

    /** The model's chat templates for tool formats; 0 on failure. Free with [chatTemplatesFree]. */
    fun chatTemplatesInit(model: Long): Long

    fun chatTemplatesFree(templates: Long)

    /** Applies the templates to a JSON request (chat_tools.cpp); UTF-8 JSON reply, or null. */
    fun applyChat(templates: Long, request: ByteArray): ByteArray?

    /** Parses a reply described by a JSON request (chat_tools.cpp); UTF-8 JSON reply, or null. */
    fun parseChat(request: ByteArray): ByteArray?

    /** A GBNF grammar for Bruce's own tool-call format over the tools in the JSON request, or null. */
    fun toolCallGrammar(request: ByteArray): ByteArray?

    /** How many tokens [textUtf8] is for [model], as a prompt: special tokens parsed, BOS added. */
    fun countTokens(model: Long, textUtf8: ByteArray): Int

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
    }

    /**
     * Loads every ggml backend library in [nativeLibraryDir] that this phone can run,
     * choosing the best CPU variant. Must run before a model is loaded; later calls do nothing.
     */
    external fun loadBackends(nativeLibraryDir: String)

    external override fun version(): String

    external override fun loadModel(path: String, deviceIndices: IntArray, gpuLayers: Int): Long

    external override fun freeModel(model: Long)

    external override fun newContext(model: Long, contextLength: Int, threads: Int, batchSize: Int): Long

    external override fun countTokens(model: Long, textUtf8: ByteArray): Int

    external override fun freeContext(context: Long)

    external override fun modelDescription(model: Long): String

    external override fun modelParameterCount(model: Long): Long

    external override fun modelSizeBytes(model: Long): Long

    external override fun modelTrainedContextLength(model: Long): Int

    external override fun deviceCount(): Int

    external override fun deviceStrings(index: Int): Array<String>

    external override fun deviceType(index: Int): Int

    external override fun deviceMemoryBytes(index: Int): Long

    external override fun vulkanDeviceApiVersion(deviceName: String): Int

    external override fun cpuBackendFeatures(): Array<String>

    external override fun formatChat(model: Long, roles: Array<String>, contents: Array<ByteArray>, addAssistant: Boolean): ByteArray?

    external override fun beginGeneration(context: Long, temperature: Float, seed: Int): Long

    external override fun beginGenerationWithGrammar(context: Long, temperature: Float, seed: Int, grammarJson: ByteArray): Long

    external override fun chatTemplatesInit(model: Long): Long

    external override fun chatTemplatesFree(templates: Long)

    external override fun applyChat(templates: Long, request: ByteArray): ByteArray?

    external override fun parseChat(request: ByteArray): ByteArray?

    external override fun toolCallGrammar(request: ByteArray): ByteArray?

    external override fun evaluatePrompt(generation: Long, promptUtf8: ByteArray): Int

    external override fun nextToken(generation: Long): Int

    external override fun takePiece(generation: Long): ByteArray

    external override fun endGeneration(generation: Long)
}
