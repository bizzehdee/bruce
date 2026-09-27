package com.bizzeh.bruce.ui.theme

import com.bizzeh.bruce.settings.ThemeMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BruceThemeTest {
    @Test
    fun darkThemeUsesDarkPalette() {
        assertSame(BruceDarkColors, bruceColorScheme(darkTheme = true))
    }

    @Test
    fun lightThemeUsesLightPalette() {
        assertSame(BruceLightColors, bruceColorScheme(darkTheme = false))
    }

    @Test
    fun systemModeFollowsTheSystem() {
        assertTrue(isDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDark(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun explicitModesIgnoreTheSystem() {
        assertFalse(isDark(ThemeMode.LIGHT, systemDark = true))
        assertTrue(isDark(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun dynamicColourNeedsAndroid12() {
        assertFalse(dynamicColourSupported(sdkInt = 30))
        assertTrue(dynamicColourSupported(sdkInt = 31))
    }
}
