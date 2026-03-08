package com.cybersentinel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Uses system default; swap in JetBrains Mono or Space Grotesk via Google Fonts if desired
val CyberTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 32.sp,
        letterSpacing = (-0.5).sp,
        color      = NeonGreen
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 20.sp,
        letterSpacing = 0.sp,
        color      = OnSurface
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 18.sp,
        letterSpacing = 0.5.sp,
        color      = OnSurface
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 15.sp,
        letterSpacing = 0.15.sp,
        color      = OnSurface
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        letterSpacing = 0.5.sp,
        color      = OnSurface
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        letterSpacing = 0.25.sp,
        color      = OnSurfaceMuted
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 10.sp,
        letterSpacing = 1.sp,
        color      = OnSurfaceMuted
    ),
)
