package com.bizzeh.bruce.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackendSelectionTest {
    private val cpu = ComputeDevice(0, Backend.CPU, "CPU", "CPU", DeviceType.CPU, 0, usable = true)
    private val vulkan = ComputeDevice(1, Backend.VULKAN, "Vulkan0", "Adreno", DeviceType.INTEGRATED_GPU, 0, usable = true)
    private val oldVulkan = vulkan.copy(index = 2, name = "Vulkan1", usable = false)
    private val openCl = ComputeDevice(3, Backend.OPENCL, "GPUOpenCL", "Adreno", DeviceType.INTEGRATED_GPU, 0, usable = true)
    private val cpuAttempt = LoadAttempt(Backend.CPU, emptyList())

    private fun plan(preference: BackendPreference, vararg devices: ComputeDevice) =
        BackendSelection.plan(preference, EngineCapabilities(devices.toList(), emptyList()))

    @Test
    fun autoWithoutGpuUsesCpu() {
        assertEquals(BackendPlan.Attempts(listOf(cpuAttempt)), plan(BackendPreference.AUTO, cpu))
    }

    @Test
    fun autoUsesCpuEvenWithAUsableGpu() {
        assertEquals(BackendPlan.Attempts(listOf(cpuAttempt)), plan(BackendPreference.AUTO, cpu, openCl, vulkan))
    }

    @Test
    fun firstUsableDeviceOfABackendIsChosen() {
        assertEquals(
            BackendPlan.Attempts(listOf(LoadAttempt(Backend.VULKAN, listOf(vulkan)), cpuAttempt)),
            plan(BackendPreference.VULKAN, cpu, oldVulkan, vulkan),
        )
    }

    @Test
    fun forcedCpuUsesOnlyCpu() {
        assertEquals(BackendPlan.Attempts(listOf(cpuAttempt)), plan(BackendPreference.CPU, cpu, vulkan))
    }

    @Test
    fun forcedOpenClFallsBackToCpu() {
        assertEquals(
            BackendPlan.Attempts(listOf(LoadAttempt(Backend.OPENCL, listOf(openCl)), cpuAttempt)),
            plan(BackendPreference.OPENCL, cpu, vulkan, openCl),
        )
    }

    @Test
    fun forcedBackendWithoutUsableDeviceIsUnavailable() {
        assertEquals(BackendPlan.Unavailable(Backend.VULKAN), plan(BackendPreference.VULKAN, cpu, oldVulkan))
        assertEquals(BackendPlan.Unavailable(Backend.OPENCL), plan(BackendPreference.OPENCL, cpu))
    }
}
