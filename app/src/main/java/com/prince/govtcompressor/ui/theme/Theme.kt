package com.prince.govtcompressor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF1565C0)
private val BlueDark = Color(0xFF0D47A1)
private val BlueLight = Color(0xFF64B5F6)
private val BlueContainer = Color(0xFFE3F2FD)
private val AccentGreen = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BlueDark,
    secondary = Color(0xFF1976D2),
    onSecondary = Color.White,
    tertiary = AccentGreen,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE8EEF4)
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF0D1B2A),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = BlueLight,
    onSecondary = Color(0xFF0D1B2A),
    tertiary = Color(0xFF81C784),
    background = Color(0xFF101418),
    surface = Color(0xFF1B2127),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2A323C)
)

@Composable
fun GovtCompressorTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
