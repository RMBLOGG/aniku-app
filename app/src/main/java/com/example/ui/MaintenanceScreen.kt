package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_maintenance_mascot),
            contentDescription = "Aniku sedang maintenance",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth(0.85f)
        )

        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
