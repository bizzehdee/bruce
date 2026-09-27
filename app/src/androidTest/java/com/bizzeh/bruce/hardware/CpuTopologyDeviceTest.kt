package com.bizzeh.bruce.hardware

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CpuTopologyDeviceTest {
    @Test
    fun appCanReadCoreFrequencies() {
        val cores = Runtime.getRuntime().availableProcessors()
        val performance = CpuTopology.performanceCoreCount(availableProcessors = -1)

        assertTrue("sysfs frequencies unreadable on this phone", performance != -1)
        assertTrue(performance in 1..cores)
    }
}
