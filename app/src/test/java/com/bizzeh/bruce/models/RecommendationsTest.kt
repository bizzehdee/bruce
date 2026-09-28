package com.bizzeh.bruce.models

import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.huggingface.HubModel
import com.bizzeh.bruce.huggingface.HubSearchFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationsTest {
    private val cpu = CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false)
    private val gb = 1_000_000_000L

    private fun model(id: String, parameters: Long?, vararg files: String, architecture: String? = "qwen3") =
        HubModel(id, 0, false, null, architecture, parameters, null, files.toList())

    @Test
    fun sizeIsParametersTimesBitsPerWeightWithMargin() {
        assertEquals((2_000_000_000 * 4.9 / 8 * 1.05).toLong(), SizeEstimate.bytes(2_000_000_000, "dir/Model-Q4_K_M.gguf"))
        assertEquals((1_000_000_000 * 16.0 / 8 * 1.05).toLong(), SizeEstimate.bytes(1_000_000_000, "model.BF16.gguf"))
        assertNull(SizeEstimate.bytes(1_000_000_000, "model.gguf"), "no quantisation in the name")
    }

    /** Real files from TASK-033: parameters from the Hub, size on disk. */
    @Test
    fun estimatesAreWithinSixPercentOfRealFiles() {
        listOf(
            Triple(770_000_000L, "Qwen3.5-0.8B-Q8_0.gguf", 833_592_096L),
            Triple(1_880_000_000L, "Qwen3.5-2B-Q4_K_M.gguf", 1_280_835_840L),
            Triple(3_660_000_000L, "granite-4.2-3b-Q4_K_M.gguf", 2_244_011_552L),
        ).forEach { (parameters, path, size) ->
            val estimate = SizeEstimate.bytes(parameters, path)!!
            assertTrue(kotlin.math.abs(estimate - size) <= size * 0.06, "$path: $estimate vs $size")
        }
    }

    @Test
    fun projectorsAndSplitPartsAreNotWholeModels() {
        assertFalse(SizeEstimate.isWholeModel("mmproj-model-f16.gguf"))
        assertFalse(SizeEstimate.isWholeModel("big/Model-Q4_K_M-00001-of-00003.gguf"))
        assertTrue(SizeEstimate.isWholeModel("Model-Q4_K_M.gguf"))
    }

    @Test
    fun hubFilterCombinesBucketWithWhatCouldFitThisPhone() {
        val phone = DeviceProfile(2 * gb, cpu)
        val deviceMax = 2 * gb * 8 / 2

        assertEquals(HubSearchFilter(maxParameters = deviceMax), Recommendations.hubFilter(BrowseFilters(), phone))
        assertEquals(
            HubSearchFilter(minParameters = gb, maxParameters = 3 * gb - 1),
            Recommendations.hubFilter(BrowseFilters(parameters = ParameterBucket.FROM_1B_TO_3B), phone),
        )
        assertEquals(
            HubSearchFilter(minParameters = 14 * gb, maxParameters = deviceMax),
            Recommendations.hubFilter(BrowseFilters(parameters = ParameterBucket.FROM_14B), phone),
        )
        assertEquals(
            HubSearchFilter(textGeneration = false),
            Recommendations.hubFilter(BrowseFilters(runs = RunsFilter.ANY, textGeneration = false), phone),
        )
    }

    @Test
    fun bestFileNeedsParametersAndAnEstimableWholeModelFile() {
        val phone = DeviceProfile(8 * gb, cpu)
        assertNull(Recommendations.bestFile(model("a/b", null, "m-Q4_0.gguf"), phone, 4096))
        assertNull(Recommendations.bestFile(model("a/b", gb, "m.gguf", "mmproj-Q8_0.gguf"), phone, 4096))
        assertEquals("m-Q8_0.gguf", Recommendations.bestFile(model("a/b", gb, "m-Q4_0.gguf", "m-Q8_0.gguf", "m-F16.gguf"), phone, 4096)?.candidate?.path)
    }

    @Test
    fun runsFilterSeparatesComfortableTightAndUnsupported() {
        // 1B at Q8_0 is about 1.12 GB: comfortable on 4 GB, tight on 1.3 GB.
        val fits = model("a/fits", gb, "m-Q8_0.gguf")
        val unsupported = model("b/clip", gb, "m-Q8_0.gguf", architecture = "clip")
        val results = listOf(fits, unsupported)
        fun ids(filter: RunsFilter, memory: Long) =
            Recommendations.listings(results, BrowseFilters(runs = filter), DeviceProfile(memory, cpu), 4096, recommended = true).map { it.model.id }

        assertEquals(listOf("a/fits"), ids(RunsFilter.FITS, 4 * gb))
        assertEquals(emptyList<String>(), ids(RunsFilter.FITS, 1_300_000_000))
        assertEquals(listOf("a/fits"), ids(RunsFilter.FITS_OR_TIGHT, 1_300_000_000))
        assertEquals(listOf("a/fits", "b/clip"), ids(RunsFilter.ANY, 1_300_000_000))
    }

    @Test
    fun sizeBucketsAreInclusiveAndNeedAnEstimate() {
        val phone = DeviceProfile(64 * gb, cpu)
        val results = listOf(model("a/small", gb, "m-Q4_0.gguf"), model("b/none", null, "m-Q4_0.gguf"), model("c/huge", 20 * gb, "m-Q4_0.gguf"))
        fun ids(size: SizeBucket) =
            Recommendations.listings(results, BrowseFilters(size = size, runs = RunsFilter.ANY), phone, 4096, recommended = false).map { it.model.id }

        assertEquals(listOf("a/small"), ids(SizeBucket.UNDER_1GB))
        assertEquals(listOf("c/huge"), ids(SizeBucket.FROM_8GB))
        assertEquals(emptyList<String>(), ids(SizeBucket.FROM_2GB_TO_4GB))
    }
}
