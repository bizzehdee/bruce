package com.bizzeh.bruce.litert

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-hardware check for the Tensor G5/G6 NPU delegate (TASK-119): whether
 * `LiteRtNative.loadModel(..., LiteRtBackendPreference.NPU, ...)` actually initialises on the NPU,
 * not just falls back to the CPU silently. Push a Tensor-AOT-compiled `.litertlm` model to
 * `files/litert-npu` first (e.g. `litert-community/Gemma3-1B-IT-Tensor-NPU`'s
 * `Gemma3-1B-IT_int8_aot_ekv4096_G6.litertlm`), then:
 * `adb shell am instrument -w -e class com.bizzeh.bruce.litert.LiteRtNpuDeviceTest
 *  -e model Gemma3-1B-IT_int8_aot_ekv4096_G6.litertlm com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner`
 */
@ManualOnly("Tensor NPU delegate: needs a Tensor G5/G6 phone and a model pushed to files/litert-npu")
@RunWith(AndroidJUnit4::class)
class LiteRtNpuDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "litert-npu")

    @Test
    fun loadsOnTheTensorNpuDelegate() {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("model")) { "-e model <file in files/litert-npu>" }
        val model = File(dir, name)
        Log.i(TAG, "loading $model (${model.length()} bytes) on NPU; nativeLibraryDir=${context.applicationInfo.nativeLibraryDir}")

        when (val result = LiteRtNative.loadModel(model, 4096, LiteRtBackendPreference.NPU, context.applicationInfo.nativeLibraryDir)) {
            is LiteRtLoadResult.Loaded -> {
                Log.i(TAG, "loaded: backend=${result.backend} failedBackends=${result.failedBackends} contextTokens=${result.model.maxContextTokens}")
                result.model.close()
                assertEquals("fell back instead of using the NPU", LiteRtBackendChoice.NPU, result.backend)
            }
            LiteRtLoadResult.Failed -> fail("model failed to load on any backend (NPU and CPU)")
        }
    }

    private companion object {
        const val TAG = "LiteRtNpuDeviceTest"
    }
}
