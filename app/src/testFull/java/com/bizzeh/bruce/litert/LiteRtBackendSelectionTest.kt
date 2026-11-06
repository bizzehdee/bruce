package com.bizzeh.bruce.litert

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiteRtBackendSelectionTest {
    @Test
    fun autoUsesOnlyCpu() {
        assertEquals(listOf(LiteRtBackendChoice.CPU), LiteRtBackendSelection.plan(LiteRtBackendPreference.AUTO))
    }

    @Test
    fun forcedCpuUsesOnlyCpu() {
        assertEquals(listOf(LiteRtBackendChoice.CPU), LiteRtBackendSelection.plan(LiteRtBackendPreference.CPU))
    }

    @Test
    fun gpuFallsBackToCpu() {
        assertEquals(
            listOf(LiteRtBackendChoice.GPU, LiteRtBackendChoice.CPU),
            LiteRtBackendSelection.plan(LiteRtBackendPreference.GPU),
        )
    }

    @Test
    fun npuFallsBackToCpu() {
        assertEquals(
            listOf(LiteRtBackendChoice.NPU, LiteRtBackendChoice.CPU),
            LiteRtBackendSelection.plan(LiteRtBackendPreference.NPU),
        )
    }
}
