package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * Tier visual badge. Dulu ditentuin dari harga, sekarang dari skin_id
 * langsung (lihat [badgeSkinStyle]) biar tiap desain punya karakter sendiri.
 */
enum class BadgeTier { STANDARD, HOLO, NEON }

// Tetep disediain buat kompatibilitas kalau ada pemanggil lama yg masih
// nurunin tier dari harga - tapi jalur utama sekarang lewat badgeSkinStyle().
fun badgeTierForPrice(priceDiamond: Int): BadgeTier = when {
    priceDiamond >= 2000 -> BadgeTier.NEON
    priceDiamond >= 1000 -> BadgeTier.HOLO
    else -> BadgeTier.STANDARD
}

/** Deskripsi visual 1 skin: bentuk, tier, dan warna dasar TETAP (gak ngikut warna clan). */
data class BadgeSkinStyle(
    val shape: String,       // "ribbon" atau "pennant"
    val tier: BadgeTier,
    val baseColor: Color
)

// Warna dasar tiap skin sengaja beda-beda & FIX per desain, gak diambil dari
// warna clan - biar keliatan variasinya walau tag/clan-nya sama.
val badgeSkinRegistry: Map<String, BadgeSkinStyle> = mapOf(
    "ribbon_standard" to BadgeSkinStyle("ribbon", BadgeTier.STANDARD, Color(0xFF3B82F6)),   // biru
    "ribbon_holo" to BadgeSkinStyle("ribbon", BadgeTier.HOLO, Color(0xFF9B4FE0)),           // ungu (dasar sblm rainbow scroll)
    "ribbon_neon" to BadgeSkinStyle("ribbon", BadgeTier.NEON, Color(0xFFFF2E9A)),           // pink neon
    "pennant_standard" to BadgeSkinStyle("pennant", BadgeTier.STANDARD, Color(0xFFF5A623)), // oranye
    "pennant_holo" to BadgeSkinStyle("pennant", BadgeTier.HOLO, Color(0xFF2FA8BF)),         // teal
    "pennant_neon" to BadgeSkinStyle("pennant", BadgeTier.NEON, Color(0xFF39FF6A))          // hijau neon
)

fun badgeSkinStyle(skinId: String): BadgeSkinStyle =
    badgeSkinRegistry[skinId] ?: BadgeSkinStyle("ribbon", BadgeTier.STANDARD, Color(0xFF8A4FD6))

private fun Color.lighten(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[2] = min(1f, hsv[2] + factor)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.darken(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[2] = max(0f, hsv[2] - factor)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// Palet penuh buat efek "holographic foil" - dilooping biar scroll-nya mulus.
private val rainbowSweep = listOf(
    Color(0xFFFF2E63), Color(0xFFFF8A3D), Color(0xFFFFE93D), Color(0xFF3DFF8A),
    Color(0xFF3DD9FF), Color(0xFF9B4FE0), Color(0xFFFF2E63)
)

/**
 * Badge visual final - bentuk dari [shape], warna & efek animasi dari [tier]:
 *   STANDARD -> gradient solid + kilau kaca statis + shimmer pelan
 *   HOLO     -> warna "foil" pelangi yg scroll terus-menerus + shimmer glint
 *   NEON     -> glow luar berdenyut + border nyala + scanline cepat + breathing scale
 */
@Composable
fun FuturisticBadge(
    text: String,
    baseColor: Color,
    modifier: Modifier = Modifier,
    tier: BadgeTier = BadgeTier.STANDARD,
    shape: Shape = RibbonBadgeShape()
) {
    val infinite = rememberInfiniteTransition(label = "badge_fx")

    val shimmer by infinite.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val rainbowScroll by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val scanline by infinite.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline"
    )
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val baseGradient = when (tier) {
        BadgeTier.NEON -> Brush.linearGradient(
            listOf(baseColor.lighten(0.2f), baseColor, baseColor.darken(0.3f))
        )
        BadgeTier.HOLO -> Brush.linearGradient(
            colors = rainbowSweep,
            start = Offset(rainbowScroll * 200f - 100f, 0f),
            end = Offset(rainbowScroll * 200f + 60f, 70f)
        )
        BadgeTier.STANDARD -> Brush.linearGradient(
            listOf(baseColor.lighten(0.18f), baseColor, baseColor.darken(0.2f))
        )
    }

    Box(
        modifier = modifier
            .scale(if (tier == BadgeTier.NEON) breathe else 1f)
            .then(
                if (tier == BadgeTier.NEON) {
                    Modifier.shadow(
                        elevation = (9 + 7 * glowPulse).dp,
                        shape = shape,
                        ambientColor = baseColor,
                        spotColor = baseColor
                    )
                } else Modifier
            )
            .clip(shape)
            .background(baseGradient)
            .then(
                if (tier != BadgeTier.STANDARD) {
                    Modifier.border(
                        width = if (tier == BadgeTier.NEON) 1.4.dp else 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.75f * glowPulse), baseColor.copy(alpha = 0.25f))
                        ),
                        shape = shape
                    )
                } else Modifier
            )
    ) {
        // Kilau kaca statis di bagian atas - bikin badge STANDARD gak keliatan flat
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        endY = 40f
                    )
                )
        )

        // Shimmer sweep pelan - dipakai semua tier
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent),
                        start = Offset(shimmer * 220f - 60f, 0f),
                        end = Offset(shimmer * 220f, 60f)
                    )
                )
        )

        // Scanline cepat & tipis - khusus NEON, biar kesan "energi/listrik"
        if (tier == BadgeTier.NEON) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(scanline * 180f - 20f, 0f),
                            end = Offset(scanline * 180f + 6f, 60f)
                        )
                    )
            )
        }

        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(
                    start = if (shape is PennantBadgeShape) 12.dp else 10.dp,
                    end = if (shape is PennantBadgeShape) 16.dp else 12.dp,
                    top = 3.dp,
                    bottom = 3.dp
                )
        )
    }
}
