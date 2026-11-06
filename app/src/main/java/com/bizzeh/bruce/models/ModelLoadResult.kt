package com.bizzeh.bruce.models

import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.ModelInfo

/**
 * What choosing a model actually resulted in (TASK-120), across both pipelines.
 * [com.bizzeh.bruce.inference.LoadResult] stays llama.cpp/GGUF-specific; this is the
 * format-agnostic result [ActiveModel.loadAny] and [ModelSelection.choose] return.
 */
sealed interface ModelLoadResult {
    /** [info] and [backend] are null for a LiteRT-LM model, which has no equivalent metadata (TASK-120). */
    data class Loaded(val format: ModelFormat, val info: ModelInfo? = null, val backend: Backend? = null) : ModelLoadResult

    data class Failed(val error: LoadError) : ModelLoadResult
}
