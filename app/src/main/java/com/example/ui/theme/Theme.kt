package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentRed,
    secondary = AccentOrange,
    background = CinematicBackground,
    surface = CinematicSurface,
    surfaceVariant = CinematicSurfaceVariant,
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AccentRed,
    secondary = AccentOrange,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEEEE),
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    onPrimary = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    accentName: String = "Red",
    textScale: String = "Sedang",
    content: @Composable () -> Unit
) {
    val accent = getAccentColor(accentName)
    val colorScheme = if (darkTheme) {
        DarkColorScheme.copy(
            primary = accent,
            secondary = if (accentName == "Orange") AccentRed else AccentOrange
        )
    } else {
        LightColorScheme.copy(
            primary = accent,
            secondary = if (accentName == "Orange") AccentRed else AccentOrange
        )
    }

    val typography = getTypography(textScale)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
