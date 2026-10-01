package com.bizzeh.bruce.models

import androidx.annotation.StringRes
import com.bizzeh.bruce.R

/**
 * A kind of phone the model evaluation measured (TASK-105, TASK-106): its memory, and whether its
 * CPU has the dot-product instructions llama.cpp's fast paths need. The memory bounds sit a little
 * under the nominal size, as the kernel reports less than the box says (7.8 GB for an 8 GB phone).
 */
enum class DeviceTier(val minTotalMemoryBytes: Long, val needsDotProd: Boolean) {
    FOUR_GB(3_500_000_000, needsDotProd = false),
    EIGHT_GB(7_000_000_000, needsDotProd = true),
    TWELVE_GB(11_000_000_000, needsDotProd = true),
}

/** A model the evaluation found good on [tiers], with the file measured there. */
data class SuggestedModel(
    val repositoryId: String,
    val path: String,
    val sizeBytes: Long,
    val name: String,
    @StringRes val description: Int,
    val tiers: Set<DeviceTier>,
)

/** The setup wizard's suggested first models (TASK-107). Shipped with the app, so choosing one asks Hugging Face for nothing until it is downloaded. */
object SuggestedModels {
    val all = listOf(
        SuggestedModel(
            "MaziyarPanahi/Qwen3-1.7B-GGUF", "Qwen3-1.7B.Q5_K_M.gguf", 1_471_805_568, "Qwen3 1.7B",
            R.string.suggested_qwen3_1_7b, setOf(DeviceTier.EIGHT_GB, DeviceTier.TWELVE_GB),
        ),
        SuggestedModel(
            "openbmb/MiniCPM5-2B-GGUF", "MiniCPM5-2B-Q4_K_M.gguf", 1_561_318_368, "MiniCPM5 2B",
            R.string.suggested_minicpm5_2b, setOf(DeviceTier.EIGHT_GB, DeviceTier.TWELVE_GB),
        ),
        SuggestedModel(
            "LiquidAI/LFM2.5-2.6B-GGUF", "LFM2.5-2.6B-QAD-Q4_0.gguf", 1_593_894_944, "LFM2.5 2.6B",
            R.string.suggested_lfm2_5_2_6b, setOf(DeviceTier.TWELVE_GB),
        ),
        SuggestedModel(
            "unsloth/gemma-4-E2B-it-GGUF", "gemma-4-E2B-it-Q3_K_M.gguf", 2_536_786_016, "Gemma 4 E2B",
            R.string.suggested_gemma4_e2b, setOf(DeviceTier.EIGHT_GB),
        ),
    )

    /** The tier whose results apply: the largest with no more memory than this phone and no CPU feature it lacks; null below every tier. */
    fun tier(device: DeviceProfile): DeviceTier? = DeviceTier.entries
        .filter { device.totalMemoryBytes >= it.minTotalMemoryBytes && (!it.needsDotProd || device.cpu.dotProd) }
        .maxByOrNull { it.minTotalMemoryBytes }

    fun forDevice(device: DeviceProfile, models: List<SuggestedModel> = all): List<SuggestedModel> =
        tier(device)?.let { tier -> models.filter { tier in it.tiers } }.orEmpty()
}
