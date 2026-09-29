package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoContextTest {
    private val mb = 1L shl 20
    private val gb = 1L shl 30

    /** Shaped like Llama 3.2 1B: 800 MB of weights and 32 KB of cache per token, trained for 128K. */
    private val model = GgufMetadata(
        version = 3, architecture = "llama", name = null, parameterCount = 1_240_000_000, tensorCount = 147, contextLength = 131_072,
        fileType = 15, quantisation = "Q4_K_M", fileSizeBytes = 800 * mb,
        blockCount = 16, embeddingLength = 2048, headCount = 32, headCountKv = 8, keyLength = 64, valueLength = 64,
    )

    @Test
    fun theBiggestSizeThatLeavesRoomIsPicked() {
        assertEquals(65536, AutoContext.pick(model, 4 * gb), "64K takes 2.8 GB, within 80% of 4 GB")
        assertEquals(24576, AutoContext.pick(model, 2 * gb), "24K takes 1.55 GB; 32K's 1.8 GB would crowd 2 GB")
        assertEquals(2048, AutoContext.pick(model, 512 * mb), "nothing is comfortable: the smallest size")
    }

    @Test
    fun neverBeyondWhatTheModelWasTrainedFor() {
        assertEquals(8192, AutoContext.pick(model.copy(contextLength = 8192), 16 * gb))
        assertEquals(2048, AutoContext.pick(model.copy(contextLength = 1000), 16 * gb))
    }

    @Test
    fun withoutEnoughToEstimateTheContextTheFallbackIsUsed() {
        assertEquals(4096, AutoContext.pick(model.copy(blockCount = null), 16 * gb))
        assertEquals(2048, AutoContext.pick(model.copy(blockCount = null, contextLength = 2048), 16 * gb))
        assertEquals(4096, AutoContext.pick(null, 16 * gb))
    }
}
