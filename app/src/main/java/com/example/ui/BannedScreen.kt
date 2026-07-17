package com.example.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SignalDisplayFont
import com.example.ui.theme.SignalMonoFont
import kotlin.random.Random

/**
 * Layar full-screen yang nge-block seluruh app begitu device/akun ini
 * dianggap banned -- baik lewat kick realtime (Firebase) maupun pengecekan
 * device-ban pas app baru dibuka (termasuk sebelum sempat masuk Mode Tamu).
 *
 * Konsep visual: "transmisi diputus" -- ngambil identitas Aniku sendiri
 * ("Cinema-grade Anime Portal") dan diputerbalik jadi momen siaran yang
 * dihentikan paksa, bukan ikon-terlarang generik. Satu-satunya aksi yang
 * tersedia adalah nutup aplikasi -- gak ada jalan buat nembus balik ke
 * Mode Tamu atau layar login.
 */
@Composable
fun BannedScreen(onAcknowledge: () -> Unit = {}) {
    val ink = Color(0xFF0A0808)
    val signal = Color(0xFFE8483A)
    val bone = Color(0xFFEDE7E1)
    val slate = Color(0xFF8C837E)
    val activity = LocalContext.current as? Activity
    val exitInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ink)
    ) {
        StaticNoiseBand(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter),
            color = signal
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp)
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            Text(
                "ANIKU BROADCAST",
                fontFamily = SignalMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                color = signal
            )

            Spacer(modifier = Modifier.height(28.dp))

            SeveredFilmStrip(
                modifier = Modifier.fillMaxWidth(),
                tint = bone.copy(alpha = 0.14f)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                "TRANSMISI\nDIPUTUS",
                fontFamily = SignalDisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                letterSpacing = 0.5.sp,
                color = bone
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                "Device atau akun ini ditangguhkan oleh admin Aniku. Semua akses ke aplikasi, termasuk Mode Tamu, dikunci sampai statusnya ditinjau ulang.",
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = slate,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bone.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(signal, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "STATUS: SIGNAL-LOCKED",
                    fontFamily = SignalMonoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.2.sp,
                    color = bone.copy(alpha = 0.75f)
                )
            }

            Text(
                "Kalau menurut kamu ini keliru, hubungi tim Aniku lewat kanal komunitas resmi.",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = slate.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            PerforationDivider(color = bone.copy(alpha = 0.14f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "TUTUP APLIKASI",
                    fontFamily = SignalMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    color = bone,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable(interactionSource = exitInteraction, indication = null) {
                            onAcknowledge()
                            activity?.finish()
                        }
                        .padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StaticNoiseBand(modifier: Modifier = Modifier, color: Color) {
    val seed = remember { Random(42) }
    Canvas(modifier = modifier) {
        val lineCount = 46
        for (i in 0 until lineCount) {
            val y = size.height * (i / lineCount.toFloat())
            val alpha = (1f - (y / size.height)).coerceIn(0f, 1f) * 0.16f * (0.4f + seed.nextFloat() * 0.6f)
            val startX = size.width * seed.nextFloat() * 0.4f
            val segWidth = size.width * (0.15f + seed.nextFloat() * 0.5f)
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(startX, y),
                end = Offset((startX + segWidth).coerceAtMost(size.width), y),
                strokeWidth = 1.4f
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF0A0808)),
                startY = size.height * 0.35f,
                endY = size.height
            )
        )
    }
}

@Composable
private fun SeveredFilmStrip(modifier: Modifier = Modifier, tint: Color) {
    Row(modifier = modifier.height(44.dp)) {
        FilmStripHalf(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            tint = tint,
            jaggedOnRight = true
        )
        Spacer(modifier = Modifier.width(16.dp))
        FilmStripHalf(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    rotationZ = -3.2f
                    translationY = 9f
                },
            tint = tint,
            jaggedOnLeft = true
        )
    }
}

@Composable
private fun FilmStripHalf(
    modifier: Modifier = Modifier,
    tint: Color,
    jaggedOnLeft: Boolean = false,
    jaggedOnRight: Boolean = false
) {
    Canvas(modifier = modifier) {
        val holeSize = 6.dp.toPx()
        val holeGap = 16.dp.toPx()
        val holeCount = (size.width / holeGap).toInt()
        val jagAmp = 7.dp.toPx()
        val jagStep = 9.dp.toPx()
        val bgHole = Color(0xFF0A0808)

        val bodyPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)

            if (jaggedOnRight) {
                var y = 0f
                var idx = 0
                while (y < size.height) {
                    val nextY = (y + jagStep).coerceAtMost(size.height)
                    val bite = if (idx % 2 == 0) jagAmp else 0f
                    lineTo(size.width - bite, nextY)
                    idx++
                    y = nextY
                }
            } else {
                lineTo(size.width, size.height)
            }

            lineTo(0f, size.height)

            if (jaggedOnLeft) {
                var y = size.height
                var idx = 0
                while (y > 0f) {
                    val nextY = (y - jagStep).coerceAtLeast(0f)
                    val bite = if (idx % 2 == 0) jagAmp else 0f
                    lineTo(bite, nextY)
                    idx++
                    y = nextY
                }
            } else {
                lineTo(0f, 0f)
            }
            close()
        }

        drawPath(bodyPath, color = tint)

        // Lubang sprocket ala pita film di tepi atas & bawah
        for (i in 0..holeCount) {
            val cx = i * holeGap + holeGap / 2f
            if (cx > size.width - holeSize) continue
            drawRect(
                color = bgHole,
                topLeft = Offset(cx - holeSize / 2f, 5.dp.toPx()),
                size = Size(holeSize, holeSize)
            )
            drawRect(
                color = bgHole,
                topLeft = Offset(cx - holeSize / 2f, size.height - 5.dp.toPx() - holeSize),
                size = Size(holeSize, holeSize)
            )
        }
    }
}

@Composable
private fun PerforationDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 10f), 0f)
        )
    }
}
