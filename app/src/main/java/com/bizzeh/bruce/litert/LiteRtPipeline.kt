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
    /** Loads [file] as the active model, replacing any previously loaded one; null on failure. */
    suspend fun load(file: File, contextTokens: Int): LiteRtPipelineResult

    /** Releases the active model, if any. */
    suspend fun unload()

    /** The active model's maximum context length, or null when nothing is loaded. */
    fun contextLength(): Int?

    /** The active model's [ChatBackend] (TASK-120, for `BruceRuntime`'s `localOverride`), or null when nothing is loaded. */
    fun current(): ChatBackend?
}

sealed interface LiteRtPipelineResult {
    data class Loaded(val backend: ChatBackend, val contextLength: Int) : LiteRtPipelineResult

    data object Failed : LiteRtPipelineResult
}
