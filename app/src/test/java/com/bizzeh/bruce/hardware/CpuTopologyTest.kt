package com.bizzeh.bruce.hardware

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CpuTopologyTest {
    @TempDir
    lateinit var root: File

    @Test
    fun xperia1IiTriClusterHasFourPerformanceCores() {
        val frequencies = listOf(1804800L, 1804800, 1804800, 1804800, 2419200, 2419200, 2419200, 2841600)
        assertEquals(4, CpuTopology.performanceCores(frequencies))
    }

    @Test
    fun xzPremiumTwoClustersHasFourPerformanceCores() {
        val frequencies = listOf(1900800L, 1900800, 1900800, 1900800, 2457600, 2457600, 2457600, 2457600)
        assertEquals(4, CpuTopology.performanceCores(frequencies))
    }

    @Test
    fun uniformCoresAreAllPerformanceCores() {
        assertEquals(4, CpuTopology.performanceCores(listOf(2_000_000L, 2_000_000, 2_000_000, 2_000_000)))
    }

    @Test
    fun unknownFrequencyGivesNoAnswer() {
        assertNull(CpuTopology.performanceCores(listOf(2_000_000L, null)))
        assertNull(CpuTopology.performanceCores(emptyList()))
    }

    private fun core(index: Int, maxFrequency: String?) {
        val dir = File(root, "cpu$index/cpufreq").apply { mkdirs() }
        maxFrequency?.let { File(dir, "cpuinfo_max_freq").writeText(it) }
    }

    @Test
    fun readsFrequenciesFromSysfsLayout() {
        listOf("1800000\n", "1800000\n", "2400000\n").forEachIndexed(::core)
        File(root, "cpufreq").mkdirs()
        File(root, "cpuidle").mkdirs()

        assertEquals(1, CpuTopology.performanceCoreCount(root, availableProcessors = 3))
    }

    @Test
    fun fallsBackToAllCoresWhenAFrequencyIsMissing() {
        core(0, "1800000")
        core(1, null)

        assertEquals(8, CpuTopology.performanceCoreCount(root, availableProcessors = 8))
    }

    @Test
    fun fallsBackToAllCoresWhenAFrequencyIsGarbage() {
        core(0, "fast")

        assertEquals(6, CpuTopology.performanceCoreCount(root, availableProcessors = 6))
    }
}
