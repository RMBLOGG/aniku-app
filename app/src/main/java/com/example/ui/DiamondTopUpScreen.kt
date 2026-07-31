package com.example.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AnikuViewModel
import com.example.network.SakurupiahDiamondInvoiceResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiamondTopUpScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var amountInput by remember { mutableStateOf("") }
    var isCreatingInvoice by remember { mutableStateOf(false) }
    var invoiceError by remember { mutableStateOf<String?>(null) }
    var invoiceResult by remember { mutableStateOf<SakurupiahDiamondInvoiceResponse?>(null) }
    var showInvoiceSheet by remember { mutableStateOf(false) }

    val amountValue = amountInput.toIntOrNull() ?: 0
    val estimatedDiamond = amountValue / 4

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
            Text("Masukin Nominal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = amountInput,
                onValueChange = { new ->
                    if (new.length <= 8 && new.all { it.isDigit() }) amountInput = new
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Contoh: 10000") },
                prefix = { Text("Rp") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5000, 20000, 50000).forEach { preset ->
                    OutlinedButton(
                        onClick = { amountInput = preset.toString() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Rp${preset / 1000}rb", fontSize = 12.sp)
                    }
                }
            }

            if (amountValue > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2FA8BF).copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kamu akan dapat", fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$estimatedDiamond DM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            invoiceError?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    invoiceError = null
                    if (amountValue < 5000) {
                        invoiceError = "Nominal minimal Rp5.000"
                        return@Button
                    }
                    isCreatingInvoice = true
                    viewModel.createSakurupiahDiamondInvoice(amountValue) { result, error ->
                        isCreatingInvoice = false
                        if (result != null) {
                            invoiceResult = result
                            showInvoiceSheet = true
                        } else {
                            invoiceError = error ?: "Gagal membuat invoice pembayaran"
                        }
                    }
                },
                enabled = amountValue >= 5000 && !isCreatingInvoice,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF))
            ) {
                if (isCreatingInvoice) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bayar dengan QRIS", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Diamond otomatis masuk ke akun kamu setelah pembayaran terverifikasi. Kalau belum masuk lebih dari 15 menit, hubungi admin di Chat Room.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showInvoiceSheet && invoiceResult != null) {
        DiamondInvoiceSheet(
            invoice = invoiceResult!!,
            onDismiss = {
                showInvoiceSheet = false
                invoiceResult = null
                amountInput = ""
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiamondInvoiceSheet(
    invoice: SakurupiahDiamondInvoiceResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Invoice Top-up Diamond Dibuat!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Selesaikan pembayaran QRIS buat top-up ${invoice.diamond_amount ?: 0} DM. Diamond otomatis masuk setelah pembayaran terverifikasi.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val url = invoice.checkout_url
                    if (!url.isNullOrBlank()) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Log.e("DiamondTopUpScreen", "Failed to open checkout url", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Bayar Sekarang")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            ) {
                Text("Tutup", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
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

