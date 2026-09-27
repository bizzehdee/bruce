package com.bizzeh.bruce.hardware

data class CpuFeatures(
    val arm64: Boolean,
    val neon: Boolean,
    val fp16: Boolean,
    val dotProd: Boolean,
    val i8mm: Boolean,
) {
    companion object {
        // Bit layout shared with cpu_features.h.
        private const val ARM64 = 1 shl 0
        private const val NEON = 1 shl 1
        private const val FP16 = 1 shl 2
        private const val DOT_PROD = 1 shl 3
        private const val I8MM = 1 shl 4

        internal fun fromBits(bits: Int) = CpuFeatures(
            arm64 = bits and ARM64 != 0,
            neon = bits and NEON != 0,
            fp16 = bits and FP16 != 0,
            dotProd = bits and DOT_PROD != 0,
            i8mm = bits and I8MM != 0,
        )

        /** Reads the running CPU's features from the kernel's hardware capability flags. */
        fun detect(): CpuFeatures = fromBits(CpuFeaturesNative.detect())
    }
}

internal object CpuFeaturesNative {
    init {
        System.loadLibrary("bruce")
    }

    external fun detect(): Int
}
