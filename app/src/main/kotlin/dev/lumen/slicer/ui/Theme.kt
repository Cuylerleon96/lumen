package dev.lumen.slicer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand tokens — see docs/design-language.md. Dark-first; brand colors, not the wallpaper.
private val Bg = Color(0xFF0B0D13)
private val Surface = Color(0xFF13151D)
private val SurfaceVariant = Color(0xFF1E2130)
private val OnBg = Color(0xFFE8E8ED)
private val OnBgMuted = Color(0xFF8B8DA3)
private val Accent = Color(0xFF8B5CF6)
private val Border = Color(0x0FFFFFFF) // white @ ~6%

private val Success = Color(0xFF34D399)
private val Error = Color(0xFFF87171)
private val Warning = Color(0xFFFBBF24)
private val Info = Color(0xFF60A5FA)

/** Extra semantic tokens Material 3's ColorScheme doesn't carry. */
data class ExtendedColors(
    val border: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val accentGlow: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(Border, Success, Warning, Info, Accent.copy(alpha = 0.35f))
}

/** Convenience accessor: `ExtendedTheme.border`, etc. */
object ExtendedTheme {
    val border: Color @Composable get() = LocalExtendedColors.current.border
    val success: Color @Composable get() = LocalExtendedColors.current.success
    val warning: Color @Composable get() = LocalExtendedColors.current.warning
    val info: Color @Composable get() = LocalExtendedColors.current.info
    val accentGlow: Color @Composable get() = LocalExtendedColors.current.accentGlow
}

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = Color(0xFFD6CCFF),
    secondary = Accent,
    onSecondary = Color.White,
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnBgMuted,
    outline = Border,
    outlineVariant = Color(0x14FFFFFF),
    error = Error,
    onError = Color.White,
)

// Light is a close sibling for the later toggle; not the default.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF14141A),
    surface = Color.White,
    onSurface = Color(0xFF14141A),
    surfaceVariant = Color(0xFFECECF2),
    onSurfaceVariant = Color(0xFF565669),
    error = Color(0xFFB91C1C),
)

@Composable
fun AppTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalExtendedColors provides ExtendedColors(Border, Success, Warning, Info, Accent.copy(alpha = 0.35f)),
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
