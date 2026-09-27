package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlamaNativeDeviceTest {
    @Test
    fun reportsPinnedLlamaCppVersion() {
        assertEquals("0.5.0", LlamaNative.version())
    }
}
