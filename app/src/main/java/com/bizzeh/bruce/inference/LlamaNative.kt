package com.bizzeh.bruce.inference

internal interface LlamaApi {
    fun version(): String

    /** Returns a native model handle, or 0 if the model could not be loaded. */
    fun loadModel(path: String): Long

    fun freeModel(model: Long)

    /** Returns a native context handle, or 0 if the context could not be created. */
    fun newContext(model: Long, contextLength: Int, threads: Int): Long

    fun freeContext(context: Long)

    fun modelDescription(model: Long): String

    fun modelParameterCount(model: Long): Long

    fun modelSizeBytes(model: Long): Long

    fun modelTrainedContextLength(model: Long): Int

    /** ggml_backend_dev_type values, one per registered device. */
    fun deviceTypes(): IntArray
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

    external override fun newContext(model: Long, contextLength: Int, threads: Int): Long

    external override fun freeContext(context: Long)

    external override fun modelDescription(model: Long): String

    external override fun modelParameterCount(model: Long): Long

    external override fun modelSizeBytes(model: Long): Long

    external override fun modelTrainedContextLength(model: Long): Int

    external override fun deviceTypes(): IntArray
}
