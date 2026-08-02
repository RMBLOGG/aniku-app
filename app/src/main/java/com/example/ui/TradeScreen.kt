package com.example.ui

// Layar Pasar/Trade kartu gacha. Reuse helper internal dari GachaScreen.kt
// (rarityColor, RarityBadge, rememberGlossyShine, glossyBorder, rarityShimmerColors,
// isPremiumRarity) karena satu package - tapi PALET WARNA dibikin sendiri
// (TradeNeon) soalnya object "Neon" di GachaScreen.kt di-private-in ke file itu.

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.BuyTradeListingResult
import com.example.network.MyTradeListing
import com.example.network.TradeMarketListing
import com.example.network.UserCharacterEntry
import kotlin.random.Random

// Palet warna sendiri (nilai sama kayak "Neon" di GachaScreen.kt) - gak bisa
// reuse langsung soalnya Neon di GachaScreen.kt dideklarasiin "private object",
// jadi cuma keliatan di file itu sendiri walau satu package.
private object TradeNeon {
    val Cyan = Color(0xFF3DF4FF)
    val Magenta = Color(0xFFFF3DA6)
    val Purple = Color(0xFF9D4EFF)
    val Gold = Color(0xFFFFD23F)
    val Green = Color(0xFF3DFFA6)
    val Void = Color(0xFF05050B)
    val Deep1 = Color(0xFF0A0A16)
    val Deep2 = Color(0xFF130E24)
    val CardBase = Color(0xFF14141F)
    val Glass = Color(0x1AFFFFFF)
}

private enum class TradeTab { PASAR, JUAL_LISTING_SAYA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(TradeTab.PASAR) }

    val market by viewModel.tradeMarket.collectAsState()
    val myListings by viewModel.myTradeListings.collectAsState()
    val myCollection by viewModel.gachaCollection.collectAsState()

    var sellSheetOpen by remember { mutableStateOf(false) }
    var buyConfirmListing by remember { mutableStateOf<TradeMarketListing?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Pencarian & filter rarity buat tab Pasar
    var marketSearchQuery by remember { mutableStateOf("") }
    var marketRarityFilter by remember { mutableStateOf<String?>(null) } // null = semua rarity
    val filteredMarket = remember(market, marketSearchQuery, marketRarityFilter) {
        market.filter { listing ->
            val matchesQuery = marketSearchQuery.isBlank() ||
                (listing.character_name ?: "").contains(marketSearchQuery, ignoreCase = true)
            val matchesRarity = marketRarityFilter == null || listing.rarity == marketRarityFilter
            matchesQuery && matchesRarity
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadTradeMarket()
        viewModel.loadMyTradeListings()
        viewModel.loadGachaCollection()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TradeBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { sellSheetOpen = true },
                    containerColor = TradeNeon.Cyan,
                    contentColor = Color.Black,
                    icon = { Icon(Icons.Default.Sell, contentDescription = null) },
                    text = { Text("Jual Kartu", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                TradeHeader(onBack = onBack)

                MarketStatsRow(
                    activeListingCount = market.size,
                    myActiveCount = myListings.count { it.status == "active" }
                )

                Spacer(Modifier.height(4.dp))

                TradePillTabs(
                    selected = tab,
                    marketCount = market.size,
                    myCount = myListings.size,
                    onSelect = { tab = it }
                )

                Spacer(Modifier.height(8.dp))

                Box(Modifier.weight(1f)) {
                    when (tab) {
                        TradeTab.PASAR -> MarketGrid(
                            listings = filteredMarket,
                            isMarketEmpty = market.isEmpty(),
                            searchQuery = marketSearchQuery,
                            onSearchQueryChange = { marketSearchQuery = it },
                            selectedRarityFilter = marketRarityFilter,
                            onRarityFilterChange = { marketRarityFilter = it },
                            onBuyClick = { buyConfirmListing = it },
                            onSellClick = { sellSheetOpen = true }
                        )
                        TradeTab.JUAL_LISTING_SAYA -> MyListingsList(
                            listings = myListings,
                            busy = busy,
                            onCancel = { listing ->
                                busy = true
                                viewModel.cancelTradeListing(listing.id) { _, err ->
                                    busy = false
                                    Toast.makeText(context, err ?: "Listing dibatalin", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSellClick = { sellSheetOpen = true }
                        )
                    }
                }
            }
        }
    }

    if (sellSheetOpen) {
        SellCardSheet(
            collection = myCollection,
            activeListingCharIds = myListings.filter { it.status == "active" }.map { it.character_mal_id }.toSet(),
            busy = busy,
            onDismiss = { sellSheetOpen = false },
            onConfirmSell = { characterMalId, price ->
                busy = true
                viewModel.createTradeListing(characterMalId, price) { _, err ->
                    busy = false
                    if (err == null) {
                        Toast.makeText(context, "Kartu dipasang di pasar!", Toast.LENGTH_SHORT).show()
                        sellSheetOpen = false
                    } else {
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    buyConfirmListing?.let { listing ->
        BuyConfirmDialog(
            listing = listing,
            busy = busy,
            onDismiss = { buyConfirmListing = null },
            onConfirm = {
                busy = true
                viewModel.buyTradeListing(listing.listing_id) { result: BuyTradeListingResult?, err ->
                    busy = false
                    buyConfirmListing = null
                    val msg = if (err != null) err else "Berhasil beli ${result?.character_name}!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ============================================================================
//  BACKDROP - senada sama GachaScreen (grid garis diagonal + partikel drift)
//  tapi lebih tenang/gelap biar konten kartu pasar yang jadi fokus.
// ============================================================================
@Composable
private fun TradeBackdrop() {
    val particles = remember {
        List(16) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2f + 1f) }
    }
    val infinite = rememberInfiniteTransition(label = "tradeBackdrop")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "drift"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TradeNeon.Deep2, TradeNeon.Void)))
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-60).dp)
                .blur(90.dp)
                .background(TradeNeon.Cyan.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-50).dp, y = 50.dp)
                .blur(90.dp)
                .background(TradeNeon.Purple.copy(alpha = 0.16f), CircleShape)
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.02f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            particles.forEach { (px, py, r) ->
                val yy = ((py - drift + 1f) % 1f) * size.height
                drawCircle(
                    color = if (px > 0.6f) TradeNeon.Magenta.copy(alpha = 0.28f) else TradeNeon.Cyan.copy(alpha = 0.22f),
                    radius = r.dp.toPx(),
                    center = Offset(px * size.width, yy)
                )
            }
        }
    }
}

@Composable
private fun TradeHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("Pasar Kartu", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text(
                "Jual-beli koleksi antar sesama pemain",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(TradeNeon.Cyan.copy(alpha = 0.12f))
                .border(1.dp, TradeNeon.Cyan.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = TradeNeon.Cyan, modifier = Modifier.size(19.dp))
        }
    }
}

// Baris kecil "denyut pasar" - signature element layar ini: nunjukin langsung
// berapa banyak kartu lagi aktif dijual & berapa punya kamu sendiri, tanpa
// perlu pindah tab dulu buat tau.
@Composable
private fun MarketStatsRow(activeListingCount: Int, myActiveCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip(
            icon = Icons.Default.Storefront,
            label = "Aktif dijual",
            value = activeListingCount.toString(),
            color = TradeNeon.Cyan,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            icon = Icons.Default.Sell,
            label = "Listing kamu",
            value = myActiveCount.toString(),
            color = TradeNeon.Gold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TradeNeon.Glass)
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

// Tab pill custom (bukan TabRow default Material yang flat & bergaris bawah doang)
@Composable
private fun TradePillTabs(
    selected: TradeTab,
    marketCount: Int,
    myCount: Int,
    onSelect: (TradeTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TradeNeon.CardBase)
            .padding(4.dp)
    ) {
        PillTabItem(
            label = "Pasar",
            count = marketCount,
            selected = selected == TradeTab.PASAR,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TradeTab.PASAR) }
        )
        PillTabItem(
            label = "Listing Saya",
            count = myCount,
            selected = selected == TradeTab.JUAL_LISTING_SAYA,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TradeTab.JUAL_LISTING_SAYA) }
        )
    }
}

@Composable
private fun PillTabItem(label: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) TradeNeon.Cyan else Color.Transparent
    val fg = if (selected) Color.Black else Color.White.copy(alpha = 0.65f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$label ($count)", color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
    }
}

// ============================================================================
//  TAB: PASAR
// ============================================================================
@Composable
private fun MarketGrid(
    listings: List<TradeMarketListing>,
    isMarketEmpty: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedRarityFilter: String?,
    onRarityFilterChange: (String?) -> Unit,
    onBuyClick: (TradeMarketListing) -> Unit,
    onSellClick: () -> Unit
) {
    if (isMarketEmpty) {
        EmptyState(
            icon = Icons.Default.Storefront,
            title = "Pasar masih sepi",
            subtitle = "Belum ada yang jual kartu. Jadi yang pertama pasang kartu di sini!",
            ctaLabel = "Pasang Kartu Pertama",
            onCtaClick = onSellClick
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Cari karakter...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TradeNeon.Cyan.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = TradeNeon.Glass,
                unfocusedContainerColor = TradeNeon.Glass,
                cursorColor = TradeNeon.Cyan
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val rarityFilters = listOf(null, "Common", "Rare", "Epic", "Legendary", "Mythic")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rarityFilters) { rarity -> RarityFilterChip(rarity, selectedRarityFilter, onRarityFilterChange) }
        }

        Spacer(Modifier.height(6.dp))

        if (listings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Gak ketemu kartu yang cocok.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(listings, key = { it.listing_id }) { listing ->
                    MarketCard(listing, onClick = { onBuyClick(listing) })
                }
            }
        }
    }
}

// Chip filter rarity - dipakai ulang di tab Pasar & di sheet Jual Kartu biar
// konsisten (satu "bahasa visual" buat filter di seluruh layar Pasar).
@Composable
private fun RarityFilterChip(rarity: String?, selected: String?, onSelect: (String?) -> Unit) {
    val isSelected = selected == rarity
    val chipColor = if (rarity == null) TradeNeon.Cyan else rarityColor(rarity)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) chipColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (isSelected) chipColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            )
            .clickable { onSelect(rarity) }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (rarity != null) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(chipColor))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            rarity ?: "Semua",
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MarketCard(listing: TradeMarketListing, onClick: () -> Unit) {
    val rColor = rarityColor(listing.rarity)
    val premium = isPremiumRarity(listing.rarity)
    val shine = if (premium) rememberGlossyShine(2200) else 0f

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TradeNeon.CardBase)
            .then(
                if (premium)
                    Modifier.glossyBorder(rarityShimmerColors(listing.rarity), shine, 18.dp)
                else
                    Modifier.border(1.dp, rColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            )
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = listing.character_image_url,
                contentDescription = listing.character_name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 60f
                        )
                    )
            )
            RarityBadge(rarity = listing.rarity, compact = true, modifier = Modifier.padding(8.dp))

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, TradeNeon.Cyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Diamond, contentDescription = null, tint = TradeNeon.Cyan, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("${listing.price_dm}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                listing.character_name ?: "?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(TradeNeon.Purple.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (listing.seller_avatar_url != null) {
                        AsyncImage(
                            model = listing.seller_avatar_url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    listing.seller_username ?: "?",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ============================================================================
//  TAB: LISTING SAYA
// ============================================================================
@Composable
private fun MyListingsList(
    listings: List<MyTradeListing>,
    busy: Boolean,
    onCancel: (MyTradeListing) -> Unit,
    onSellClick: () -> Unit
) {
    if (listings.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Sell,
            title = "Belum pernah jualan",
            subtitle = "Kartu duplikat numpuk di koleksi? Jual aja, tuker jadi DM.",
            ctaLabel = "Jual Kartu Sekarang",
            onCtaClick = onSellClick
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(listings, key = { it.id }) { listing ->
            MyListingCard(listing, busy = busy, onCancel = { onCancel(listing) })
        }
    }
}

@Composable
private fun MyListingCard(listing: MyTradeListing, busy: Boolean, onCancel: () -> Unit) {
    val char = listing.characters
    val statusColor = when (listing.status) {
        "active" -> TradeNeon.Cyan
        "sold" -> TradeNeon.Green
        else -> Color.White.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TradeNeon.CardBase)
            .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = char?.image_url,
                contentDescription = char?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            if (char != null) {
                RarityBadge(
                    rarity = char.rarity,
                    compact = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(char?.name ?: "?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Diamond, contentDescription = null, tint = TradeNeon.Cyan, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text("${listing.price_dm} DM", color = TradeNeon.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                StatusPill(status = listing.status, color = statusColor)
            }
        }
        if (listing.status == "active") {
            OutlinedButton(
                onClick = onCancel,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Batalin", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, color: Color) {
    val label = when (status) {
        "active" -> "Aktif"
        "sold" -> "Terjual"
        "cancelled" -> "Dibatalin"
        else -> status
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================================
//  EMPTY STATE
// ============================================================================
@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(TradeNeon.Glass)
                .border(1.dp, TradeNeon.Cyan.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TradeNeon.Cyan.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCtaClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TradeNeon.Cyan)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(ctaLabel, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

// ============================================================================
//  SHEET: JUAL KARTU
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellCardSheet(
    collection: List<UserCharacterEntry>,
    activeListingCharIds: Set<Int>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirmSell: (characterMalId: Int, price: Int) -> Unit
) {
    var selected by remember { mutableStateOf<UserCharacterEntry?>(null) }
    var priceInput by remember { mutableStateOf("") }
    val price = priceInput.toIntOrNull() ?: 0
    val fee = if (price >= 10) maxOf(1, price / 10) else 0
    val net = price - fee

    // Pencarian & filter rarity buat nyari kartu di koleksi - pola sama kayak tab Pasar
    var searchQuery by remember { mutableStateOf("") }
    var sellRarityFilter by remember { mutableStateOf<String?>(null) }
    val filteredCollection = remember(collection, searchQuery, sellRarityFilter) {
        collection.filter { entry ->
            val char = entry.characters ?: return@filter false
            val matchesQuery = searchQuery.isBlank() || char.name.contains(searchQuery, ignoreCase = true)
            val matchesRarity = sellRarityFilter == null || char.rarity == sellRarityFilter
            matchesQuery && matchesRarity
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TradeNeon.Deep1) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = TradeNeon.Cyan)
                Spacer(Modifier.width(8.dp))
                Text("Jual Kartu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White) }
            }
            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(visible = selected == null, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Text("Pilih kartu dari koleksi kamu", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    if (collection.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Koleksi kamu masih kosong", color = Color.White.copy(alpha = 0.4f))
                        }
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari karakter...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TradeNeon.Cyan.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = TradeNeon.Glass,
                                unfocusedContainerColor = TradeNeon.Glass,
                                cursorColor = TradeNeon.Cyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        val rarityFilters = listOf(null, "Common", "Rare", "Epic", "Legendary", "Mythic")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rarityFilters) { rarity -> RarityFilterChip(rarity, sellRarityFilter) { sellRarityFilter = it } }
                        }
                        Spacer(Modifier.height(10.dp))

                        if (filteredCollection.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Text("Gak ketemu karakter yang cocok.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.heightIn(max = 340.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredCollection, key = { it.characters?.mal_id ?: it.hashCode() }) { entry ->
                                    val mal = entry.characters?.mal_id
                                    if (mal != null) {
                                        SellPickCard(
                                            entry = entry,
                                            alreadyListed = mal in activeListingCharIds,
                                            onClick = { selected = entry }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selected != null, enter = fadeIn(), exit = fadeOut()) {
                val entry = selected
                if (entry != null) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(TradeNeon.CardBase)
                                .padding(10.dp)
                        ) {
                            AsyncImage(
                                model = entry.characters?.image_url,
                                contentDescription = entry.characters?.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.characters?.name ?: "?", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                RarityBadge(rarity = entry.characters?.rarity ?: "Common", compact = true)
                            }
                            TextButton(onClick = { selected = null; priceInput = "" }) {
                                Text("Ganti", color = TradeNeon.Cyan, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = priceInput,
                            onValueChange = { input -> priceInput = input.filter { it.isDigit() }.take(7) },
                            label = { Text("Harga jual (DM)") },
                            leadingIcon = { Icon(Icons.Default.Diamond, contentDescription = null, tint = TradeNeon.Cyan) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TradeNeon.Cyan,
                                focusedLabelColor = TradeNeon.Cyan,
                                cursorColor = TradeNeon.Cyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TradeNeon.Glass)
                                .padding(12.dp)
                        ) {
                            PriceBreakdownRow("Harga jual", "$price DM", Color.White.copy(alpha = 0.7f))
                            PriceBreakdownRow("Fee pasar (10%)", "-$fee DM", Color(0xFFFF6B6B))
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(Modifier.height(4.dp))
                            PriceBreakdownRow("Kamu terima", "$net DM", TradeNeon.Green, bold = true)
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val mal = entry.characters?.mal_id ?: return@Button
                                onConfirmSell(mal, price)
                            },
                            enabled = !busy && price >= 10,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TradeNeon.Cyan, disabledContainerColor = TradeNeon.Cyan.copy(alpha = 0.3f))
                        ) {
                            Text(if (busy) "Memproses..." else "Pasang di Pasar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceBreakdownRow(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SellPickCard(entry: UserCharacterEntry, alreadyListed: Boolean, onClick: () -> Unit) {
    val rColor = rarityColor(entry.characters?.rarity ?: "Common")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TradeNeon.CardBase)
            .border(1.dp, if (alreadyListed) Color.White.copy(alpha = 0.08f) else rColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .then(if (alreadyListed) Modifier else Modifier.clickable(onClick = onClick))
            .padding(5.dp)
    ) {
        AsyncImage(
            model = entry.characters?.image_url,
            contentDescription = entry.characters?.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(9.dp))
                .then(if (alreadyListed) Modifier.background(Color.Black.copy(alpha = 0.5f)) else Modifier)
        )
        Spacer(Modifier.height(3.dp))
        if (alreadyListed) {
            Text("Lagi dijual", color = TradeNeon.Cyan.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Text("x${entry.count}", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ============================================================================
//  DIALOG: KONFIRMASI BELI
// ============================================================================
@Composable
private fun BuyConfirmDialog(
    listing: TradeMarketListing,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(TradeNeon.Deep1)
                .border(1.dp, TradeNeon.Cyan.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = listing.character_image_url,
                    contentDescription = listing.character_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(listing.character_name ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    RarityBadge(rarity = listing.rarity, compact = true)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TradeNeon.Glass)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Harga", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Diamond, contentDescription = null, tint = TradeNeon.Cyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${listing.price_dm} DM", color = TradeNeon.Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Dijual oleh ${listing.seller_username ?: "seller"}. Pembelian gak bisa dibatalin setelah dikonfirmasi.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Batal", color = Color.White)
                }
                Button(
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TradeNeon.Cyan)
                ) {
                    Text(if (busy) "..." else "Beli", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
