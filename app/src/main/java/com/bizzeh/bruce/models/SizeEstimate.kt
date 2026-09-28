package com.bizzeh.bruce.models

/**
 * Estimates a GGUF file's size from the model's parameter count and the quantisation in its
 * file name, for lists where the exact size would need one request per repository
 * (.learnings/hf-hub-api-for-model-discovery.md). The exact size comes from the repository's
 * file list once it is opened.
 */
object SizeEstimate {
    /**
     * Bits per weight. K-quant and legacy types are from llama.cpp's quantize tool (sizes for
     * Llama-3-8B, tools/quantize/quantize.cpp at the pinned revision); the others are the bpw it
     * states.
     */
    private val BITS_PER_WEIGHT = mapOf(
        "Q1_0" to 1.125, "Q2_0" to 2.25, "IQ1_S" to 1.56, "IQ1_M" to 1.75, "TQ1_0" to 1.69, "TQ2_0" to 2.06,
        "IQ2_XXS" to 2.06, "IQ2_XS" to 2.31, "IQ2_S" to 2.5, "IQ2_M" to 2.7,
        "Q2_K" to 3.17, "Q2_K_S" to 3.17, "IQ3_XXS" to 3.06, "IQ3_XS" to 3.3, "IQ3_S" to 3.44, "IQ3_M" to 3.66,
        "Q3_K_S" to 3.65, "Q3_K_M" to 4.0, "Q3_K" to 4.0, "Q3_K_L" to 4.31,
        "IQ4_XS" to 4.25, "IQ4_NL" to 4.5, "Q4_0" to 4.64, "Q4_1" to 5.11, "Q4_K_S" to 4.67, "Q4_K_M" to 4.9, "Q4_K" to 4.9,
        "Q5_0" to 5.57, "Q5_1" to 6.04, "Q5_K_S" to 5.57, "Q5_K_M" to 5.7, "Q5_K" to 5.7, "Q6_K" to 6.57, "Q8_0" to 8.52,
        "F16" to 16.0, "BF16" to 16.0, "F32" to 32.0,
    )

    /**
     * Real files are larger than parameters × bpw because embedding and output tensors keep
     * higher precision. On seven 0.8–4.7B files (TASK-033) the bare estimate was 0–10% low;
     * with this margin all seven are within 6% (5.6% low to 5% high).
     */
    private const val MARGIN = 1.05

    private val SPLIT_PART = Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE)

    fun bytes(parameterCount: Long, path: String): Long? {
        val bits = ModelFit.quantisationFromName(path)?.let(BITS_PER_WEIGHT::get) ?: return null
        return (parameterCount * bits / 8 * MARGIN).toLong()
    }

    /** Files a user can download as a whole model: not vision projectors, not parts of a split model. */
    fun isWholeModel(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return !name.contains("mmproj", ignoreCase = true) && !SPLIT_PART.containsMatchIn(name)
    }
}
