package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.settings.InferenceDefaults

/** A context size the phone can run with a model, and the memory the model then needs. */
data class ContextOption(val contextLength: Int, val totalBytes: Long)

/**
 * The context size Bruce uses when the user has not chosen one (Auto; TASK-073, rule from TASK-111):
 * the biggest choice the phone runs comfortably (memory estimate within [ModelFit.TIGHT_SHARE] of
 * [usableMemoryBytes]) that keeps the model's total within [CAP_BYTES]; for a model whose weights
 * alone pass the cap, the biggest such choice up to [LARGE_MODEL_MAX_CONTEXT]. Never bigger than the
 * model was trained for. The smallest choice when none is comfortable; the old fixed default when
 * the file does not declare enough to estimate the context's memory. A vision projector's
 * [projectorBytes] count against the room.
 */
object AutoContext {
    /** The owner's ceiling for Auto (2026-10-02): 6 GB, in the binary units Bruce shows sizes in. */
    const val CAP_BYTES = 6L shl 30

    const val LARGE_MODEL_MAX_CONTEXT = 16384

    fun pick(metadata: GgufMetadata?, usableMemoryBytes: Long, projectorBytes: Long = 0): Int {
        val choices = trainedChoices(metadata)
        if (metadata == null || !ModelMemory.estimate(metadata, choices.first()).complete) {
            return choices.lastOrNull { it <= InferenceDefaults.FALLBACK_CONTEXT } ?: choices.first()
        }
        val large = ModelMemory.estimate(metadata, choices.first(), projectorBytes).let { it.weightsBytes + it.projectorBytes } > CAP_BYTES
        return choices.lastOrNull { length ->
            val total = ModelMemory.estimate(metadata, length, projectorBytes).totalBytes
            total <= usableMemoryBytes * ModelFit.TIGHT_SHARE && (if (large) length <= LARGE_MODEL_MAX_CONTEXT else total <= CAP_BYTES)
        } ?: choices.first()
    }

    /**
     * The sizes the setup wizard offers (TASK-111): every choice up to what the model was trained for
     * whose memory estimate fits the phone at all. Empty when the file does not declare enough to
     * estimate, and the wizard then offers only Auto.
     */
    fun options(metadata: GgufMetadata?, usableMemoryBytes: Long, projectorBytes: Long = 0): List<ContextOption> {
        if (metadata == null || !ModelMemory.estimate(metadata, InferenceDefaults.CONTEXT_CHOICES.first()).complete) return emptyList()
        return trainedChoices(metadata)
            .map { ContextOption(it, ModelMemory.estimate(metadata, it, projectorBytes).totalBytes) }
            .filter { it.totalBytes <= usableMemoryBytes }
    }

    private fun trainedChoices(metadata: GgufMetadata?): List<Int> {
        val trained = metadata?.contextLength?.takeIf { it > 0 }
        return InferenceDefaults.CONTEXT_CHOICES.filter { trained == null || it <= trained }.ifEmpty { listOf(InferenceDefaults.CONTEXT_CHOICES.first()) }
    }
}
