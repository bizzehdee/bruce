package com.bizzeh.bruce.litert

/** Which backend to load a LiteRT-LM model on (TASK-119); [AUTO] currently always chooses the CPU. */
internal enum class LiteRtBackendPreference {
    AUTO,
    CPU,
    GPU,

    /** The Tensor G5/G6 NPU delegate; falls back to the CPU on a phone without one. */
    NPU,
}

internal enum class LiteRtBackendChoice {
    CPU,
    GPU,
    NPU,
}

internal object LiteRtBackendSelection {
    /**
     * Attempts in order; the last is always [LiteRtBackendChoice.CPU]. Auto never picks the GPU or
     * NPU — the same conservative default [com.bizzeh.bruce.inference.BackendSelection] uses for
     * llama.cpp, until a delegate is proven reliable on a real phone; a GPU or NPU is used only
     * when the user picks it. A phone without a Tensor chip falls back to the CPU on the native
     * side (`LiteRtNative.openOn`'s try/catch), not here: this function does not need to know the
     * phone's SoC.
     */
    fun plan(preference: LiteRtBackendPreference): List<LiteRtBackendChoice> = when (preference) {
        LiteRtBackendPreference.AUTO, LiteRtBackendPreference.CPU -> listOf(LiteRtBackendChoice.CPU)
        LiteRtBackendPreference.GPU -> listOf(LiteRtBackendChoice.GPU, LiteRtBackendChoice.CPU)
        LiteRtBackendPreference.NPU -> listOf(LiteRtBackendChoice.NPU, LiteRtBackendChoice.CPU)
    }
}
