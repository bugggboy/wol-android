package com.wolwake.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    background = Color(0xFFF4F7F8),
    onBackground = Color(0xFF11201F),
    surface = Color.White,
    onSurface = Color(0xFF11201F),
    surfaceVariant = Color(0xFFE8EFF0),
    onSurfaceVariant = Color(0xFF546A69),
    outline = Color(0xFFCBD9D9),
    error = Color(0xFFB3261E)
)

@Composable
fun WolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
