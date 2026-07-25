package com.example.ui

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SignalDisplayFont
import com.example.ui.theme.SignalMonoFont

/**
 * Layar full-screen kill-switch PERMANEN, dikontrol lewat Firebase Remote Config
 * (app_shutdown_enabled). Beda dari MaintenanceScreen yang kesannya sementara --
 * layar ini dipakai kalau aplikasi resmi ditutup total, misalnya karena admin
 * udah gak sanggup lagi bayar biaya server/database bulanan.
 *
 * Konsep visual: "sinyal padam" -- server yang mati, bukan user yang dihukum.
 * Cukup ganti nilai app_shutdown_message (dan opsional app_shutdown_support_info,
 * contoh: link donasi/kontak admin) di Firebase Console, real-time tanpa update apk.
 * Prioritas paling tinggi di MainActivity, di atas maintenance_mode & ban.
 */
@Composable
fun ShutdownScreen(
    message: String,
    supportInfo: String = "",
    onAcknowledge: () -> Unit = {}
) {
    val ink = Color(0xFF0B0D10)
    val dim = Color(0xFF4A5560)
    val bone = Color(0xFFE7EAEC)
    val slate = Color(0xFF8B949C)
    val activity = LocalContext.current as? Activity
    val exitInteraction = remember { MutableInteractionSource() }
    val uriHandler = LocalUriHandler.current
    val supportInfoUrls = remember(supportInfo) {
        Regex("""https?://\S+""").findAll(supportInfo)
            .map { it.value.trimEnd('.', ',', ')', '"') }
            .toList()
    }

    // Denyut pelan buat titik status "OFFLINE" -- kesan server yang benar-benar diam
    val pulseTransition = rememberInfiniteTransition(label = "shutdown_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ink)
    ) {
        FlatlineWave(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter),
            color = dim
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp)
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            Text(
                "ANIKU SERVER",
                fontFamily = SignalMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                color = dim
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "SINYAL\nPADAM",
                fontFamily = SignalDisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                letterSpacing = 0.5.sp,
                color = bone
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                message,
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
                        .background(dim.copy(alpha = pulseAlpha), shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "STATUS: SERVER OFFLINE",
                    fontFamily = SignalMonoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.2.sp,
                    color = bone.copy(alpha = 0.75f)
                )
            }

            if (supportInfo.isNotBlank()) {
                val annotatedSupportInfo = remember(supportInfo, supportInfoUrls) {
                    buildAnnotatedString {
                        var cursor = 0
                        for (url in supportInfoUrls) {
                            val start = supportInfo.indexOf(url, cursor)
                            if (start < 0) continue
                            append(supportInfo.substring(cursor, start))
                            val linkStart = length
                            withStyle(
                                SpanStyle(
                                    color = bone,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(url)
                            }
                            addStringAnnotation(
                                tag = "URL",
                                annotation = url,
                                start = linkStart,
                                end = length
                            )
                            cursor = start + url.length
                        }
                        if (cursor < supportInfo.length) {
                            append(supportInfo.substring(cursor))
                        }
                    }
                }
                ClickableText(
                    text = annotatedSupportInfo,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = slate.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(top = 16.dp),
                    onClick = { offset ->
                        annotatedSupportInfo
                            .getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()
                            ?.let { uriHandler.openUri(it.item) }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bone.copy(alpha = 0.06f))
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
                        .fillMaxWidth()
                        .clickable(interactionSource = exitInteraction, indication = null) {
                            onAcknowledge()
                            activity?.finish()
                        }
                        .padding(vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

/** Garis "flatline" (kayak EKG yang berhenti berdenyut) sebagai motif server mati. */
@Composable
private fun FlatlineWave(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val midY = size.height * 0.42f
        val bumpX = size.width * 0.42f
        val bumpWidth = size.width * 0.16f

        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(0f, midY),
            end = Offset(bumpX, midY),
            strokeWidth = 2f
        )
        // Satu denyut kecil terakhir sebelum jadi garis lurus panjang (server "wafat")
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(bumpX, midY),
            end = Offset(bumpX + bumpWidth * 0.3f, midY - 26f),
            strokeWidth = 2f
        )
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(bumpX + bumpWidth * 0.3f, midY - 26f),
            end = Offset(bumpX + bumpWidth * 0.6f, midY + 18f),
            strokeWidth = 2f
        )
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(bumpX + bumpWidth * 0.6f, midY + 18f),
            end = Offset(bumpX + bumpWidth, midY),
            strokeWidth = 2f
        )
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(bumpX + bumpWidth, midY),
            end = Offset(size.width, midY),
            strokeWidth = 2f
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF0B0D10)),
                startY = size.height * 0.35f,
                endY = size.height
            )
        )
    }
}
