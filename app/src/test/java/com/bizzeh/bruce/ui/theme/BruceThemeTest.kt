package com.bizzeh.bruce.ui.theme

import org.junit.jupiter.api.Assertions.assertSame
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
}
