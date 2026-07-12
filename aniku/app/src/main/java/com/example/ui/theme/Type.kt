package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun getTypography(scale: String): Typography {
    val (titleSize, bodySize, labelSize) = when (scale) {
        "Kecil" -> Triple(18.sp, 13.sp, 10.sp)
        "Besar" -> Triple(26.sp, 17.sp, 14.sp)
        else -> Triple(22.sp, 15.sp, 12.sp) // "Sedang"
    }

    return Typography(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = titleSize,
            lineHeight = (titleSize.value * 1.3).sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = bodySize,
            lineHeight = (bodySize.value * 1.4).sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = labelSize,
            lineHeight = (labelSize.value * 1.2).sp,
            letterSpacing = 0.5.sp
        )
    )
}

val Typography = getTypography("Sedang")
