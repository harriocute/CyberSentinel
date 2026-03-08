package com.cybersentinel.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Neon Cybersecurity Palette ─────────────────────────────────────────────
val NeonGreen       = Color(0xFF00FF9C)
val NeonBlue        = Color(0xFF00D4FF)
val NeonPurple      = Color(0xFFBF5AF2)
val DangerRed       = Color(0xFFFF453A)
val WarningAmber    = Color(0xFFFFD60A)

val BackgroundDark  = Color(0xFF080B14)
val SurfaceDark     = Color(0xFF0E1320)
val SurfaceVariant  = Color(0xFF141A2E)
val CardBackground  = Color(0xFF111827)
val DividerColor    = Color(0xFF1E293B)

val OnSurface       = Color(0xFFE2E8F0)
val OnSurfaceMuted  = Color(0xFF64748B)

private val CyberDarkColorScheme = darkColorScheme(
    primary          = NeonGreen,
    onPrimary        = Color(0xFF003320),
    primaryContainer = Color(0xFF00472B),
    onPrimaryContainer = NeonGreen,

    secondary        = NeonBlue,
    onSecondary      = Color(0xFF003344),
    secondaryContainer = Color(0xFF004D66),
    onSecondaryContainer = NeonBlue,

    tertiary         = NeonPurple,
    onTertiary       = Color(0xFF2A0044),

    background       = BackgroundDark,
    onBackground     = OnSurface,

    surface          = SurfaceDark,
    onSurface        = OnSurface,

    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,

    error            = DangerRed,
    onError          = Color(0xFF2D0000),
    errorContainer   = Color(0xFF4D0000),

    outline          = Color(0xFF1E3A5F),
    outlineVariant   = DividerColor,
)

@Composable
fun CyberSentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberDarkColorScheme,
        typography  = CyberTypography,
        content     = content
    )
}
