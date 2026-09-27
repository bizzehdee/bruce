package com.bizzeh.bruce.inference

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizzeh.bruce.hardware.CpuFeatures
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendDeviceTest {
    private val capabilities = deviceEngine(Dispatchers.Default).getCapabilities().also { capabilities ->
        capabilities.devices.forEach { Log.i(TAG, "device: $it") }
        Log.i(TAG, "cpu backend features: ${capabilities.cpuBackendFeatures}")
    }

    private fun cpuBackendHas(feature: String) = "$feature=1" in capabilities.cpuBackendFeatures

    @Test
    fun exactlyOneCpuDevice() {
        assertEquals(1, capabilities.devices.count { it.backend == Backend.CPU })
    }

    @Test
    fun cpuVariantMatchesDetectedCpuFeatures() {
        val cpu = CpuFeatures.detect()

        assertEquals(cpu.dotProd, cpuBackendHas("DOTPROD"))
        assertEquals(cpu.fp16, cpuBackendHas("FP16_VA"))
        assertEquals(cpu.i8mm, cpuBackendHas("MATMUL_INT8"))
    }

    private companion object {
        const val TAG = "BackendDeviceTest"
    }
}
