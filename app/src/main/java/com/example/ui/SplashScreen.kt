package com.example.ui

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

/**
 * Splash screen pembuka app: karakter Aniku muncul dengan entrance animasi,
 * lalu idle "hidup" (melayang naik-turun + sway) selama splash ditampilkan.
 * Tidak pakai video/GIF/Lottie — murni digerakkan lewat Compose animation
 * di atas satu aset gambar (splash_character.webp).
 */
@Composable
fun SplashScreen() {
    val accentColor = MaterialTheme.colorScheme.primary

    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(80)
        visible = true
    }

    // Ambient glow di belakang karakter, biar splash gak keliatan flat
    val infiniteTransition = rememberInfiniteTransition(label = "splash_ambient")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    // Idle motion: melayang naik-turun pelan
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatOffset"
    )

    // Idle motion: goyang tipis kiri-kanan (kayak napas/nge-bounce ringan)
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swayAngle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Glow blob di belakang karakter — radial gradient fade ke transparan,
        // bukan blur di dalam kotak (itu penyebab keliatan kotak merah kemarin)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .size(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = glowAlpha),
                            accentColor.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.7f, animationSpec = tween(650, easing = EaseOutBack))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_character),
                    contentDescription = "Aniku",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(320.dp)
                        .offset(y = floatOffset.dp)
                        .rotate(swayAngle)
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 150)) +
                        slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(500, delayMillis = 150, easing = EaseOutCubic))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Aniku",
                        color = accentColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Cinema-grade Anime Portal",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
