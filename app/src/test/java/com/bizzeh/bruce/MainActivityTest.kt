package com.bizzeh.bruce

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** App-level manifest settings; the activity itself needs the native engine, so it is tested on a device. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    private val info = ApplicationProvider.getApplicationContext<Context>().applicationInfo

    @Test
    fun appHasLauncherIcon() {
        assertNotEquals(0, info.icon)
    }

    @Test
    fun appDataIsExcludedFromBackup() {
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
