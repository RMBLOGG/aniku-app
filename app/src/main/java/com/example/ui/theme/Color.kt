package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Default (Cinematic Dark) ──
val CinematicBackground    = Color(0xFF0A0A0A)
val CinematicSurface       = Color(0xFF161616)
val CinematicSurfaceVariant= Color(0xFF222222)
val TextPrimary            = Color(0xFFFFFFFF)
val TextSecondary          = Color(0xFF9E9E9E)
val BorderColor            = Color(0xFF2A2A2A)

// ── Netflix ──
val NetflixBackground      = Color(0xFF141414)
val NetflixSurface         = Color(0xFF1F1F1F)
val NetflixSurfaceVariant  = Color(0xFF2A2A2A)
val NetflixAccent          = Color(0xFFE50914)

// ── Midnight ──
val MidnightBackground     = Color(0xFF0B0C1A)
val MidnightSurface        = Color(0xFF13152B)
val MidnightSurfaceVariant = Color(0xFF1C1F3A)
val MidnightAccent         = Color(0xFF7C5AF6)

// ── Sakura Noir (Premium) ──
// Background gelap netral (bukan hitam pekat polos) supaya aksen pink-nya
// tetap kerasa "soft" bukan norak, selisih background->surface dijaga tipis
// (konsisten sama prinsip floating nav biar gak ada elemen yang keliatan
// "kotak" solid terpisah pas nyatu sama komponen lain di app).
val SakuraNoirBackground     = Color(0xFF16121A)
val SakuraNoirSurface        = Color(0xFF201A26)
val SakuraNoirSurfaceVariant = Color(0xFF2B2333)
val SakuraNoirAccent         = Color(0xFFF2A6C4)

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
    "Netflix" -> ThemeColors(
        background     = NetflixBackground,
        surface        = NetflixSurface,
        surfaceVariant = NetflixSurfaceVariant,
        accent         = NetflixAccent
    )
    "Midnight" -> ThemeColors(
        background     = MidnightBackground,
        surface        = MidnightSurface,
        surfaceVariant = MidnightSurfaceVariant,
        accent         = MidnightAccent
    )
    "SakuraNoir" -> ThemeColors(
        background     = SakuraNoirBackground,
        surface        = SakuraNoirSurface,
        surfaceVariant = SakuraNoirSurfaceVariant,
        accent         = SakuraNoirAccent
    )
    else -> ThemeColors(  // Default
        background     = CinematicBackground,
        surface        = CinematicSurface,
        surfaceVariant = CinematicSurfaceVariant,
        accent         = getAccentColor(accentName)
    )
}
