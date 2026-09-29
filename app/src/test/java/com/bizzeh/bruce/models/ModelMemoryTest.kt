package com.bizzeh.bruce.models

import android.app.ActivityManager
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ModelMemoryTest {
    private val base = GgufMetadata(
        version = 3, architecture = "llama", name = null, parameterCount = 0, tensorCount = 0,
        contextLength = null, fileType = null, quantisation = null, fileSizeBytes = 1_000_000_000L,
    )

    @Test
    fun realFixtureKvCacheAtPaddedContext() {
        val fixture = (GgufReader.read(File("src/androidTest/assets/stories260K.gguf")) as GgufReadResult.Read).metadata

        val estimate = ModelMemory.estimate(fixture, contextLength = 128)

        // 5 layers x 256 tokens x 4 KV heads x (8 + 8) head size x 2 bytes.
        assertEquals(256, estimate.contextLength)
        assertEquals(5L * 256 * 4 * 16 * 2, estimate.kvCacheBytes)
        assertEquals(fixture.fileSizeBytes + 163_840L, estimate.totalBytes)
        assertTrue(estimate.complete)
    }

    /**
     * Shaped like Gemma 4 E4B: 42 layers, the last 18 sharing earlier layers' cache, every sixth
     * layer attending over the whole context and the rest over a 512-token window. llama.cpp itself
     * reported 104 MiB of cache at 4K and 552 MiB at 32K for that model (2026-09-29).
     */
    private val gemma = base.copy(
        architecture = "gemma4", blockCount = 42, embeddingLength = 2560, headCount = 8, headCountKv = 2, keyLength = 512, valueLength = 512,
        slidingWindow = 512, slidingWindowPattern = List(42) { (it + 1) % 6 != 0 }, keyLengthSwa = 256, valueLengthSwa = 256, sharedKvLayers = 18,
        fileSizeBytes = 5_335_289_792L, lookupOnlyBytes = 2_312_110_080L,
    )

    @Test
    fun slidingWindowAndSharedLayersAreSizedAsLlamaCppBuildsThem() {
        val mib = 1L shl 20
        assertEquals(104 * mib, ModelMemory.estimate(gemma, 4096).kvCacheBytes)
        assertEquals(552 * mib, ModelMemory.estimate(gemma, 32768).kvCacheBytes)
        assertEquals(4L * 512 * 2 * 1024 * 2 + 20L * 512 * 2 * 512 * 2, ModelMemory.estimate(gemma, 512).kvCacheBytes, "a window cache never exceeds the context")
    }

    @Test
    fun lookupOnlyTablesAreNotCountedAsMemory() {
        assertEquals(5_335_289_792L - 2_312_110_080L, ModelMemory.estimate(gemma, 4096).weightsBytes)
    }

    @Test
    fun aPatternThatDoesNotMatchTheLayersFallsBackToEveryLayerHoldingTheWholeContext() {
        val full = 42L * 4096 * 2 * 1024 * 2
        assertEquals(full, ModelMemory.estimate(gemma.copy(slidingWindowPattern = List(10) { true }), 4096).kvCacheBytes)
        assertEquals(full, ModelMemory.estimate(gemma.copy(slidingWindow = null), 4096).kvCacheBytes)
    }

    @Test
    fun groupedQueryAttentionModel() {
        // Shaped like a 4B model: 36 layers, 2560 wide, 32 query heads, 8 KV heads, 128 per head.
        val metadata = base.copy(blockCount = 36, embeddingLength = 2560, headCount = 32, headCountKv = 8, keyLength = 128, valueLength = 128)

        assertEquals(36L * 4096 * 8 * 256 * 2, ModelMemory.estimate(metadata, 4096).kvCacheBytes)
    }

    @Test
    fun missingKvHeadCountMeansOneKvHeadPerQueryHead() {
        val metadata = base.copy(blockCount = 2, embeddingLength = 64, headCount = 4)

        assertEquals(2L * 256 * 4 * (16 + 16) * 2, ModelMemory.estimate(metadata, 256).kvCacheBytes)
    }

    @Test
    fun explicitKeyAndValueLengthsOverrideEmbeddingDerivedSize() {
        val metadata = base.copy(blockCount = 1, embeddingLength = 64, headCount = 4, headCountKv = 1, keyLength = 256, valueLength = 128)

        assertEquals(1L * 256 * 1 * (256 + 128) * 2, ModelMemory.estimate(metadata, 1).kvCacheBytes)
    }

    @Test
    fun unknownShapeGivesWeightsOnlyEstimate() {
        val estimate = ModelMemory.estimate(base.copy(blockCount = 12), 2048)

        assertNull(estimate.kvCacheBytes)
        assertEquals(base.fileSizeBytes, estimate.totalBytes)
        assertFalse(estimate.complete)
    }

    @Test
    fun zeroHeadCountIsTreatedAsUnknown() {
        assertNull(ModelMemory.estimate(base.copy(blockCount = 1, embeddingLength = 64, headCount = 0), 256).kvCacheBytes)
    }

    @Test
    fun missingHeadSizeIsUnknown() {
        assertNull(ModelMemory.estimate(base.copy(blockCount = 1, headCount = 4), 256).kvCacheBytes)
        assertNull(ModelMemory.estimate(base.copy(blockCount = 1, headCount = 4, keyLength = 64), 256).kvCacheBytes)
    }

    @Test
    fun checkUsesAvailableMemoryAboveTheLowMemoryThreshold() {
        val estimate = MemoryEstimate(weightsBytes = 900, kvCacheBytes = 100, contextLength = 256)
        val memory = ActivityManager.MemoryInfo().apply {
            availMem = 1_200
            threshold = 200
        }

        val check = ModelMemory.check(estimate, memory)

        assertEquals(1_000, check.usableBytes)
        assertTrue(check.fits)
        assertFalse(ModelMemory.check(estimate.copy(kvCacheBytes = 101), memory).fits)
    }

    @Test
    fun usableMemoryIsNeverNegative() {
        val memory = ActivityManager.MemoryInfo().apply {
            availMem = 100
            threshold = 200
        }

        assertEquals(0, ModelMemory.check(MemoryEstimate(1, 0, 256), memory).usableBytes)
    }

    @Test
    fun contextMustBePositive() {
        assertThrows<IllegalArgumentException> { ModelMemory.estimate(base, 0) }
    }
}
