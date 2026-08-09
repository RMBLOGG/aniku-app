package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge tag clan bentuk ribbon/pita, mirip "JF" / "TSR" di referensi.
 * Contoh: RibbonBadge(text = "JF", backgroundColor = Color(0xFF8A4FD6))
 */
@Composable
fun RibbonBadge(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    Row(
        modifier = modifier
            .clip(RibbonBadgeShape())
            .background(backgroundColor)
            .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Badge role bentuk pennant/tag, mirip "OTF" di referensi.
 * Contoh: PennantBadge(text = "OTF", backgroundColor = Color(0xFFF5A623))
 */
@Composable
fun PennantBadge(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    Row(
        modifier = modifier
            .clip(PennantBadgeShape())
            .background(backgroundColor)
            .padding(start = 10.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Badge level bentuk pill, mirip "Lvl. 256" di referensi.
 */
@Composable
fun LevelBadge(
    level: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF3A3F4B),
    textColor: Color = Color(0xFFE6C87A)
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CreditCard,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = textColor
        )
        Text(
            text = " Lvl. $level",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
