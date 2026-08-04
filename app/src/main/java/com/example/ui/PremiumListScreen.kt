package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AnikuViewModel
import com.example.network.PremiumPackageDto

// Halaman khusus daftar paket Premium (dipisah dari GiftPremiumSheet yang tadinya
// cuma bottom sheet) -- biar user bisa liat semua paket + bonus Diamond dengan
// tenang sebelum milih, konsisten sama gaya DiamondTopUpScreen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumListScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val packages by viewModel.premiumPackages.collectAsState()
    val session by viewModel.session.collectAsState()
    var chosenPackageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (packages.isEmpty()) viewModel.loadPremiumPackages()
        viewModel.refreshProfile()
    }

    val activePackages = packages.filter { it.is_active != false }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Aniku Premium", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
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
            if (session.isPremiumActive()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Premium kamu masih aktif — beli paket lagi buat nambah durasi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (activePackages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                activePackages.forEach { pkg ->
                    PremiumPackageCard(pkg = pkg, onChoose = { chosenPackageId = pkg.id })
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }

    if (chosenPackageId != null && session.userId != null) {
        GiftPremiumSheet(
            targetUserId = session.userId!!,
            targetUsername = session.username ?: "",
            viewModel = viewModel,
            selfMode = true,
            preselectedPackageId = chosenPackageId,
            onDismiss = { chosenPackageId = null }
        )
    }
}

@Composable
private fun PremiumPackageCard(pkg: PremiumPackageDto, onChoose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pkg.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${pkg.duration_days} hari premium",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                val bonus = pkg.bonus_diamond ?: 0
                if (bonus > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF4CD964),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Bonus Diamond: $bonus",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CD964)
                        )
                    }
                }
            }
            Text(
                "Rp${"%,d".format(pkg.price).replace(",", ".")}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4FC3F7)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onChoose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB800)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.6f))
        ) {
            Text("Pilih Paket", fontWeight = FontWeight.Bold)
        }
    }
}
