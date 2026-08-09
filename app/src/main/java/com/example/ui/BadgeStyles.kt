package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
 * Tier visual badge, ditentuin otomatis dari harga Diamond-nya - jadi badge
 * yg lebih mahal/langka kelihatan lebih "wah" tanpa perlu kolom baru di DB.
 */
enum class BadgeTier { STANDARD, HOLO, NEON }

fun badgeTierForPrice(priceDiamond: Int): BadgeTier = when {
    priceDiamond >= 2000 -> BadgeTier.NEON
    priceDiamond >= 1000 -> BadgeTier.HOLO
    else -> BadgeTier.STANDARD
}

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

private fun Color.hueShift(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Badge ribbon versi futuristik - gradient + shimmer sweep animasi + (buat
 * tier NEON) glow berdenyut. Bentuknya tetep pakai RibbonBadgeShape/
 * PennantBadgeShape yg udah ada, cuma "kulitnya" yg di-upgrade.
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

    val shimmer by infinite.animateFloatSafe(
        initial = -0.4f,
        target = 1.4f,
        durationMillis = 1700
    )
    val hueAnim by infinite.animateFloatSafe(
        initial = 0f,
        target = 360f,
        durationMillis = 4500
    )
    val glowPulse by infinite.animateFloatPingPong(
        initial = 0.45f,
        target = 1f,
        durationMillis = 900
    )

    val gradientColors = when (tier) {
        BadgeTier.HOLO -> listOf(
            baseColor.hueShift(hueAnim),
            baseColor.lighten(0.22f).hueShift(hueAnim + 70f),
            baseColor.darken(0.12f).hueShift(hueAnim + 140f)
        )
        BadgeTier.NEON -> listOf(
            baseColor.lighten(0.15f),
            baseColor,
            baseColor.darken(0.28f)
        )
        BadgeTier.STANDARD -> listOf(
            baseColor.lighten(0.18f),
            baseColor,
            baseColor.darken(0.2f)
        )
    }

    Box(
        modifier = modifier
            .then(
                if (tier == BadgeTier.NEON) {
                    Modifier.shadow(
                        elevation = (8 + 6 * glowPulse).dp,
                        shape = shape,
                        ambientColor = baseColor,
                        spotColor = baseColor
                    )
                } else Modifier
            )
            .clip(shape)
            .background(Brush.linearGradient(gradientColors))
            .then(
                if (tier != BadgeTier.STANDARD) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.7f * glowPulse), baseColor.copy(alpha = 0.3f))
                        ),
                        shape = shape
                    )
                } else Modifier
            )
    ) {
        // Shimmer sweep - garis terang yg geser dari kiri-atas ke kanan-bawah terus-menerus
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent),
                        start = Offset(shimmer * 220f - 60f, 0f),
                        end = Offset(shimmer * 220f, 60f)
                    )
                )
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 10.dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
        )
    }
}

// ── helper animasi kecil biar pemanggilan di atas ringkas ──────────────────
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatSafe(
    initial: Float,
    target: Float,
    durationMillis: Int
) = this.animateFloat(
    initialValue = initial,
    targetValue = target,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    label = "anim"
)

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatPingPong(
    initial: Float,
    target: Float,
    durationMillis: Int
) = this.animateFloat(
    initialValue = initial,
    targetValue = target,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    ),
    label = "anim_pingpong"
)
