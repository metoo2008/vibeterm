package dev.vibeterm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TermBg = Color(0xFF0D1117)
val TermSurface = Color(0xFF161B22)
val TermGreen = Color(0xFF7EE787)
val TermYellow = Color(0xFFF0B72F)
val TermRed = Color(0xFFFF7B72)
val TermGray = Color(0xFF8B949E)

private val DarkColors = darkColorScheme(
    primary = TermGreen,
    onPrimary = TermBg,
    background = TermBg,
    onBackground = Color(0xFFE6EDF3),
    surface = TermSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = TermGray,
    error = TermRed,
)

@Composable
fun VibeTermTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
