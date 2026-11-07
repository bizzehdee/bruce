package com.bizzeh.bruce.litert

import com.bizzeh.bruce.runtime.ChatBackend
import java.io.File

/**
 * The LiteRT-LM pipeline (TASK-118 onward), present only in Google Play/GitHub Releases builds
 * (TASK-117). `app/src/main` code must go through this interface rather than reference LiteRT-LM
 * types directly, since those do not exist in the `foss` flavour. `createLiteRtPipeline` (TASK-120)
 * is declared once per flavour — `app/src/full/.../LiteRtPipelineFactory.kt` returns a real
 * instance, `app/src/foss/.../LiteRtPipelineFactory.kt` returns null — rather than here, since this
 * file compiles into both.
 */
interface LiteRtPipeline {
    /**
     * Loads [file] as the active model, replacing any previously loaded one; null on failure.
     * [preferGpu] is null for the automatic choice (GPU only for a device-verified file, TASK-119's
     * allowlist, CPU otherwise), true to force a GPU attempt (falls back to CPU on failure), false
     * to force CPU. A caller should only pass true when [gpuAvailable] said so for this file.
     */
    suspend fun load(file: File, contextTokens: Int, preferGpu: Boolean? = null): LiteRtPipelineResult

    /** Releases the active model, if any. */
    suspend fun unload()

    /** The active model's maximum context length, or null when nothing is loaded. */
    fun contextLength(): Int?

    /** The active model's [ChatBackend] (TASK-120, for `BruceRuntime`'s `localOverride`), or null when nothing is loaded. */
    fun current(): ChatBackend?

    /** Whether [file] is on the device-verified GPU allowlist (TASK-119) — the only models for which GPU should ever be offered as a user choice. */
    fun gpuAvailable(file: File): Boolean
}

sealed interface LiteRtPipelineResult {
    data class Loaded(val backend: ChatBackend, val contextLength: Int) : LiteRtPipelineResult

    data object Failed : LiteRtPipelineResult
}
