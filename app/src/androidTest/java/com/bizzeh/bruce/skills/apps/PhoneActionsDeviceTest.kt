package com.bizzeh.bruce.skills.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opens Settings, a web page and the share sheet on the phone; press Back after. */
@ManualOnly("opens other apps on the phone")
@RunWith(AndroidJUnit4::class)
class PhoneActionsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val actions = AndroidPhoneActions(context)

    @Test
    fun launcherAppsAreFoundAndOpened() {
        val apps = actions.launchable()
        val settings = apps.single { it.packageName == "com.android.settings" }
        assertFalse("Bruce is not offered to itself", apps.any { it.packageName == context.packageName })
        assertTrue(actions.launch(settings.packageName))
        assertTrue(actions.openUrl("https://example.com/"))
        assertTrue(actions.share("Shared by a Bruce device test", null))
    }
}
