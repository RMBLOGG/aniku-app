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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.network.AnikuViewModel
import com.example.network.ClanBadgeCatalogDto

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
    val equippedClanId by viewModel.equippedBadgeClanId.collectAsState()
    val diamondBalance by viewModel.diamondBalance.collectAsState()

    var busyClanId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadBadgeCatalog()
        viewModel.loadMyOwnedBadges()
        viewModel.loadEquippedBadgesPublic()
        isLoading = false
    }

    val ownedIds = remember(owned) { owned.map { it.clan_id }.toSet() }

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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (catalog.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Kamu belum join clan. Join clan dulu buat bisa beli badge tag clan kamu sendiri.",
                    textAlign = TextAlign.Center,
                    color = Color(0xFF9AA3AF),
                    fontSize = 14.sp
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Tag badge clan kamu sendiri - beli buat dipajang di chat",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(catalog) { item ->
                    BadgeStoreCard(
                        item = item,
                        isOwned = ownedIds.contains(item.clan_id),
                        isEquipped = equippedClanId == item.clan_id,
                        isBusy = busyClanId == item.clan_id,
                        diamondBalance = diamondBalance,
                        onBuy = {
                            busyClanId = item.clan_id
                            viewModel.buyBadge(item.clan_id) { result, error ->
                                busyClanId = null
                                if (result != null) {
                                    Toast.makeText(context, "Badge tag \"${result.tag}\" berhasil dibeli!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, error ?: "Gagal beli badge", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onEquip = {
                            busyClanId = item.clan_id
                            val target = if (equippedClanId == item.clan_id) null else item.clan_id
                            viewModel.equipBadge(target) { success, error ->
                                busyClanId = null
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
}

@Composable
private fun BadgeStoreCard(
    item: ClanBadgeCatalogDto,
    isOwned: Boolean,
    isEquipped: Boolean,
    isBusy: Boolean,
    diamondBalance: Int,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    val bg = parseHex(item.badge_color, Color(0xFF8A4FD6))
    val canAfford = diamondBalance >= item.badge_price_diamond

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1F2A))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            FuturisticBadge(
                text = item.tag,
                baseColor = bg,
                tier = badgeTierForPrice(item.badge_price_diamond)
            )
        }

        Text(
            text = item.name,
            fontSize = 11.sp,
            color = Color(0xFF9AA3AF),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        val tier = badgeTierForPrice(item.badge_price_diamond)
        if (tier != BadgeTier.STANDARD) {
            Text(
                text = if (tier == BadgeTier.NEON) "✦ NEON TIER" else "✦ HOLO TIER",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = bg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
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
                text = "${item.badge_price_diamond}",
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
                        color = Color.White
                    )
                }
            }
        }
    }
}
