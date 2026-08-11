package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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

private fun tierFromString(tier: String): BadgeTier = when (tier) {
    "holo" -> BadgeTier.HOLO
    "neon" -> BadgeTier.NEON
    else -> BadgeTier.STANDARD
}

private fun shapeFromString(shape: String) =
    if (shape == "pennant") PennantBadgeShape() else RibbonBadgeShape()

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
    val equippedSkinId by viewModel.equippedBadgeSkinId.collectAsState()
    val diamondBalance by viewModel.diamondBalance.collectAsState()

    // busy key = "clanId:skinId" biar loading indicator-nya spesifik per kartu
    var busyKey by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadBadgeCatalog()
        viewModel.loadMyOwnedBadges()
        viewModel.loadEquippedBadgesPublic()
        isLoading = false
    }

    val ownedKeys = remember(owned) { owned.map { "${it.clan_id}:${it.skin_id}" }.toSet() }

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

        // Kelompokin per clan biar ada judul "AniKu Family" dsb di atas 6 varian desainnya
        val grouped = remember(catalog) { catalog.groupBy { Triple(it.clan_id, it.name, it.tag) } }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (clanInfo, skins) ->
                val (_, clanName, clanTag) = clanInfo
                item(span = { GridItemSpan(2) }) {
                    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                        Text(
                            text = "$clanName ($clanTag)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Pilih salah satu desain buat tag clan kamu",
                            fontSize = 11.sp,
                            color = Color(0xFF9AA3AF)
                        )
                    }
                }
                items(skins) { item ->
                    val key = "${item.clan_id}:${item.skin_id}"
                    BadgeStoreCard(
                        item = item,
                        isOwned = ownedKeys.contains(key),
                        isEquipped = equippedClanId == item.clan_id && equippedSkinId == item.skin_id,
                        isBusy = busyKey == key,
                        diamondBalance = diamondBalance,
                        onBuy = {
                            busyKey = key
                            viewModel.buyBadge(item.clan_id, item.skin_id) { result, error ->
                                busyKey = null
                                if (result != null) {
                                    Toast.makeText(context, "Desain \"${item.skin_name}\" berhasil dibeli!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, error ?: "Gagal beli badge", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onEquip = {
                            busyKey = key
                            val alreadyEquipped = equippedClanId == item.clan_id && equippedSkinId == item.skin_id
                            val targetClan = if (alreadyEquipped) null else item.clan_id
                            val targetSkin = if (alreadyEquipped) null else item.skin_id
                            viewModel.equipBadge(targetClan, targetSkin) { success, error ->
                                busyKey = null
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
    val skinStyle = badgeSkinStyle(item.skin_id)
    val bg = skinStyle.baseColor
    val canAfford = diamondBalance >= item.badge_price_diamond
    val tier = tierFromString(item.badge_tier)

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
                tier = tier,
                shape = shapeFromString(item.badge_shape)
            )
        }

        Text(
            text = item.skin_name,
            fontSize = 11.sp,
            color = Color(0xFF9AA3AF),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

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
