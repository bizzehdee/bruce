package com.bizzeh.bruce.gguf

/** Names for llama_ftype values, from enum llama_ftype in llama.h (llama.cpp v0.5.0). */
internal object LlamaFileTypes {
    private val NAMES = mapOf(
        0 to "F32", 1 to "F16", 2 to "Q4_0", 3 to "Q4_1", 7 to "Q8_0", 8 to "Q5_0", 9 to "Q5_1",
        10 to "Q2_K", 11 to "Q3_K_S", 12 to "Q3_K_M", 13 to "Q3_K_L", 14 to "Q4_K_S", 15 to "Q4_K_M",
        16 to "Q5_K_S", 17 to "Q5_K_M", 18 to "Q6_K", 19 to "IQ2_XXS", 20 to "IQ2_XS", 21 to "Q2_K_S",
        22 to "IQ3_XS", 23 to "IQ3_XXS", 24 to "IQ1_S", 25 to "IQ4_NL", 26 to "IQ3_S", 27 to "IQ3_M",
        28 to "IQ2_S", 29 to "IQ2_M", 30 to "IQ4_XS", 31 to "IQ1_M", 32 to "BF16", 36 to "TQ1_0",
        37 to "TQ2_0", 38 to "MXFP4_MOE", 39 to "NVFP4", 40 to "Q1_0", 41 to "Q2_0",
    )

    fun name(fileType: Int): String? = NAMES[fileType]

    val names: Collection<String> get() = NAMES.values
}
