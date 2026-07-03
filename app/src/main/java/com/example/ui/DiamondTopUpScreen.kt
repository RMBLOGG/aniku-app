package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AnikuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiamondTopUpScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Top-up Diamond", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            HeroBanner()

            Spacer(modifier = Modifier.height(20.dp))
            Text("Cara Top-up", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            listOf(
                "Donasi lewat Trakteer dengan nominal berapa pun (rasio Rp4 = 1 DM, jadi Rp5.000 \u2248 1.250 DM)",
                "Wajib cantumkan Username kamu di kolom pesan dukungan, biar bisa dicocokkan",
                "Saldo DM otomatis masuk ke akun kamu dalam beberapa menit setelah donasi terverifikasi"
            ).forEachIndexed { i, step ->
                StepRow(index = i, text = step)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://trakteer.id/Dayynimee")))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF))
            ) {
                Text("Buka Trakteer buat Top-up", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Kalau saldo belum masuk lebih dari 15 menit, hubungi admin di Chat Room.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun HeroBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotate"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sparkle"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF241530), Color(0xFF16414D))),
                RoundedCornerShape(22.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopStart)
                .offset(x = (-40).dp, y = (-40).dp)
                .background(Color(0xFF7B2FBF).copy(alpha = glowAlpha), CircleShape)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF5FC9DE),
                modifier = Modifier.size(46.dp).rotate(rotation)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Top-up Diamond (DM)", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
            Text("Dipakai buat bikin & kontribusi Clan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun StepRow(index: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF7B2FBF), Color(0xFF7B2FBF))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

