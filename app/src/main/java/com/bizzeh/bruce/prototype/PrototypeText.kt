package com.bizzeh.bruce.prototype

import com.bizzeh.bruce.gguf.GgufError
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.ComputeDevice
import com.bizzeh.bruce.models.MemoryCheck
import java.util.Locale

/** Formats prototype state for display; kept out of composables so tests can cover it. */
internal object PrototypeText {
    fun cpu(features: CpuFeatures?): String {
        if (features == null) return "CPU: detecting…"
        val flags = listOf(
            "ARM64" to features.arm64,
            "NEON" to features.neon,
            "FP16" to features.fp16,
            "DOTPROD" to features.dotProd,
            "I8MM" to features.i8mm,
        )
        return "CPU: " + flags.joinToString(" ") { (name, present) -> if (present) "$name ✓" else "$name ✗" }
    }

    fun device(device: ComputeDevice): String =
        "${device.backend}: ${device.description} (${bytes(device.memoryBytes)})" +
            if (device.usable) "" else " — unusable"

    fun metadata(metadata: GgufMetadata?, error: GgufError?): List<String> {
        if (metadata == null) return listOf("Unreadable: ${error ?: "unknown"}")
        return listOf(
            "Architecture: ${metadata.architecture ?: "unknown"}",
            "Parameters: ${count(metadata.parameterCount)}",
            "Quantisation: ${metadata.quantisation ?: "not declared"}",
            "Trained context: ${metadata.contextLength ?: "unknown"}",
            "File size: ${bytes(metadata.fileSizeBytes)}",
        )
    }

    fun memory(check: MemoryCheck): String {
        val estimate = check.estimate
        val kv = estimate.kvCacheBytes?.let(::bytes) ?: "unknown"
        return "Memory: ${bytes(estimate.totalBytes)} (weights ${bytes(estimate.weightsBytes)}, " +
            "KV cache $kv at ${estimate.contextLength} tokens); usable ${bytes(check.usableBytes)}"
    }

    fun load(state: LoadState): String = when (state) {
        LoadState.Unloaded -> "Not loaded"
        LoadState.Loading -> "Loading…"
        is LoadState.Failed -> "Load failed: ${state.error}"
        is LoadState.Loaded ->
            "Loaded on ${state.backend}" +
                if (state.failedBackends.isEmpty()) "" else " after ${state.failedBackends.joinToString()} failed"
    }

    fun result(state: PrototypeState): String? {
        state.generationError?.let { return "Generation failed: $it" }
        val stats = state.stats ?: return null
        return String.format(
            Locale.ROOT,
            "%s — prompt %d tok at %.1f tok/s, generated %d tok at %.1f tok/s",
            state.stopReason,
            stats.promptTokens,
            stats.promptTokensPerSecond,
            stats.generatedTokens,
            stats.generationTokensPerSecond,
        )
    }

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

    private const val KIB = 1024L
    private const val MIB = KIB * 1024
    private const val GIB = MIB * 1024
}
