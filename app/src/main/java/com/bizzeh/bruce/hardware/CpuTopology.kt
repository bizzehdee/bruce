package com.bizzeh.bruce.hardware

import kotlin.math.min

object CpuTopology {
    const val MAX_DEFAULT_THREADS = 4

    /**
     * Threads used unless the user chooses a number: four, or every core on a phone with fewer
     * (owner, 2026-10-01). Four was at or near the fastest on every test phone; more threads were
     * often slower, by up to three quarters (.learnings/phone-speed-measurement.md).
     */
    fun defaultThreads(availableProcessors: Int = Runtime.getRuntime().availableProcessors()): Int =
        min(MAX_DEFAULT_THREADS, availableProcessors.coerceAtLeast(1))
}
