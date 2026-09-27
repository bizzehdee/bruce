package com.bizzeh.bruce.prototype

import com.bizzeh.bruce.gguf.GgufError
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.ComputeDevice
import com.bizzeh.bruce.inference.DeviceType
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.LoadError
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.StopReason
import com.bizzeh.bruce.models.MemoryCheck
import com.bizzeh.bruce.models.MemoryEstimate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class PrototypeTextTest {
    @Test
    fun cpuFlags() {
        assertEquals("CPU: detecting…", PrototypeText.cpu(null))
        assertEquals(
            "CPU: ARM64 ✓ NEON ✓ FP16 ✗ DOTPROD ✓ I8MM ✗",
            PrototypeText.cpu(CpuFeatures(arm64 = true, neon = true, fp16 = false, dotProd = true, i8mm = false)),
        )
    }

    @Test
    fun devices() {
        val vulkan = ComputeDevice(1, Backend.VULKAN, "Vulkan0", "Adreno 540", DeviceType.INTEGRATED_GPU, 3L shl 30, usable = false)
        assertEquals("VULKAN: Adreno 540 (3.00 GB) — unusable", PrototypeText.device(vulkan))
        assertEquals("VULKAN: Adreno 540 (3.00 GB)", PrototypeText.device(vulkan.copy(usable = true)))
    }

    @Test
    fun metadata() {
        val metadata = GgufMetadata(3, "llama", "m", 292_800, 48, 2048, 15, "Q4_K_M", 1_185_376)
        assertEquals(
            listOf("Architecture: llama", "Parameters: 292.8K", "Quantisation: Q4_K_M", "Trained context: 2048", "File size: 1.1 MB"),
            PrototypeText.metadata(metadata, null),
        )
        assertEquals(
            listOf("Architecture: unknown", "Parameters: 7", "Quantisation: not declared", "Trained context: unknown", "File size: 7 B"),
            PrototypeText.metadata(GgufMetadata(3, null, null, 7, 1, null, null, null, 7), null),
        )
        assertEquals(listOf("Unreadable: NOT_GGUF"), PrototypeText.metadata(null, GgufError.NOT_GGUF))
        assertEquals(listOf("Unreadable: unknown"), PrototypeText.metadata(null, null))
    }

    @Test
    fun memory() {
        val estimate = MemoryEstimate(weightsBytes = 2L shl 30, kvCacheBytes = 256L shl 20, contextLength = 2048)
        assertEquals(
            "Memory: 2.25 GB (weights 2.00 GB, KV cache 256.0 MB at 2048 tokens); usable 3.00 GB",
            PrototypeText.memory(MemoryCheck(estimate, 3L shl 30)),
        )
        assertEquals(
            "Memory: 2.00 GB (weights 2.00 GB, KV cache unknown at 2048 tokens); usable 1.0 KB",
            PrototypeText.memory(MemoryCheck(estimate.copy(kvCacheBytes = null), 1024)),
        )
    }

    @Test
    fun loadStates() {
        val info = ModelInfo("m", 1, 1, 1)
        assertEquals("Not loaded", PrototypeText.load(LoadState.Unloaded))
        assertEquals("Loading…", PrototypeText.load(LoadState.Loading))
        assertEquals("Load failed: NOT_GGUF", PrototypeText.load(LoadState.Failed(LoadError.NOT_GGUF)))
        assertEquals("Loaded on CPU", PrototypeText.load(LoadState.Loaded(info, Backend.CPU, emptyList())))
        assertEquals(
            "Loaded on CPU after VULKAN, OPENCL failed",
            PrototypeText.load(LoadState.Loaded(info, Backend.CPU, listOf(Backend.VULKAN, Backend.OPENCL))),
        )
    }

    @Test
    fun generationResult() {
        assertNull(PrototypeText.result(PrototypeState()))
        assertEquals(
            "Generation failed: DECODE_FAILED",
            PrototypeText.result(PrototypeState(generationError = GenerationError.DECODE_FAILED)),
        )
        val stats = GenerationStats(10, 2.seconds, 30, 3.seconds)
        assertEquals(
            "MAX_TOKENS — prompt 10 tok at 5.0 tok/s, generated 30 tok at 10.0 tok/s",
            PrototypeText.result(PrototypeState(stats = stats, stopReason = StopReason.MAX_TOKENS)),
        )
    }

    @Test
    fun counts() {
        assertEquals("4.02B", PrototypeText.count(4_022_000_000))
        assertEquals("596.0M", PrototypeText.count(596_000_000))
    }
}
