package com.bizzeh.bruce.models

import android.app.ActivityManager
import com.bizzeh.bruce.gguf.GgufMetadata

/**
 * Estimated RAM for a model: its weights plus the KV cache for [contextLength] tokens.
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

    fun estimate(metadata: GgufMetadata, contextLength: Int): MemoryEstimate {
        require(contextLength > 0) { "contextLength must be positive, was $contextLength" }
        val paddedContext = (contextLength + CONTEXT_PADDING - 1) / CONTEXT_PADDING * CONTEXT_PADDING
        return MemoryEstimate(
            weightsBytes = metadata.fileSizeBytes,
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
        return layers * context * kvHeads * (keySize + valueSize) * KV_BYTES_PER_ELEMENT
    }
}
