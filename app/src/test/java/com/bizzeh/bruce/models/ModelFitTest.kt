package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.hardware.CpuFeatures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ModelFitTest {
    private val dotProd = CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false)
    private val baseline = CpuFeatures(arm64 = true, neon = true, fp16 = false, dotProd = false, i8mm = false)
    private val xperia1Ii = DeviceProfile(usableMemoryBytes = 3_500_000_000, cpu = dotProd)
    private val xzPremium = DeviceProfile(usableMemoryBytes = 1_600_000_000, cpu = baseline)

    private fun qwen(sizeBytes: Long, params: Long, path: String) = Candidate(
        repositoryId = "ggml-org/Qwen3-GGUF",
        path = path,
        sizeBytes = sizeBytes,
        gated = false,
        architecture = "qwen3",
        parameterCount = params,
        header = GgufMetadata(
            version = 3, architecture = "qwen3", name = null, parameterCount = params, tensorCount = 0,
            contextLength = 40_960, fileType = null, quantisation = null, fileSizeBytes = sizeBytes,
            blockCount = 28, embeddingLength = 1024, headCount = 16, headCountKv = 8, keyLength = 128, valueLength = 128,
        ),
    )

    private val qwen06 = qwen(428_970_080, 596_000_000, "Qwen3-0.6B-Q4_0.gguf")
    private val qwen17 = qwen(1_282_439_264, 1_720_000_000, "Qwen3-1.7B-Q4_K_M.gguf")

    @Test
    fun speedsMatchWhatTask012Measured() {
        // Measured: 59.1 and 20.2 tok/s on the Xperia 1 II; 14.0 and 5.1 tok/s on the XZ Premium.
        assertEquals(55.9, ModelFit.assess(qwen06, xperia1Ii, 2048).expectedTokensPerSecond, 0.1)
        assertEquals(18.7, ModelFit.assess(qwen17, xperia1Ii, 2048).expectedTokensPerSecond, 0.1)
        assertEquals(13.5, ModelFit.assess(qwen06, xzPremium, 2048).expectedTokensPerSecond, 0.1)
        assertEquals(4.5, ModelFit.assess(qwen17, xzPremium, 2048).expectedTokensPerSecond, 0.1)
    }

    @Test
    fun speedBands() {
        assertEquals(SpeedBand.FAST, ModelFit.assess(qwen06, xperia1Ii, 2048).speed)
        assertEquals(SpeedBand.USABLE, ModelFit.assess(qwen06, xzPremium, 2048).speed)
        assertEquals(SpeedBand.SLOW, ModelFit.assess(qwen17, xzPremium, 2048).speed)
    }

    @Test
    fun fitUsesHeaderEstimateIncludingKvCache() {
        val assessment = ModelFit.assess(qwen06, xperia1Ii, 2048)

        assertTrue(assessment.estimate.complete)
        assertEquals(qwen06.sizeBytes + 28L * 2048 * 8 * 256 * 2, assessment.estimate.totalBytes)
        assertEquals(Fit.FITS, assessment.fit)
    }

    @Test
    fun fitBoundaries() {
        val weightsOnly = qwen06.copy(header = null)
        fun fitWith(usable: Long) = ModelFit.assess(weightsOnly, DeviceProfile(usable, dotProd), 2048).fit

        assertEquals(Fit.FITS, fitWith((weightsOnly.sizeBytes / ModelFit.TIGHT_SHARE).toLong() + 1))
        assertEquals(Fit.TIGHT, fitWith(weightsOnly.sizeBytes))
        assertEquals(Fit.DOES_NOT_FIT, fitWith(weightsOnly.sizeBytes - 1))
    }

    @Test
    fun withoutHeaderTheEstimateIsWeightsOnlyAndSearchDetailsAreUsed() {
        val assessment = ModelFit.assess(qwen17.copy(header = null), xperia1Ii, 2048)

        assertFalse(assessment.estimate.complete)
        assertEquals(qwen17.sizeBytes, assessment.estimate.totalBytes)
        assertTrue(assessment.supported)
        assertEquals("Q4_K_M", assessment.quantisation)
    }

    @Test
    fun unsupportedArchitectures() {
        assertFalse(ModelFit.assess(qwen06.copy(header = null, architecture = "clip"), xperia1Ii, 2048).supported)
        assertFalse(ModelFit.assess(qwen06.copy(header = null, architecture = null), xperia1Ii, 2048).supported)
        assertFalse(ModelFit.assess(qwen06.copy(header = null, architecture = "made-up"), xperia1Ii, 2048).supported)
    }

    @Test
    fun headerQuantisationWinsOverFileName() {
        val header = qwen06.header!!.copy(quantisation = "Q8_0")

        assertEquals("Q8_0", ModelFit.assess(qwen06.copy(header = header), xperia1Ii, 2048).quantisation)
    }

    @ParameterizedTest
    @CsvSource(
        "Qwen3-1.7B-Q4_K_M.gguf, Q4_K_M",
        "qwen3-4b-q4_k_m.gguf, Q4_K_M",
        "Qwen3-0.6B-Q4_0.gguf, Q4_0",
        "model.Q8_0.gguf, Q8_0",
        "sub/Model-IQ4_XS.gguf, IQ4_XS",
        "Model-BF16.gguf, BF16",
        "Model-f16.gguf, F16",
        "gemma-Q4_K.gguf, Q4_K",
    )
    fun quantisationFromFileName(path: String, expected: String) {
        assertEquals(expected, ModelFit.quantisationFromName(path))
    }

    @Test
    fun noQuantisationInName() {
        assertNull(ModelFit.quantisationFromName("model.gguf"))
        assertNull(ModelFit.quantisationFromName("stories260K.gguf"))
    }

    @Test
    fun quantisedFilesRankAheadOfFullPrecision() {
        val q8 = qwen(639_446_688, 596_000_000, "Qwen3-0.6B-Q8_0.gguf").copy(header = null)
        val bf16 = qwen(1_509_347_552, 596_000_000, "Qwen3-0.6B-BF16.gguf").copy(header = null)
        val q4 = qwen(428_970_080, 596_000_000, "Qwen3-0.6B-Q4_0.gguf").copy(header = null)

        val ranked = ModelFit.rank(listOf(bf16, q4, q8), xperia1Ii, 2048).map { it.candidate.path }

        assertEquals(listOf("Qwen3-0.6B-Q8_0.gguf", "Qwen3-0.6B-Q4_0.gguf", "Qwen3-0.6B-BF16.gguf"), ranked)
    }

    @Test
    fun rankingPutsRunnableModelsFirstThenGatedThenOnesThatDoNotFit() {
        val gated = qwen06.copy(repositoryId = "meta/gated", gated = true)
        val projector = qwen06.copy(header = null, architecture = "clip", path = "mmproj-F16.gguf")
        val tooBig = qwen(9_000_000_000, 14_000_000_000, "Qwen3-14B-Q4_K_M.gguf")
        val q8 = qwen(639_446_688, 596_000_000, "Qwen3-0.6B-Q8_0.gguf")

        val ranked = ModelFit.rank(listOf(projector, tooBig, qwen06, gated, qwen17, q8), xperia1Ii, 2048)

        assertEquals(
            listOf("Qwen3-1.7B-Q4_K_M.gguf", "Qwen3-0.6B-Q8_0.gguf", "Qwen3-0.6B-Q4_0.gguf", "Qwen3-0.6B-Q4_0.gguf", "Qwen3-14B-Q4_K_M.gguf", "mmproj-F16.gguf"),
            ranked.map { it.candidate.path },
        )
        assertEquals("meta/gated", ranked[3].candidate.repositoryId)
    }
}
