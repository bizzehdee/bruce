package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun theBiggestSizeWithinThirtyPercentOfRamIsPicked() {
        assertEquals(65536, AutoContext.pick(model, 12 * gb), "64K and the app take 3.3 GB, within 3.6 GB")
        assertEquals(32768, AutoContext.pick(model, 8 * gb), "32K and the app take 2.3 GB; 48K's 2.8 GB passes 2.4 GB")
        assertEquals(16384, AutoContext.pick(model, 6 * gb))
        assertEquals(2048, AutoContext.pick(model, 4 * gb), "nothing fits 1.2 GB: the smallest size")
    }

    @Test
    fun whatIsFreeAtTheMomentDoesNotChangeAuto() {
        val profile = DeviceProfile(usableMemoryBytes = 1 * gb, cpu = com.bizzeh.bruce.hardware.CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false), totalMemoryBytes = 12 * gb)
        assertEquals(65536, AutoContext.pick(model, profile.totalMemoryBytes))
    }

    @Test
    fun aVisionProjectorTakesItsShareOfTheRoom() {
        assertEquals(49152, AutoContext.pick(model, 12 * gb, projectorBytes = 800 * mb), "64K plus the projector would pass 3.6 GB; 48K fits")
        assertEquals(65536, ModelMemory.estimate(model, 65536, 800 * mb).contextLength)
        assertEquals(ModelMemory.estimate(model, 2048).totalBytes + 800 * mb, ModelMemory.estimate(model, 2048, 800 * mb).totalBytes)
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
        assertEquals(4096, AutoContext.pick(model, 0), "the phone's RAM is unknown")
    }

    @Test
    fun autoKeepsTheModelWithinSixGigabytes() {
        val bigger = model.copy(fileSizeBytes = 4608 * mb)
        val picked = AutoContext.pick(bigger, 32 * gb)
        assertTrue(ModelMemory.estimate(bigger, picked).totalBytes <= AutoContext.CAP_BYTES)
        val next = com.bizzeh.bruce.settings.InferenceDefaults.CONTEXT_CHOICES.first { it > picked }
        assertTrue(ModelMemory.estimate(bigger, next).totalBytes > AutoContext.CAP_BYTES, "the next size up would pass 6 GB")
    }

    @Test
    fun aModelOverSixGigabytesOnItsOwnGetsSixteenKAtMost() {
        val large = model.copy(fileSizeBytes = 7 * gb)
        assertEquals(16384, AutoContext.pick(large, 32 * gb))
        assertEquals(2048, AutoContext.pick(large, 9 * gb), "a phone it does not fit gets the smallest size")
    }

    @Test
    fun theWizardOffersEverySizeThatFitsUpToTheTrainedMaximum() {
        val offered = AutoContext.options(model, 2 * gb).map { it.contextLength }
        assertEquals(listOf(2048, 4096, 8192, 16384, 24576, 32768), offered.take(6))
        assertTrue(AutoContext.options(model, 2 * gb).all { it.totalBytes <= 2 * gb })
        assertEquals(listOf(2048, 4096, 8192), AutoContext.options(model.copy(contextLength = 8192), 16 * gb).map { it.contextLength })
        assertEquals(emptyList<ContextOption>(), AutoContext.options(model.copy(blockCount = null), 16 * gb))
        assertEquals(emptyList<ContextOption>(), AutoContext.options(null, 16 * gb))
    }
}
