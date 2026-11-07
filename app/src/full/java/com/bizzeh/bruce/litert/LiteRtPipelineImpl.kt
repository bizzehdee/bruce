package com.bizzeh.bruce.litert

import com.bizzeh.bruce.runtime.ChatBackend
import java.io.File

/**
 * Holds the one loaded LiteRT-LM model at a time (TASK-120), closing the previous one before
 * opening the next — mirrors how [com.bizzeh.bruce.models.ActiveModel] holds llama.cpp's one loaded
 * model. Every other LiteRT-LM model loads CPU-only ([LiteRtBackendPreference.AUTO]); there is no
 * user-facing backend choice for this pipeline (TASK-119's GPU half exists but has no setting).
 */
internal class LiteRtPipelineImpl(private val api: LiteRtApi, private val nativeLibraryDir: String) : LiteRtPipeline {
    private data class Active(val model: LiteRtModel, val backend: LiteRtBackend)

    @Volatile private var active: Active? = null

    override suspend fun load(file: File, contextTokens: Int): LiteRtPipelineResult {
        active?.model?.close()
        active = null
        val preference = if (file.name in GPU_VERIFIED_MODELS) LiteRtBackendPreference.GPU else LiteRtBackendPreference.AUTO
        return when (val result = api.loadModel(file, contextTokens, preference, nativeLibraryDir)) {
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

    private companion object {
        /**
         * Exact file names of plain `.litertlm` models device-verified (Pixel 11, PowerVR GPU) to
         * give correct GPU output, with the same CPU fallback already proven for NPU covering any
         * later regression. Not a size or model-family rule: `.learnings/litert-lm-gpu-model-sweep.md`
         * found no clean rule — smaller models fail (gemma-3-270m-it: wrong output) and some larger
         * ones crash (Qwen3.5-2B, Phi-4-mini-instruct) while others this size or bigger succeed, so
         * this list is per-file, grown only by testing a new file the same way, never guessed.
         */
        val GPU_VERIFIED_MODELS = setOf(
            "gemma-4-E2B-it.litertlm",
            "gemma-4-E4B-it.litertlm",
            "MiniCPM5-1B_dynamic_wi8_afp32.litertlm",
            "MiniCPM5-2B_int8.litertlm",
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            "Qwen3-0.6B.litertlm",
            "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            "granite-4.0-350m_q8_ekv1280.litertlm",
        )
    }
}
