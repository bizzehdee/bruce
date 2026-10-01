package com.bizzeh.bruce.models

import com.bizzeh.bruce.R
import com.bizzeh.bruce.hardware.CpuFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedModelsTest {
    private fun phone(totalBytes: Long, dotProd: Boolean = true) =
        DeviceProfile(usableMemoryBytes = totalBytes / 2, cpu = CpuFeatures(arm64 = true, neon = true, fp16 = dotProd, dotProd = dotProd, i8mm = false), totalMemoryBytes = totalBytes)

    @Test
    fun theTestPhonesFallInTheirOwnTiers() {
        // MemTotal as the kernel reports it on the Xperia XZ Premium, Xperia 1 II and Pixel 11.
        assertEquals(DeviceTier.FOUR_GB, SuggestedModels.tier(phone(3_906_961_408, dotProd = false)))
        assertEquals(DeviceTier.EIGHT_GB, SuggestedModels.tier(phone(8_037_171_200)))
        assertEquals(DeviceTier.TWELVE_GB, SuggestedModels.tier(phone(12_212_867_072)))
    }

    @Test
    fun aPhoneBetweenTiersUsesTheLargestItCovers() {
        assertEquals(DeviceTier.TWELVE_GB, SuggestedModels.tier(phone(16_000_000_000)))
        assertEquals(DeviceTier.FOUR_GB, SuggestedModels.tier(phone(5_800_000_000)))
    }

    @Test
    fun aPhoneWithoutDotProductInstructionsOnlyGetsTheTierMeasuredWithoutThem() {
        assertEquals(DeviceTier.FOUR_GB, SuggestedModels.tier(phone(12_000_000_000, dotProd = false)))
    }

    @Test
    fun aPhoneBelowEveryTierOrOfUnknownMemoryGetsNoSuggestions() {
        assertNull(SuggestedModels.tier(phone(3_000_000_000)))
        assertNull(SuggestedModels.tier(phone(0)))
        assertEquals(emptyList<SuggestedModel>(), SuggestedModels.forDevice(phone(3_000_000_000)))
    }

    @Test
    fun suggestionsAreThoseMeasuredOnThePhonesTier() {
        val small = SuggestedModel("a/small", "s.gguf", 1, "Small", R.string.suggested_qwen3_1_7b, setOf(DeviceTier.FOUR_GB, DeviceTier.EIGHT_GB))
        val large = SuggestedModel("a/large", "l.gguf", 1, "Large", R.string.suggested_qwen3_1_7b, setOf(DeviceTier.TWELVE_GB))

        assertEquals(listOf(small), SuggestedModels.forDevice(phone(8_037_171_200), listOf(small, large)))
        assertEquals(listOf(large), SuggestedModels.forDevice(phone(12_212_867_072), listOf(small, large)))
    }

    @Test
    fun everyShippedSuggestionNamesOneGgufFileInARepository() {
        SuggestedModels.all.forEach {
            assertTrue(it.repositoryId, com.bizzeh.bruce.huggingface.HubClient.isRepositoryId(it.repositoryId))
            assertTrue(it.path, it.path.endsWith(".gguf") && '/' !in it.path)
            assertTrue(it.name, it.tiers.isNotEmpty() && it.sizeBytes > 0)
        }
        assertEquals(SuggestedModels.all.size, SuggestedModels.all.map { it.path }.toSet().size)
    }
}
