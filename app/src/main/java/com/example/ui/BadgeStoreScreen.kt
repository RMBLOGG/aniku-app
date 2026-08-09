package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.network.AnikuViewModel
import com.example.network.BadgeStoreItemDto

private fun parseHex(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeStoreScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val catalog by viewModel.badgeCatalog.collectAsState()
    val owned by viewModel.myOwnedBadges.collectAsState()
    val equippedId by viewModel.equippedBadgeId.collectAsState()
    val diamondBalance by viewModel.diamondBalance.collectAsState()

    var busyBadgeId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadBadgeCatalog()
        viewModel.loadMyOwnedBadges()
        viewModel.loadEquippedBadgesPublic()
    }

    val ownedIds = remember(owned) { owned.map { it.badge_id }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badge Store") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Diamond,
                            contentDescription = null,
                            tint = Color(0xFF4FD1C5),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$diamondBalance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (catalog.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(catalog) { item ->
                BadgeStoreCard(
                    item = item,
                    isOwned = ownedIds.contains(item.id),
                    isEquipped = equippedId == item.id,
                    isBusy = busyBadgeId == item.id,
                    diamondBalance = diamondBalance,
                    onBuy = {
                        busyBadgeId = item.id
                        viewModel.buyBadge(item.id) { result, error ->
                            busyBadgeId = null
                            if (result != null) {
                                Toast.makeText(context, "Badge \"${result.label}\" berhasil dibeli!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error ?: "Gagal beli badge", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEquip = {
                        busyBadgeId = item.id
                        val target = if (equippedId == item.id) null else item.id
                        viewModel.equipBadge(target) { success, error ->
                            busyBadgeId = null
                            if (!success) {
                                Toast.makeText(context, error ?: "Gagal pakai badge", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BadgeStoreCard(
    item: BadgeStoreItemDto,
    isOwned: Boolean,
    isEquipped: Boolean,
    isBusy: Boolean,
    diamondBalance: Int,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    val bg = parseHex(item.background_color, Color(0xFF8A4FD6))
    val textColor = parseHex(item.text_color, Color.White)
    val canAfford = diamondBalance >= item.price_diamond

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1F2A))
            .padding(14.dp)
    ) {
        // Preview badge - bentuk beneran sesuai shape-nya, bukan cuma warna
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (item.shape) {
                "pennant" -> PennantBadge(text = item.label, backgroundColor = bg, textColor = textColor)
                else -> RibbonBadge(text = item.label, backgroundColor = bg, textColor = textColor)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Icon(
                Icons.Default.Diamond,
                contentDescription = null,
                tint = Color(0xFF4FD1C5),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${item.price_diamond}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCFD8DC)
            )
        }

        when {
            isEquipped -> {
                Button(
                    onClick = onEquip,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F3B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isBusy) "..." else "Terpakai • Lepas")
                }
            }
            isOwned -> {
                Button(
                    onClick = onEquip,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FD1C5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isBusy) "..." else "Pakai", color = Color.Black)
                }
            }
            else -> {
                Button(
                    onClick = onBuy,
                    enabled = !isBusy && canAfford,
                    colors = ButtonDefaults.buttonColors(containerColor = bg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            isBusy -> "..."
                            !canAfford -> "DM kurang"
                            else -> "Beli"
                        },
                        color = textColor
                    )
                }
            }
        }
    }
}
