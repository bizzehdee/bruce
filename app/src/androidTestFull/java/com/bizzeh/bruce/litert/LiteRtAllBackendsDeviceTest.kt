package com.bizzeh.bruce.litert

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-hardware check (TASK-119) for which backend a plain (non-AOT) `.litertlm` model actually
 * loads on when NPU, GPU and CPU are each offered in turn, one preference per attempt (not a
 * combined NPU->GPU->CPU plan — [LiteRtBackendSelection] has no such entry, and this test does not
 * add one; it is a diagnostic, not a production selection change). Push a plain `.litertlm` model
 * to `files/litert-npu` first (e.g. a `--variant standard` build from `tools/litertlm-convert`),
 * then:
 * `adb shell am instrument -w -e class com.bizzeh.bruce.litert.LiteRtAllBackendsDeviceTest
 *  -e model gemma-3-270m-standard.litertlm com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner`
 */
@ManualOnly("Tries NPU, GPU and CPU in turn on a real phone; needs a plain .litertlm pushed to files/litert-npu")
@RunWith(AndroidJUnit4::class)
class LiteRtAllBackendsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "litert-npu")

    @Test
    fun triesEachBackendInTurn() {
        val name = requireNotNull(InstrumentationRegistry.getArguments().getString("model")) { "-e model <file in files/litert-npu>" }
        val model = File(dir, name)
        Log.i(TAG, "model=$model (${model.length()} bytes) nativeLibraryDir=${context.applicationInfo.nativeLibraryDir}")

        for (preference in listOf(LiteRtBackendPreference.NPU, LiteRtBackendPreference.GPU, LiteRtBackendPreference.CPU)) {
            when (val result = LiteRtNative.loadModel(model, 4096, preference, context.applicationInfo.nativeLibraryDir)) {
                is LiteRtLoadResult.Loaded -> {
                    Log.i(TAG, "$preference requested -> loaded on ${result.backend} (failedBackends=${result.failedBackends}, contextTokens=${result.model.maxContextTokens})")
                    result.model.close()
                }
                LiteRtLoadResult.Failed -> Log.w(TAG, "$preference requested -> failed on every backend tried")
            }
        }
    }

    private companion object {
        const val TAG = "LiteRtAllBackendsTest"
    }
}
