package com.bizzeh.bruce.models

import android.app.ActivityManager
import com.bizzeh.bruce.gguf.GgufMetadata

/**
 * Estimated RAM for a model: its weights (less tables llama.cpp only looks rows up in, which stay in
 * the mapped file) plus the KV cache for [contextLength] tokens.
 * This is memory that cannot be reclaimed. On CPUs where llama.cpp repacks weights (DOTPROD
 * and later), the original memory-mapped file pages also stay resident, so the process's RSS
 * can read up to about 1.6 times this; those pages are clean page cache that Android can drop.
 * Compute buffers measured about 20 MB and are not included
 * (.learnings/phase0-cpu-benchmarks.md).
 * [kvCacheBytes] is null when the file does not declare the attention shape, in which case
 * [totalBytes] covers the weights only and [complete] is false.
 */
data class MemoryEstimate(
    val weightsBytes: Long,
    val kvCacheBytes: Long?,
    val contextLength: Int,
) {
    val totalBytes: Long get() = weightsBytes + (kvCacheBytes ?: 0)

    val complete: Boolean get() = kvCacheBytes != null
}

data class MemoryCheck(val estimate: MemoryEstimate, val usableBytes: Long) {
    val fits: Boolean get() = estimate.totalBytes <= usableBytes
}

object ModelMemory {
    // llama.cpp rounds the context up to a multiple of 256 (.learnings/llama-context-padding.md).
    private const val CONTEXT_PADDING = 256

    // llama.cpp's default KV cache type is f16.
    private const val KV_BYTES_PER_ELEMENT = 2L

    // llama.cpp's default micro-batch, which a sliding-window cache holds on top of the window.
    private const val MICRO_BATCH = 512L

    fun estimate(metadata: GgufMetadata, contextLength: Int): MemoryEstimate {
        require(contextLength > 0) { "contextLength must be positive, was $contextLength" }
        val paddedContext = (contextLength + CONTEXT_PADDING - 1) / CONTEXT_PADDING * CONTEXT_PADDING
        return MemoryEstimate(
            weightsBytes = (metadata.fileSizeBytes - metadata.lookupOnlyBytes).coerceAtLeast(0),
            kvCacheBytes = kvCacheBytes(metadata, paddedContext.toLong()),
            contextLength = paddedContext,
        )
    }

    fun check(estimate: MemoryEstimate, memoryInfo: ActivityManager.MemoryInfo): MemoryCheck =
        MemoryCheck(estimate, usableBytes = (memoryInfo.availMem - memoryInfo.threshold).coerceAtLeast(0))

    private fun kvCacheBytes(metadata: GgufMetadata, context: Long): Long? {
        val layers = metadata.blockCount ?: return null
        val heads = metadata.headCount?.takeIf { it > 0 } ?: return null
        val kvHeads = metadata.headCountKv ?: heads
        val defaultHeadSize = metadata.embeddingLength?.let { it / heads }
        val keySize = metadata.keyLength ?: defaultHeadSize ?: return null
        val valueSize = metadata.valueLength ?: defaultHeadSize ?: return null
        val pattern = metadata.slidingWindowPattern?.takeIf { it.size.toLong() == layers }
        val window = metadata.slidingWindow?.takeIf { it > 0 }
        if (pattern == null || window == null) return layers * context * kvHeads * (keySize + valueSize) * KV_BYTES_PER_ELEMENT
        // As llama.cpp builds it (llama-kv-cache-iswa.cpp): full-attention layers hold the whole
        // context, sliding-window layers the window plus a micro-batch, and shared layers nothing.
        val ownLayers = pattern.take((layers - (metadata.sharedKvLayers ?: 0)).coerceIn(0, layers).toInt())
        val windowLayers = ownLayers.count { it }.toLong()
        val fullLayers = ownLayers.size - windowLayers
        val windowCells = minOf(context, (window + MICRO_BATCH + CONTEXT_PADDING - 1) / CONTEXT_PADDING * CONTEXT_PADDING)
        val windowKey = metadata.keyLengthSwa ?: keySize
        val windowValue = metadata.valueLengthSwa ?: valueSize
        return (fullLayers * context * (keySize + valueSize) + windowLayers * windowCells * (windowKey + windowValue)) * kvHeads * KV_BYTES_PER_ELEMENT
    }
}
