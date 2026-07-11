package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

// Palet sapuan warna buat border berputar - dipakai khusus rarity tinggi (Epic ke atas)
private fun rarityShimmerColors(rarity: String): List<Color> {
    val base = rarityColor(rarity)
    return when (rarity) {
        "Mythic" -> listOf(Color(0xFFFF3D6E), Color(0xFFFFC93C), Color(0xFFB16CEA), Color(0xFFFF3D6E))
        "Legendary" -> listOf(Color(0xFFFFC93C), Color(0xFFFFF3B0), Color(0xFFFF8A00), Color(0xFFFFC93C))
        "Epic" -> listOf(Color(0xFFB16CEA), Color(0xFF6C63FF), Color(0xFF4FD8E8), Color(0xFFB16CEA))
        else -> listOf(base, base.copy(alpha = 0.4f), base)
    }
}

private fun isPremiumRarity(rarity: String) = rarity == "Mythic" || rarity == "Legendary" || rarity == "Epic"

private const val GACHA_COST_SINGLE = 50
private const val GACHA_COST_MULTI = 300 // x6, sedikit lebih murah per-tarikan dibanding satuan
private const val GACHA_MULTI_COUNT = 6

// ── Modifier custom: border gradient yang berputar pelan, dipakai di kartu rarity tinggi ──
private fun Modifier.rotatingGradientBorder(
    colors: List<Color>,
    angle: Float,
    cornerRadius: Dp,
    strokeWidth: Dp = 2.5.dp
): Modifier = this.drawWithContent {
    drawContent()
    val stroke = strokeWidth.toPx()
    val corner = cornerRadius.toPx()
    rotate(angle) {
        drawRoundRect(
            brush = Brush.sweepGradient(colors),
            style = Stroke(width = stroke),
            cornerRadius = CornerRadius(corner, corner)
        )
    }
}

@Composable
private fun rememberRotatingAngle(durationMillis: Int = 3200): Float {
    val infinite = rememberInfiniteTransition(label = "borderRotate")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "angle"
    )
    return angle
}

// ── Overlay shimmer yang menyapu dari kiri ke kanan, buat kesan kartu "berkilau" ──
@Composable
private fun Modifier.shimmerSweep(active: Boolean, delayMillis: Int = 0): Modifier {
    if (!active) return this
    val infinite = rememberInfiniteTransition(label = "shimmerSweep")
    val translate by infinite.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )
    return this.drawWithContent {
        drawContent()
        val bandWidth = size.width * 0.3f
        val x = translate * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                start = Offset(x - bandWidth, 0f),
                end = Offset(x + bandWidth, size.height)
            )
        )
    }
}

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
                    .background(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
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
                    GachaOrb(isRolling = isRolling)

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

// ── Orb diamond yang melayang & berdenyut di tab Gacha ──
@Composable
private fun GachaOrb(isRolling: Boolean) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val float by infinite.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val glowScale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val ringAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "ring"
    )
    val rollScale by animateFloatAsState(
        targetValue = if (isRolling) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rollScale"
    )

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glow radial di belakang
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(glowScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF4FD8E8).copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )
        // Ring tipis berputar
        Box(
            modifier = Modifier
                .size(180.dp)
                .rotatingGradientBorder(
                    colors = listOf(Color(0xFF4FD8E8), Color(0xFF6C63FF), Color(0xFFB16CEA), Color(0xFF4FD8E8)),
                    angle = ringAngle,
                    cornerRadius = 90.dp,
                    strokeWidth = 1.5.dp
                )
                .clip(CircleShape)
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(y = float.dp)
                .scale(rollScale)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2A1B4D), Color(0xFF1B2E4D))))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Diamond,
                contentDescription = null,
                tint = Color(0xFF4FD8E8).copy(alpha = 0.85f),
                modifier = Modifier.size(64.dp)
            )
        }
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
        itemsIndexed(collection) { index, entry ->
            val char = entry.characters ?: return@itemsIndexed
            KoleksiCard(name = char.name, rarity = char.rarity, imageUrl = char.image_url, count = entry.count, index = index)
        }
    }
}

// helper karena foundation.lazy.grid.items tidak selalu expose indexed secara langsung
private inline fun androidx.compose.foundation.lazy.grid.LazyGridScope.itemsIndexed(
    items: List<UserCharacterEntry>,
    crossinline itemContent: @Composable (index: Int, item: UserCharacterEntry) -> Unit
) {
    items(items.size) { i -> itemContent(i, items[i]) }
}

@Composable
private fun KoleksiCard(name: String, rarity: String, imageUrl: String?, count: Int, index: Int) {
    val color = rarityColor(rarity)
    val premium = isPremiumRarity(rarity)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index % 12) * 45L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + scaleIn(
            initialScale = 0.75f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
        val angle = if (premium) rememberRotatingAngle(3400) else 0f

        Column(
            modifier = Modifier
                .scale(pressScale)
                .clickable(interactionSource = interaction, indication = null) { }
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .then(
                    if (premium) {
                        Modifier.rotatingGradientBorder(
                            colors = rarityShimmerColors(rarity),
                            angle = angle,
                            cornerRadius = 14.dp,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    }
                )
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerSweep(active = premium, delayMillis = index * 90)
                )
                // Chip rarity kecil di pojok kiri atas
                Text(
                    rarity.uppercase(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                if (count > 1) {
                    Text(
                        "x$count",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                rarity,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GachaRevealDialog(results: List<GachaRollResult>, onDismiss: () -> Unit) {
    var revealedCount by remember { mutableStateOf(0) }
    val bestRarity = remember(results) {
        val order = listOf("Mythic", "Legendary", "Epic", "Rare", "Common")
        results.map { it.rarity }.minByOrNull { order.indexOf(it).let { i -> if (i < 0) order.size else i } } ?: "Common"
    }

    LaunchedEffect(results) {
        revealedCount = 0
        for (i in results.indices) {
            delay(if (i == 0) 300L else 450L)
            revealedCount = i + 1
        }
    }

    Dialog(onDismissRequest = { if (revealedCount >= results.size) onDismiss() }) {
        Box(contentAlignment = Alignment.Center) {
            // Radial glow burst di belakang dialog, warnanya ngikutin rarity terbaik yang didapat
            if (revealedCount > 0) {
                val infinite = rememberInfiniteTransition(label = "burst")
                val burstScale by infinite.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "burstScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(1.3f)
                        .aspectRatio(1f)
                        .scale(burstScale)
                        .background(
                            Brush.radialGradient(
                                listOf(rarityColor(bestRarity).copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF16161D))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (revealedCount >= results.size && isPremiumRarity(bestRarity)) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = rarityColor(bestRarity),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            if (results.size == 1) "Karakter Didapat!" else "Hasil Gacha x${results.size}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    if (revealedCount >= results.size) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (results.size == 1) {
                    AnimatedVisibility(
                        visible = revealedCount >= 1,
                        enter = scaleIn(
                            initialScale = 0.4f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        ) + fadeIn(tween(250))
                    ) {
                        GachaResultCard(results[0], big = true)
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results.size) { idx ->
                            AnimatedVisibility(
                                visible = idx < revealedCount,
                                enter = scaleIn(
                                    initialScale = 0.4f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                ) + fadeIn(tween(250))
                            ) {
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
}

@Composable
private fun GachaResultCard(result: GachaRollResult, big: Boolean) {
    val width = if (big) 200.dp else 120.dp
    val rarity = result.rarity
    val color = rarityColor(rarity)
    val premium = isPremiumRarity(rarity)
    val angle = if (premium) rememberRotatingAngle(2600) else 0f

    // Pulsa halus khusus Mythic biar makin "wah"
    val infinite = rememberInfiniteTransition(label = "cardPulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (rarity == "Mythic") 1.035f else 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .width(width)
            .scale(pulseScale)
            .clip(RoundedCornerShape(16.dp))
            .background(rarityGradient(rarity))
            .then(
                if (premium) {
                    Modifier.rotatingGradientBorder(
                        colors = rarityShimmerColors(rarity),
                        angle = angle,
                        cornerRadius = 16.dp,
                        strokeWidth = 2.5.dp
                    )
                } else Modifier
            )
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF16161D))
        ) {
            AsyncImage(
                model = result.image_url,
                contentDescription = result.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .shimmerSweep(active = premium)
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
