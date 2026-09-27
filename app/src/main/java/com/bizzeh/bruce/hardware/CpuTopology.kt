package com.bizzeh.bruce.hardware

import java.io.File

object CpuTopology {
    private val CPU_DIR = Regex("cpu\\d+")

    /**
     * Cores outside the lowest-frequency cluster. On both test phones these 4 cores gave the
     * fastest generation; using every core was 25–35% slower (.learnings/phase0-cpu-benchmarks.md).
     * Falls back to every core when frequencies cannot be read.
     */
    fun performanceCoreCount(
        cpuRoot: File = File("/sys/devices/system/cpu"),
        availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    ): Int {
        val frequencies = cpuRoot.listFiles { file -> file.name.matches(CPU_DIR) }.orEmpty()
            .map { File(it, "cpufreq/cpuinfo_max_freq") }
            .map { file -> if (file.canRead()) file.readText().trim().toLongOrNull() else null }
        return performanceCores(frequencies) ?: availableProcessors
    }

    /** Null when any frequency is unknown. */
    internal fun performanceCores(maxFrequencies: List<Long?>): Int? {
        if (maxFrequencies.isEmpty() || maxFrequencies.any { it == null }) return null
        val known = maxFrequencies.filterNotNull()
        val lowest = known.min()
        val fast = known.count { it > lowest }
        return if (fast == 0) known.size else fast
    }
}
