package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Warna dasar dipakai bareng buat konsistensi sama badge/nama Beta di ChatScreen.kt/FeedScreen.kt
private val betaRingColors = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF22D3EE))

/**
 * Bungkus avatar dengan cincin gradient yang muter pelan - efek visual eksklusif buat
 * role Beta. Murni dekoratif, taruh avatar asli (Image/AsyncImage/Box inisial) sebagai
 * `content` di dalamnya.
 *
 * Pemakaian:
 * ```
 * BetaAvatarRing(size = 30.dp) {
 *     AsyncImage(model = url, ... modifier = Modifier.fillMaxSize().clip(CircleShape))
 * }
 * ```
 */
@Composable
fun BetaAvatarRing(
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "betaRing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "betaRingRotation"
    )
    val ringThickness = 2.dp
    Box(
        modifier = modifier.size(size + ringThickness * 2),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(betaRingColors),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = ringThickness.toPx())
                )
            }
        }
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * Ledakan partikel kecil sesaat - dipicu SEKALI tiap kali `key` berubah (biasanya diisi
 * message.id). Cocok ditumpuk (Box overlay) di atas bubble chat pesan yang baru dikirim
 * user Beta. Otomatis ilang sendiri abis durasi animasi (gak perlu di-dismiss manual).
 */
@Composable
fun MessageSendBurst(key: Any?, modifier: Modifier = Modifier) {
    var visible by remember(key) { mutableStateOf(true) }
    val progress = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        visible = false
    }

    if (visible) {
        Box(modifier = modifier) {
            val particleCount = 8
            repeat(particleCount) { i ->
                val angleDeg = i * (360f / particleCount)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val distance = 26f * progress.value
                val offsetX = (cos(angleRad) * distance).toFloat()
                val offsetY = (sin(angleRad) * distance).toFloat()
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(offsetX.dp, offsetY.dp)
                        .size(4.dp)
                        .alpha((1f - progress.value).coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(
                            if (i % 2 == 0) Color(0xFF22D3EE) else Color(0xFF3B82F6)
                        )
                )
            }
        }
    }
}
