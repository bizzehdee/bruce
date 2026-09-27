package com.bizzeh.bruce.inference

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LlamaCppEngineTest {
    private companion object {
        const val VULKAN_1_2 = (1 shl 22) or (2 shl 12)
    }

    @TempDir
    lateinit var dir: File

    private val llama = FakeLlamaApi()
    private val dispatcher = StandardTestDispatcher()
    private val engine = LlamaCppEngine(llama, dispatcher)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun ggufFile(name: String = "model.gguf") =
        File(dir, name).apply { writeBytes("GGUF".toByteArray() + ByteArray(16)) }

    @Test
    fun loadsModelAndReportsInfo() = test {
        val result = engine.loadModel(ggufFile(), LoadConfig(contextLength = 512, threads = 2, batchSize = 64))

        val expected = ModelInfo("fake model 10", 260_000L, 1_000_000L, 512)
        assertEquals(LoadResult.Loaded(expected), result)
        assertEquals(expected, engine.getModelInfo())
        assertEquals(Triple(512, 2, 64), llama.lastContextRequest)
    }

    @Test
    fun missingFileFailsWithoutNativeCall() = test {
        val result = engine.loadModel(File(dir, "absent.gguf"))

        assertEquals(LoadResult.Failed(LoadError.FILE_NOT_FOUND), result)
        assertTrue(llama.loadedPaths.isEmpty())
    }

    @Test
    fun directoryIsNotAModelFile() = test {
        assertEquals(LoadResult.Failed(LoadError.FILE_NOT_FOUND), engine.loadModel(dir))
    }

    @Test
    fun nonGgufFileFailsWithoutNativeCall() = test {
        val file = File(dir, "notes.gguf").apply { writeText("hello world") }

        assertEquals(LoadResult.Failed(LoadError.NOT_GGUF), engine.loadModel(file))
        assertTrue(llama.loadedPaths.isEmpty())
    }

    @Test
    fun nativeLoadFailureIsReported() = test {
        llama.nextModelHandle = 0L

        assertEquals(LoadResult.Failed(LoadError.MODEL_LOAD_FAILED), engine.loadModel(ggufFile()))
        assertNull(engine.getModelInfo())
    }

    @Test
    fun contextFailureFreesTheModel() = test {
        llama.nextContextHandle = 0L

        assertEquals(LoadResult.Failed(LoadError.CONTEXT_CREATION_FAILED), engine.loadModel(ggufFile()))
        assertEquals(listOf(10L), llama.freedModels)
        assertNull(engine.getModelInfo())
    }

    @Test
    fun loadingAnotherModelReleasesThePreviousOne() = test {
        engine.loadModel(ggufFile("a.gguf"))
        llama.nextModelHandle = 11L
        llama.nextContextHandle = 21L

        engine.loadModel(ggufFile("b.gguf"))

        assertEquals(listOf(10L), llama.freedModels)
        assertEquals(listOf(20L), llama.freedContexts)
        assertEquals("fake model 11", engine.getModelInfo()?.description)
    }

    @Test
    fun unloadReleasesContextThenModelOnce() = test {
        engine.loadModel(ggufFile())

        engine.unloadModel()
        engine.unloadModel()

        assertEquals(listOf(20L), llama.freedContexts)
        assertEquals(listOf(10L), llama.freedModels)
        assertNull(engine.getModelInfo())
    }

    @Test
    fun capabilitiesDescribeEachDevice() {
        llama.devices = listOf(
            FakeLlamaApi.Device("CPU", "CPU", "Kryo", 0, 8L shl 30),
            FakeLlamaApi.Device("Vulkan", "Vulkan0", "Adreno 650", 1, 4L shl 30),
            FakeLlamaApi.Device("OpenCL", "GPUOpenCL", "QUALCOMM Adreno", 2, 2L shl 30),
            FakeLlamaApi.Device("BLAS", "BLAS", "accelerate", 3, 0),
            FakeLlamaApi.Device("Meta", "meta", "tensor parallel", 4, 0),
        )
        llama.cpuFeatures = arrayOf("NEON=1", "DOTPROD=1")
        llama.vulkanApiVersions = mapOf("Adreno 650" to VULKAN_1_2)

        val capabilities = engine.getCapabilities()

        assertEquals(
            listOf(
                ComputeDevice(Backend.CPU, "CPU", "Kryo", DeviceType.CPU, 8L shl 30, usable = true),
                ComputeDevice(Backend.VULKAN, "Vulkan0", "Adreno 650", DeviceType.GPU, 4L shl 30, usable = true),
                ComputeDevice(Backend.OPENCL, "GPUOpenCL", "QUALCOMM Adreno", DeviceType.INTEGRATED_GPU, 2L shl 30, usable = true),
                ComputeDevice(Backend.OTHER, "BLAS", "accelerate", DeviceType.ACCELERATOR, 0, usable = true),
                ComputeDevice(Backend.OTHER, "meta", "tensor parallel", DeviceType.OTHER, 0, usable = true),
            ),
            capabilities.devices,
        )
        assertEquals(setOf(Backend.CPU, Backend.VULKAN, Backend.OPENCL, Backend.OTHER), capabilities.usableBackends)
        assertEquals(listOf("NEON=1", "DOTPROD=1"), capabilities.cpuBackendFeatures)
    }

    @Test
    fun vulkanDeviceBelowVersion1Point2IsUnusable() {
        llama.devices = listOf(
            FakeLlamaApi.Device("CPU", "CPU", "Kryo", 0, 0),
            FakeLlamaApi.Device("Vulkan", "Vulkan0", "Adreno 540", 2, 0),
            FakeLlamaApi.Device("Vulkan", "Vulkan1", "Unknown GPU", 1, 0),
        )
        llama.vulkanApiVersions = mapOf("Adreno 540" to VULKAN_1_2 - 1)

        val capabilities = engine.getCapabilities()

        assertEquals(listOf(true, false, false), capabilities.devices.map { it.usable })
        assertEquals(setOf(Backend.CPU), capabilities.usableBackends)
    }

    @Test
    fun loadConfigRejectsNonPositiveValues() {
        assertThrows<IllegalArgumentException> { LoadConfig(contextLength = 0) }
        assertThrows<IllegalArgumentException> { LoadConfig(threads = 0) }
        assertThrows<IllegalArgumentException> { LoadConfig(batchSize = 0) }
    }
}
