package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Layar full-screen yang nge-block seluruh app real-time begitu admin nge-ban
 * akun ini (dipush via Firebase RTDB, lihat BanStatusManager). Sejajar sama
 * MaintenanceScreen -- device korban langsung ke-kick SAAT ITU JUGA, gak perlu
 * nunggu logout manual atau token expired.
 */
@Composable
fun BannedScreen(onAcknowledge: () -> Unit) {
    val danger = Color(0xFFFF4B3E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(danger.copy(alpha = 0.18f), MaterialTheme.colorScheme.background),
                    radius = 1000f
                )
            )
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(danger.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Block,
                contentDescription = null,
                tint = danger,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Akun Kamu Diblokir",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            "Akun kamu telah ditangguhkan (banned) oleh Admin. Kalau menurut kamu ini kesalahan, hubungi tim Aniku.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAcknowledge,
            colors = ButtonDefaults.buttonColors(containerColor = danger),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Mengerti", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
