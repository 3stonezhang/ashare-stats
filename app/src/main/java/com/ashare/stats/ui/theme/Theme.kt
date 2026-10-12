package com.ashare.stats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RiseRed = Color(0xFFE53935)
private val FallGreen = Color(0xFF43A047)
private val Ink = Color(0xFF1A1C1E)
private val Paper = Color(0xFFF7F4EF)
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF243040)

private val LightColors = lightColorScheme(
    primary = RiseRed,
    onPrimary = Color.White,
    secondary = FallGreen,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = CardLight,
    onSurface = Ink,
    error = RiseRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A80),
    onPrimary = Ink,
    secondary = Color(0xFFA5D6A7),
    onSecondary = Ink,
    background = Color(0xFF121820),
    onBackground = Color(0xFFE8EEF5),
    surface = CardDark,
    onSurface = Color(0xFFE8EEF5),
    error = Color(0xFFFF8A80),
)

@Composable
fun AShareStatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
