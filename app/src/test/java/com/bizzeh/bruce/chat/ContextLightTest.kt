package com.bizzeh.bruce.chat

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.bizzeh.bruce.runtime.ContextUse
import com.bizzeh.bruce.settings.ThemeMode
import com.bizzeh.bruce.settings.ThemeSettings
import com.bizzeh.bruce.ui.theme.BruceTheme
import com.bizzeh.bruce.ui.theme.LocalTrafficLights
import com.bizzeh.bruce.ui.theme.TrafficLights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextLightTest {
    @get:Rule
    val compose = createComposeRule()

    private fun light(used: Int, limit: Int) = ContextLight.of(ContextUse(used = used, total = 4096, dropped = 0, limit = limit))

    @Test
    fun greenBelow65AmberTo85RedFrom85OfWhatThePromptMayUse() {
        assertEquals(ContextLight.GREEN, light(64, 100))
        assertEquals(ContextLight.AMBER, light(65, 100))
        assertEquals(ContextLight.AMBER, light(84, 100))
        assertEquals(ContextLight.RED, light(85, 100))
        assertEquals("a 4K context's full prompt limit is red", ContextLight.RED, light(3072, 3072))
        assertEquals(ContextLight.RED, light(0, 0))
    }

    @Test
    fun theThemeProvidesLightsOnlyWhenTheSettingIsOn() {
        var on: TrafficLights? = null
        var off: TrafficLights? = null
        var dark: TrafficLights? = null
        compose.setContent {
            BruceTheme(ThemeSettings(ThemeMode.LIGHT)) { on = LocalTrafficLights.current }
            BruceTheme(ThemeSettings(ThemeMode.LIGHT, contextTrafficLights = false)) { off = LocalTrafficLights.current }
            BruceTheme(ThemeSettings(ThemeMode.DARK)) { dark = LocalTrafficLights.current }
        }

        assertNotNull(on)
        assertNull(off)
        assertNotEquals("dark mode has its own, lighter shades", on, dark)
    }
}
