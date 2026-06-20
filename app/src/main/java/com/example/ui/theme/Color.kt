package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Default (Cinematic Dark) ──
val CinematicBackground    = Color(0xFF0A0A0A)
val CinematicSurface       = Color(0xFF161616)
val CinematicSurfaceVariant= Color(0xFF222222)
val TextPrimary            = Color(0xFFFFFFFF)
val TextSecondary          = Color(0xFF9E9E9E)
val BorderColor            = Color(0xFF2A2A2A)

// ── OLED ──
val OledBackground      = Color(0xFF000000)
val OledSurface         = Color(0xFF0D0D0D)
val OledSurfaceVariant  = Color(0xFF1A1A1A)
val OledAccent          = Color(0xFF00E5FF)

// ── Midnight ──
val MidnightBackground     = Color(0xFF0B0C1A)
val MidnightSurface        = Color(0xFF13152B)
val MidnightSurfaceVariant = Color(0xFF1C1F3A)
val MidnightAccent         = Color(0xFF7C5AF6)

// ── Accent palette ──
val AccentRed    = Color(0xFFE53935)
val AccentGreen  = Color(0xFF4CAF50)
val AccentBlue   = Color(0xFF2196F3)
val AccentPurple = Color(0xFF9C27B0)
val AccentOrange = Color(0xFFFF8C00)

fun getAccentColor(name: String): Color = when (name) {
    "Red"    -> AccentRed
    "Green"  -> AccentGreen
    "Blue"   -> AccentBlue
    "Purple" -> AccentPurple
    "Orange" -> AccentOrange
    else     -> AccentRed
}

data class ThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val accent: Color,
    val onBackground: Color = Color.White,
    val onSurface: Color = Color.White
)

fun getThemeColors(preset: String, accentName: String): ThemeColors = when (preset) {
    "OLED" -> ThemeColors(
        background     = OledBackground,
        surface        = OledSurface,
        surfaceVariant = OledSurfaceVariant,
        accent         = OledAccent
    )
    "Midnight" -> ThemeColors(
        background     = MidnightBackground,
        surface        = MidnightSurface,
        surfaceVariant = MidnightSurfaceVariant,
        accent         = MidnightAccent
    )
    else -> ThemeColors(  // Default
        background     = CinematicBackground,
        surface        = CinematicSurface,
        surfaceVariant = CinematicSurfaceVariant,
        accent         = getAccentColor(accentName)
    )
}
