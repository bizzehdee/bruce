package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.settings.InferenceDefaults

/** A context size the phone can run with a model, and the memory the model then needs. */
data class ContextOption(val contextLength: Int, val totalBytes: Long)

/**
 * The context size Bruce uses when the user has not chosen one (Auto; TASK-073, rule from TASK-111):
 * the biggest choice whose memory estimate, plus [OVERHEAD_BYTES] for the app and llama.cpp's working
 * buffers, stays within [RAM_SHARE] of the phone's RAM and keeps the model's total within [CAP_BYTES];
 * for a model whose weights alone pass the cap, the biggest such choice up to [LARGE_MODEL_MAX_CONTEXT].
 * Never bigger than the model was trained for. The smallest choice when none fits; the old fixed
 * default when the file does not declare enough to estimate, or the phone's RAM is unknown. A vision
 * projector's [projectorBytes] count against the room.
 *
 * Sized from total RAM, not from what is free at the moment: on the Pixel 11 (12 GB) a size chosen
 * from free memory (49,152 for MiniCPM5 2B) had Android drop the weights, read them back from storage
 * 218,000 times in one reply and kill Bruce for its swap; up to 32,768 nothing was read back (owner
 * chose a share of RAM, 2026-10-02; research/experiments/pixel-chat-replay-2026-10-02).
 */
object AutoContext {
    /** The owner's ceiling for Auto (2026-10-02): 6 GB, in the binary units Bruce shows sizes in. */
    const val CAP_BYTES = 6L shl 30

    const val LARGE_MODEL_MAX_CONTEXT = 16384

    /** Of the phone's RAM, what Bruce's model, context and working memory may take under Auto. */
    const val RAM_SHARE = 0.3

    /**
     * The app and llama.cpp's compute buffers, beyond the estimate: about 0.65 GB measured on the
     * Pixel 11 with MiniCPM5 2B at 49,152, of which 0.1 GB was the compute buffer, which shrinks with
     * the context.
     */
    const val OVERHEAD_BYTES = 512L shl 20

    fun pick(metadata: GgufMetadata?, totalMemoryBytes: Long, projectorBytes: Long = 0): Int {
        val choices = trainedChoices(metadata)
        if (metadata == null || totalMemoryBytes <= 0 || !ModelMemory.estimate(metadata, choices.first()).complete) {
            return choices.lastOrNull { it <= InferenceDefaults.FALLBACK_CONTEXT } ?: choices.first()
        }
        val large = ModelMemory.estimate(metadata, choices.first(), projectorBytes).let { it.weightsBytes + it.projectorBytes } > CAP_BYTES
        return choices.lastOrNull { length ->
            val total = ModelMemory.estimate(metadata, length, projectorBytes).totalBytes
            total + OVERHEAD_BYTES <= totalMemoryBytes * RAM_SHARE && (if (large) length <= LARGE_MODEL_MAX_CONTEXT else total <= CAP_BYTES)
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
