package com.bizzeh.bruce.hardware

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CpuFeaturesTest {
    @Test
    fun noBitsMeansNoFeatures() {
        assertEquals(CpuFeatures(false, false, false, false, false), CpuFeatures.fromBits(0))
    }

    @Test
    fun eachBitMapsToItsFeature() {
        assertEquals(CpuFeatures(true, false, false, false, false), CpuFeatures.fromBits(0b00001))
        assertEquals(CpuFeatures(false, true, false, false, false), CpuFeatures.fromBits(0b00010))
        assertEquals(CpuFeatures(false, false, true, false, false), CpuFeatures.fromBits(0b00100))
        assertEquals(CpuFeatures(false, false, false, true, false), CpuFeatures.fromBits(0b01000))
        assertEquals(CpuFeatures(false, false, false, false, true), CpuFeatures.fromBits(0b10000))
    }
}
