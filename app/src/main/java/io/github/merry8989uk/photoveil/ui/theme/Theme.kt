package io.github.merry8989uk.photoveil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color(0xFF0B0B12),
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = Color(0xFFDCD4FF),
    secondary = Color(0xFF3BE0C0),
    onSecondary = Color(0xFF04231D),
    secondaryContainer = Color(0xFF123A34),
    onSecondaryContainer = Color(0xFFB7F5E8),
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFE8E6F2),
    surface = Color(0xFF12121C),
    onSurface = Color(0xFFE8E6F2),
    surfaceVariant = Color(0xFF1C1C29),
    onSurfaceVariant = Color(0xFFB3B0C4),
    outline = Color(0xFF3A3A4E),
    error = Color(0xFFFF6B6B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3DE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1B0F52),
    secondary = Color(0xFF0E8C77),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF14131C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14131C),
    surfaceVariant = Color(0xFFEDEBF5),
    onSurfaceVariant = Color(0xFF4A4757),
    outline = Color(0xFFC9C5D8),
)

/** Applies the user's theme mode and accent colour. */
@Composable
fun PhotoVeilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkScheme else LightScheme
    val scheme = if (accent != null) {
        base.copy(
            primary = accent,
            onPrimary = if (darkTheme) Color(0xFF0B0B12) else Color(0xFFFFFFFF),
            primaryContainer = base.primaryContainer,
        )
    } else {
        base
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
