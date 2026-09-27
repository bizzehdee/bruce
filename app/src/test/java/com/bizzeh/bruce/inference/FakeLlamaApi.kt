package com.bizzeh.bruce.inference

internal class FakeLlamaApi : LlamaApi {
    var nextModelHandle = 10L
    var nextContextHandle = 20L
    var devices = intArrayOf(0)

    val loadedPaths = mutableListOf<String>()
    val freedModels = mutableListOf<Long>()
    val freedContexts = mutableListOf<Long>()
    var lastContextRequest: Pair<Int, Int>? = null

    override fun version() = "fake"

    override fun loadModel(path: String): Long {
        loadedPaths += path
        return nextModelHandle
    }

    override fun freeModel(model: Long) {
        freedModels += model
    }

    override fun newContext(model: Long, contextLength: Int, threads: Int): Long {
        lastContextRequest = contextLength to threads
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
}
