package com.example.ui


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.BuyTradeListingResult
import com.example.network.MyTradeListing
import com.example.network.TradeMarketListing
import com.example.network.UserCharacterEntry

private enum class TradeTab { PASAR, JUAL_LISTING_SAYA }

// Palet warna sendiri (nilai sama kayak "Neon" di GachaScreen.kt) - gak bisa
// reuse langsung soalnya Neon di GachaScreen.kt dideklarasiin "private object",
// jadi cuma keliatan di file itu sendiri walau satu package.
private object TradeNeon {
    val Cyan = Color(0xFF3DF4FF)
    val Magenta = Color(0xFFFF3DA6)
    val Purple = Color(0xFF9D4EFF)
    val Gold = Color(0xFFFFD23F)
    val Void = Color(0xFF05050B)
    val Deep1 = Color(0xFF0A0A16)
    val Deep2 = Color(0xFF130E24)
    val CardBase = Color(0xFF14141F)
    val Glass = Color(0x1AFFFFFF)
}

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

    LaunchedEffect(Unit) {
        viewModel.loadTradeMarket()
        viewModel.loadMyTradeListings()
        viewModel.loadGachaCollection()
    }

    Scaffold(
        containerColor = TradeNeon.Void,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Text(
                        "Pasar Kartu",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { sellSheetOpen = true }) {
                        Icon(Icons.Default.Sell, contentDescription = "Jual kartu", tint = TradeNeon.Cyan)
                    }
                }
                TabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = TradeNeon.Cyan
                ) {
                    Tab(
                        selected = tab == TradeTab.PASAR,
                        onClick = { tab = TradeTab.PASAR },
                        text = { Text("Pasar (${market.size})") }
                    )
                    Tab(
                        selected = tab == TradeTab.JUAL_LISTING_SAYA,
                        onClick = { tab = TradeTab.JUAL_LISTING_SAYA },
                        text = { Text("Listing Saya") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                TradeTab.PASAR -> MarketGrid(
                    listings = market,
                    onBuyClick = { buyConfirmListing = it }
                )
                TradeTab.JUAL_LISTING_SAYA -> MyListingsList(
                    listings = myListings,
                    busy = busy,
                    onCancel = { listing ->
                        busy = true
                        viewModel.cancelTradeListing(listing.id) { ok, err ->
                            busy = false
                            Toast.makeText(context, err ?: "Listing dibatalin", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
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

@Composable
private fun MarketGrid(listings: List<TradeMarketListing>, onBuyClick: (TradeMarketListing) -> Unit) {
    if (listings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada kartu dijual. Jadi yang pertama!", color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(listings, key = { it.listing_id }) { listing ->
            MarketCard(listing, onClick = { onBuyClick(listing) })
        }
    }
}

@Composable
private fun MarketCard(listing: TradeMarketListing, onClick: () -> Unit) {
    val rColor = rarityColor(listing.rarity)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TradeNeon.CardBase)
            .border(1.dp, rColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp)
    ) {
        Box {
            AsyncImage(
                model = listing.character_image_url,
                contentDescription = listing.character_name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            RarityBadge(
                rarity = listing.rarity,
                compact = true,
                modifier = Modifier.padding(6.dp)
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                listing.character_name ?: "?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "oleh ${listing.seller_username ?: "?"}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Diamond, contentDescription = null, tint = TradeNeon.Cyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${listing.price_dm} DM", color = TradeNeon.Cyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MyListingsList(
    listings: List<MyTradeListing>,
    busy: Boolean,
    onCancel: (MyTradeListing) -> Unit
) {
    if (listings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kamu belum pernah jualan kartu.", color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listings, key = { it.id }) { listing ->
            val char = listing.characters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TradeNeon.CardBase)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = char?.image_url,
                    contentDescription = char?.name,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(char?.name ?: "?", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${listing.price_dm} DM · ${statusLabel(listing.status)}", color = Color.White.copy(alpha = 0.6f))
                }
                if (listing.status == "active") {
                    TextButton(onClick = { onCancel(listing) }, enabled = !busy) {
                        Text("Batalin", color = Color(0xFFFF6B6B))
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "active" -> "Aktif"
    "sold" -> "Terjual"
    "cancelled" -> "Dibatalin"
    else -> status
}

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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TradeNeon.Deep1) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = TradeNeon.Cyan)
                Spacer(Modifier.width(8.dp))
                Text("Jual Kartu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White) }
            }
            Spacer(Modifier.height(8.dp))

            if (selected == null) {
                Text("Pilih kartu dari koleksi kamu:", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(collection, key = { it.characters?.mal_id ?: 0 }) { entry ->
                        val mal = entry.characters?.mal_id ?: return@items
                        val alreadyListed = mal in activeListingCharIds
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(TradeNeon.CardBase)
                                .then(
                                    if (alreadyListed) Modifier else Modifier.clickable { selected = entry }
                                )
                                .padding(4.dp)
                        ) {
                            Column {
                                AsyncImage(
                                    model = entry.characters?.image_url,
                                    contentDescription = entry.characters?.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                if (alreadyListed) {
                                    Text("Lagi dijual", color = TradeNeon.Cyan, maxLines = 1)
                                } else {
                                    Text("x${entry.count}", color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            } else {
                val entry = selected!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = entry.characters?.image_url,
                        contentDescription = entry.characters?.name,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(entry.characters?.name ?: "?", color = Color.White, fontWeight = FontWeight.SemiBold)
                        RarityBadge(rarity = entry.characters?.rarity ?: "Common", compact = true)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { input -> priceInput = input.filter { it.isDigit() } },
                    label = { Text("Harga (DM), minimal 10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Fee pasar 10% dipotong otomatis dari harga jual.",
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedButton(onClick = { selected = null }, modifier = Modifier.weight(1f)) {
                        Text("Ganti kartu")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val price = priceInput.toIntOrNull() ?: 0
                            val mal = entry.characters?.mal_id ?: return@Button
                            onConfirmSell(mal, price)
                        },
                        enabled = !busy && (priceInput.toIntOrNull() ?: 0) >= 10,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TradeNeon.Cyan)
                    ) {
                        Text(if (busy) "Memproses..." else "Pasang Jual", color = Color.Black)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BuyConfirmDialog(
    listing: TradeMarketListing,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beli ${listing.character_name}?") },
        text = { Text("Kamu akan membayar ${listing.price_dm} DM ke ${listing.seller_username ?: "seller"}. Aksi ini gak bisa dibatalin.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Memproses..." else "Beli", color = TradeNeon.Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

