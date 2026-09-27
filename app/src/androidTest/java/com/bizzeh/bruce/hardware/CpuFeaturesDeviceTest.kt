package com.bizzeh.bruce.hardware

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CpuFeaturesDeviceTest {
    /** /proc/cpuinfo is an independent view of the same kernel hwcaps. */
    private val cpuInfoFlags: Set<String> = File("/proc/cpuinfo").readLines()
        .first { it.startsWith("Features") }
        .substringAfter(':')
        .trim()
        .split(Regex("\\s+"))
        .toSet()

    @Test
    fun detectionMatchesProcCpuInfo() {
        val features = CpuFeatures.detect()

        assertTrue(features.arm64)
        assertEquals("asimd" in cpuInfoFlags, features.neon)
        assertEquals("asimdhp" in cpuInfoFlags, features.fp16)
        assertEquals("asimddp" in cpuInfoFlags, features.dotProd)
        assertEquals("i8mm" in cpuInfoFlags, features.i8mm)
    }
}
