package com.bizzeh.bruce.litert

import com.bizzeh.bruce.runtime.ChatBackend
import java.io.File

/**
 * Holds the one loaded LiteRT-LM model at a time (TASK-120), closing the previous one before
 * opening the next — mirrors how [com.bizzeh.bruce.models.ActiveModel] holds llama.cpp's one loaded
 * model. Always loads on the CPU/GPU-fallback plan ([LiteRtBackendPreference.AUTO]); a user-facing
 * backend choice for this pipeline is not yet wired (TASK-119's GPU half exists but has no setting).
 */
internal class LiteRtPipelineImpl(private val api: LiteRtApi, private val nativeLibraryDir: String) : LiteRtPipeline {
    private data class Active(val model: LiteRtModel, val backend: LiteRtBackend)

    @Volatile private var active: Active? = null

    override suspend fun load(file: File, contextTokens: Int): LiteRtPipelineResult {
        active?.model?.close()
        active = null
        return when (val result = api.loadModel(file, contextTokens, LiteRtBackendPreference.AUTO, nativeLibraryDir)) {
            is LiteRtLoadResult.Loaded -> {
                val backend = LiteRtBackend(result.model)
                active = Active(result.model, backend)
                LiteRtPipelineResult.Loaded(backend, result.model.maxContextTokens)
            }
            LiteRtLoadResult.Failed -> LiteRtPipelineResult.Failed
        }
    }

    override suspend fun unload() {
        active?.model?.close()
        active = null
    }

    override fun contextLength(): Int? = active?.model?.maxContextTokens

    override fun current(): ChatBackend? = active?.backend
}
