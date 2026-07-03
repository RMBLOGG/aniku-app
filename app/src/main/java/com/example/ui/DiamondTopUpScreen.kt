package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                    title = { Text("Top-up Diamond", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeroBanner()

            Spacer(modifier = Modifier.height(20.dp))
            Text("Buat Apa Aja DM Ini?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BenefitCard(icon = Icons.Default.Groups, title = "Buat Clan", subtitle = "2.000 DM", modifier = Modifier.weight(1f))
                BenefitCard(icon = Icons.Default.TrendingUp, title = "Kontribusi", subtitle = "Naikin level", modifier = Modifier.weight(1f))
                BenefitCard(icon = Icons.Default.EmojiEvents, title = "Puncak Board", subtitle = "Jadi #1", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text("Estimasi Konversi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(vertical = 4.dp)
            ) {
                listOf("Rp5.000" to "1.250 DM", "Rp20.000" to "5.000 DM", "Rp50.000" to "12.500 DM").forEachIndexed { i, (rp, dm) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(rp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(dm, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text("Cara Top-up", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            listOf(
                "Donasi lewat Trakteer dengan nominal berapa pun (rasio Rp4 = 1 DM)",
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF))
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buka Trakteer buat Top-up", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Kalau saldo belum masuk lebih dari 15 menit, hubungi admin di Chat Room.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
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
        Box(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 30.dp)
                .background(Color(0xFF2FA8BF).copy(alpha = glowAlpha * 0.6f), CircleShape)
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
private fun BenefitCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF241530), Color(0xFF16414D))))
            .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2FA8BF).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(18.dp)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun StepRow(index: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFF7B2FBF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
