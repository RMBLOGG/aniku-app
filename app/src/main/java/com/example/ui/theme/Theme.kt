package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    accentName: String = "Red",
    textScale: String = "Sedang",
    themePreset: String = "Default",
    content: @Composable () -> Unit
) {
    val tc = getThemeColors(themePreset, accentName)

    val colorScheme = if (darkTheme || themePreset != "Default") {
        darkColorScheme(
            primary         = tc.accent,
            secondary       = if (accentName == "Orange") AccentRed else AccentOrange,
            background      = tc.background,
            surface         = tc.surface,
            surfaceVariant  = tc.surfaceVariant,
            onBackground    = tc.onBackground,
            onSurface       = tc.onSurface,
            onPrimary       = Color.White
        )
    } else {
        lightColorScheme(
            primary         = tc.accent,
            secondary       = if (accentName == "Orange") AccentRed else AccentOrange,
            background      = Color(0xFFF5F5F5),
            surface         = Color.White,
            surfaceVariant  = Color(0xFFEEEEEE),
            onBackground    = Color(0xFF121212),
            onSurface       = Color(0xFF121212),
            onPrimary       = Color.White
        )
    }

    val typography = getTypography(textScale)

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = typography,
        content     = content
    )
}
