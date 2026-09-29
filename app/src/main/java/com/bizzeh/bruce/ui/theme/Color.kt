package com.bizzeh.bruce.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val BruceLightColors = lightColorScheme(
    primary = Color(0xFF855318),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBD),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEDCBE),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFF58633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE8B4),
    onTertiaryContainer = Color(0xFF161E01),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF201B16),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF201B16),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51443A),
    outline = Color(0xFF837468),
    outlineVariant = Color(0xFFD5C3B5),
    surfaceDim = Color(0xFFE3D8D0),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF1EA),
    surfaceContainer = Color(0xFFF7EBE4),
    surfaceContainerHigh = Color(0xFFF1E6DE),
    surfaceContainerHighest = Color(0xFFECE0D9),
    inverseSurface = Color(0xFF362F2A),
    inverseOnSurface = Color(0xFFFBEEE7),
    inversePrimary = Color(0xFFFDB876),
)

internal val BruceDarkColors = darkColorScheme(
    primary = Color(0xFFFDB876),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBD),
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFEDCBE),
    tertiary = Color(0xFFC0CC9A),
    onTertiary = Color(0xFF2B3410),
    tertiaryContainer = Color(0xFF414B24),
    onTertiaryContainer = Color(0xFFDCE8B4),
    background = Color(0xFF18120D),
    onBackground = Color(0xFFECE0D9),
    surface = Color(0xFF18120D),
    onSurface = Color(0xFFECE0D9),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF51443A),
    surfaceDim = Color(0xFF18120D),
    surfaceBright = Color(0xFF3F3731),
    surfaceContainerLowest = Color(0xFF120D09),
    surfaceContainerLow = Color(0xFF201B16),
    surfaceContainer = Color(0xFF251F1A),
    surfaceContainerHigh = Color(0xFF302924),
    surfaceContainerHighest = Color(0xFF3B332E),
    inverseSurface = Color(0xFFECE0D9),
    inverseOnSurface = Color(0xFF362F2A),
    inversePrimary = Color(0xFF855318),
)

/** The chat's context bar by how full the chat is (TASK-064); null in [LocalTrafficLights] when the setting is off. */
data class TrafficLights(val green: Color, val amber: Color, val red: Color)

internal val LightTrafficLights = TrafficLights(green = Color(0xFF2E7D32), amber = Color(0xFFB26A00), red = Color(0xFFBA1A1A))
internal val DarkTrafficLights = TrafficLights(green = Color(0xFF81C995), amber = Color(0xFFFFB951), red = Color(0xFFFFB4AB))

