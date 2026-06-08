package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Cinematic dark colors
val CinematicBackground = Color(0xFF0A0A0A)
val CinematicSurface = Color(0xFF161616)
val CinematicSurfaceVariant = Color(0xFF222222)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9E9E9E)
val BorderColor = Color(0xFF2A2A2A)

// Accent Colors palette
val AccentRed = Color(0xFFE53935)
val AccentGreen = Color(0xFF4CAF50)
val AccentBlue = Color(0xFF2196F3)
val AccentPurple = Color(0xFF9C27B0)
val AccentOrange = Color(0xFFFF8C00)

fun getAccentColor(name: String): Color {
    return when (name) {
        "Red" -> AccentRed
        "Green" -> AccentGreen
        "Blue" -> AccentBlue
        "Purple" -> AccentPurple
        "Orange" -> AccentOrange
        else -> AccentRed
    }
}
