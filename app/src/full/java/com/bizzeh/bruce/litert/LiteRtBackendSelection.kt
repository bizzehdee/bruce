package com.bizzeh.bruce.litert

/** Which backend to load a LiteRT-LM model on (TASK-119); [AUTO] currently always chooses the CPU. */
internal enum class LiteRtBackendPreference {
    AUTO,
    CPU,
    GPU,
}

internal enum class LiteRtBackendChoice {
    CPU,
    GPU,
}

internal object LiteRtBackendSelection {
    /**
     * Attempts in order; the last is always [LiteRtBackendChoice.CPU]. Auto never picks the GPU —
     * the same conservative default [com.bizzeh.bruce.inference.BackendSelection] uses for
     * llama.cpp, until a GPU delegate is proven reliable on a real phone; a GPU is used only when
     * the user picks it.
     */
    fun plan(preference: LiteRtBackendPreference): List<LiteRtBackendChoice> = when (preference) {
        LiteRtBackendPreference.AUTO, LiteRtBackendPreference.CPU -> listOf(LiteRtBackendChoice.CPU)
        LiteRtBackendPreference.GPU -> listOf(LiteRtBackendChoice.GPU, LiteRtBackendChoice.CPU)
    }
}
