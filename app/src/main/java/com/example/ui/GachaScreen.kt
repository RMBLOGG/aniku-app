package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.GachaRollResult
import com.example.network.UserCharacterEntry
import kotlinx.coroutines.delay

// Warna per rarity - dipake bareng di kartu reveal & grid koleksi biar konsisten
private fun rarityColor(rarity: String): Color = when (rarity) {
    "Mythic" -> Color(0xFFFF3D6E)
    "Legendary" -> Color(0xFFFFC93C)
    "Epic" -> Color(0xFFB16CEA)
    "Rare" -> Color(0xFF4FD8E8)
    else -> Color(0xFF9E9E9E) // Common
}

private fun rarityGradient(rarity: String): Brush = when (rarity) {
    "Mythic" -> Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFFFF8A3D)))
    "Legendary" -> Brush.linearGradient(listOf(Color(0xFFFFC93C), Color(0xFFFF8A00)))
    "Epic" -> Brush.linearGradient(listOf(Color(0xFFB16CEA), Color(0xFF6C63FF)))
    "Rare" -> Brush.linearGradient(listOf(Color(0xFF4FD8E8), Color(0xFF3D7EFF)))
    else -> Brush.linearGradient(listOf(Color(0xFF9E9E9E), Color(0xFF6E6E6E)))
}

private const val GACHA_COST_SINGLE = 50
private const val GACHA_COST_MULTI = 300 // x6, sedikit lebih murah per-tarikan dibanding satuan
private const val GACHA_MULTI_COUNT = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GachaScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onTopUpClick: () -> Unit
) {
    val diamondBalance by viewModel.diamondBalance.collectAsState()
    val collection by viewModel.gachaCollection.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Gacha, 1 = Koleksi
    var isRolling by remember { mutableStateOf(false) }
    var revealResults by remember { mutableStateOf<List<GachaRollResult>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
        viewModel.loadGachaCollection()
    }

    fun doRoll(times: Int, costPerRoll: Int) {
        if (isRolling) return
        isRolling = true
        errorMessage = null
        if (times == 1) {
            viewModel.rollGacha(costPerRoll) { result, error ->
                isRolling = false
                if (result != null) {
                    revealResults = listOf(result)
                    viewModel.refreshProfile()
                    viewModel.loadGachaCollection()
                } else {
                    errorMessage = error
                }
            }
        } else {
            viewModel.rollGachaMulti(times, costPerRoll) { results, error ->
                isRolling = false
                if (results.isNotEmpty()) {
                    revealResults = results
                }
                if (error != null) {
                    errorMessage = error
                }
                viewModel.refreshProfile()
                viewModel.loadGachaCollection()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0E0E13),
        topBar = {
            TopAppBar(
                title = { Text("Gacha Karakter", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E0E13))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Kartu saldo DM ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF4FD8E8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$diamondBalance DM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                TextButton(onClick = onTopUpClick) {
                    Text("Isi DM", color = Color(0xFF4FD8E8), fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Tab Gacha / Koleksi ──
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Gacha") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Koleksi (${collection.size})") })
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2A1B4D), Color(0xFF1B2E4D)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF4FD8E8).copy(alpha = 0.5f), modifier = Modifier.size(72.dp))
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    if (errorMessage != null) {
                        Text(errorMessage ?: "", color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = { doRoll(1, GACHA_COST_SINGLE) },
                        enabled = !isRolling,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FD8E8)),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (isRolling) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Gacha x1  •  $GACHA_COST_SINGLE DM", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { doRoll(GACHA_MULTI_COUNT, GACHA_COST_MULTI / GACHA_MULTI_COUNT) },
                        enabled = !isRolling,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC93C).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFC93C)),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Gacha x$GACHA_MULTI_COUNT  •  $GACHA_COST_MULTI DM", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Peluang: Mythic 1% • Legendary 4% • Epic 12% • Rare 28% • Common 55%",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                KoleksiGrid(collection)
            }
        }
    }

    // ── Overlay reveal hasil gacha ──
    revealResults?.let { results ->
        GachaRevealDialog(results = results, onDismiss = { revealResults = null })
    }
}

@Composable
private fun KoleksiGrid(collection: List<UserCharacterEntry>) {
    if (collection.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada karakter. Coba gacha dulu!", color = Color.White.copy(alpha = 0.5f))
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(collection) { entry ->
            val char = entry.characters ?: return@items
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, rarityColor(char.rarity).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = char.image_url,
                        contentDescription = char.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (entry.count > 1) {
                        Text(
                            "x${entry.count}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    char.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    char.rarity,
                    color = rarityColor(char.rarity),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GachaRevealDialog(results: List<GachaRollResult>, onDismiss: () -> Unit) {
    var revealedCount by remember { mutableStateOf(0) }

    LaunchedEffect(results) {
        revealedCount = 0
        for (i in results.indices) {
            delay(if (i == 0) 300L else 450L)
            revealedCount = i + 1
        }
    }

    Dialog(onDismissRequest = { if (revealedCount >= results.size) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF16161D))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (results.size == 1) "Karakter Didapat!" else "Hasil Gacha x${results.size}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (revealedCount >= results.size) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (results.size == 1) {
                AnimatedVisibility(visible = revealedCount >= 1, enter = scaleIn() + fadeIn()) {
                    GachaResultCard(results[0], big = true)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results.size) { idx ->
                        AnimatedVisibility(visible = idx < revealedCount, enter = scaleIn() + fadeIn()) {
                            GachaResultCard(results[idx], big = false)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (revealedCount >= results.size) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FD8E8))
                ) {
                    Text("Tutup", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "Membuka...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun GachaResultCard(result: GachaRollResult, big: Boolean) {
    val width = if (big) 200.dp else 120.dp
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .background(rarityGradient(result.rarity))
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16161D))
        ) {
            AsyncImage(
                model = result.image_url,
                contentDescription = result.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (result.is_new) {
                Text(
                    "BARU",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF3D6E))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                result.rarity.uppercase(),
                color = Color.White,
                fontSize = if (big) 13.sp else 10.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                result.name,
                color = Color.White,
                fontSize = if (big) 14.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            result.anime_title?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = if (big) 11.sp else 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}
