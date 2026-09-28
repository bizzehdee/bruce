package com.bizzeh.bruce.ui

import java.util.Locale

/** Human-readable sizes and counts, shared by every screen. */
object Format {
    private const val KIB = 1024L
    private const val MIB = KIB * 1024
    private const val GIB = MIB * 1024

    fun bytes(value: Long): String = when {
        value >= GIB -> String.format(Locale.ROOT, "%.2f GB", value.toDouble() / GIB)
        value >= MIB -> String.format(Locale.ROOT, "%.1f MB", value.toDouble() / MIB)
        value >= KIB -> String.format(Locale.ROOT, "%.1f KB", value.toDouble() / KIB)
        else -> "$value B"
    }

    fun count(value: Long): String = when {
        value >= 1_000_000_000 -> String.format(Locale.ROOT, "%.2fB", value / 1e9)
        value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1e6)
        value >= 1_000 -> String.format(Locale.ROOT, "%.1fK", value / 1e3)
        else -> value.toString()
    }
}
