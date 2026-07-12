package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Layar full-screen yang nge-block seluruh app pas maintenance_mode di Firebase
 * Remote Config di-set true — berubah real-time tanpa perlu update apk.
 *
 * Ilustrasi mascot Aniku (img_maintenance_mascot) udah include teks
 * "Sedang Dalam Perbaikan" bawaan. Text `message` di bawah tetap ditampilin
 * biar admin masih bisa nampilin info tambahan/real-time lewat Remote Config
 * tanpa perlu generate ulang gambar.
 */
@Composable
fun MaintenanceScreen(message: String) {
    val bg = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    // Efek "napas" halus buat mascot-nya, biar kerasa masih hidup/lagi kerja
    val floatTransition = rememberInfiniteTransition(label = "maintenance_float")
    val floatOffset by floatTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    // Animasi pulse buat titik-titik "sedang proses" di bawah teks
    val dotsTransition = rememberInfiniteTransition(label = "maintenance_dots")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.16f),
                        bg
                    ),
                    radius = 1000f
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_maintenance_mascot),
            contentDescription = "Aniku sedang maintenance",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth(1f)
                .offset(y = floatOffset.dp)
        )

        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                val dotAlpha by dotsTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, delayMillis = index * 200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot_$index"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .alpha(dotAlpha)
                        .background(accent, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
