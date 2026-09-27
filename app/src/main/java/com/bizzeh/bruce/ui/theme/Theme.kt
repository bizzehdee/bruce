package com.bizzeh.bruce.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

internal fun bruceColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) BruceDarkColors else BruceLightColors

@Composable
fun BruceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = bruceColorScheme(darkTheme), content = content)
}
