package com.sesolibre.somnia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// App nocturna: el tema oscuro es el principal.
private val Night = Color(0xFF0F1220)
private val NightSurface = Color(0xFF1A1E30)
private val Moon = Color(0xFF9FB4FF)
private val MoonDim = Color(0xFF6C7BB3)
private val Lavender = Color(0xFFC7B8F5)
private val SoftRed = Color(0xFFFFB4AB)

private val DarkColors = darkColorScheme(
    primary = Moon,
    onPrimary = Color(0xFF0B1030),
    secondary = Lavender,
    onSecondary = Color(0xFF201540),
    background = Night,
    onBackground = Color(0xFFE3E5F2),
    surface = NightSurface,
    onSurface = Color(0xFFE3E5F2),
    surfaceVariant = Color(0xFF262B42),
    onSurfaceVariant = Color(0xFFB9BFD6),
    outline = MoonDim,
    error = SoftRed,
)

/**
 * Siempre oscuro: la app se usa al acostarse y al despertar, con la luz
 * apagada. Un tema claro (si el sistema lo tiene) deslumbraría justo entonces.
 */
@Composable
fun SomniaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
