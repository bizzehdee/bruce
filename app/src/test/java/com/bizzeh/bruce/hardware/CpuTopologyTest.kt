package com.bizzeh.bruce.hardware

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CpuTopologyTest {
    @Test
    fun fourThreadsOnPhonesWithFourOrMoreCores() {
        assertEquals(4, CpuTopology.defaultThreads(availableProcessors = 8))
        assertEquals(4, CpuTopology.defaultThreads(availableProcessors = 7))
        assertEquals(4, CpuTopology.defaultThreads(availableProcessors = 4))
    }

    @Test
    fun everyCoreOnPhonesWithFewer() {
        assertEquals(2, CpuTopology.defaultThreads(availableProcessors = 2))
        assertEquals(1, CpuTopology.defaultThreads(availableProcessors = 0))
    }
}
