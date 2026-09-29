package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.settings.InferenceDefaults

/**
 * The context size Bruce uses when the user has not chosen one (TASK-073): the biggest choice
 * that leaves the phone room for other apps, meaning its memory estimate stays in the comfortable
 * band (at most [ModelFit.TIGHT_SHARE] of [usableMemoryBytes]), and no bigger than the model was
 * trained for. The smallest choice when none is comfortable; the old fixed default when the file
 * does not declare enough to estimate the context's memory.
 */
object AutoContext {
    fun pick(metadata: GgufMetadata?, usableMemoryBytes: Long): Int {
        val trained = metadata?.contextLength?.takeIf { it > 0 }
        val choices = InferenceDefaults.CONTEXT_CHOICES.filter { trained == null || it <= trained }.ifEmpty { listOf(InferenceDefaults.CONTEXT_CHOICES.first()) }
        if (metadata == null || !ModelMemory.estimate(metadata, choices.first()).complete) {
            return choices.lastOrNull { it <= InferenceDefaults.FALLBACK_CONTEXT } ?: choices.first()
        }
        return choices.lastOrNull { ModelMemory.estimate(metadata, it).totalBytes <= usableMemoryBytes * ModelFit.TIGHT_SHARE } ?: choices.first()
    }
}
