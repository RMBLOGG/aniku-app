package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.GachaRollResult
import com.example.network.UserCharacterEntry
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================
//  PALET WARNA — "Neon Arena" (redesign visual, logic tidak berubah)
// ============================================================================
private object Neon {
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

// Warna per rarity - dipake bareng di kartu reveal & grid koleksi biar konsisten
private fun rarityColor(rarity: String): Color = when (rarity) {
    "Mythic" -> Color(0xFFFF3D6E)
    "Legendary" -> Neon.Gold
    "Epic" -> Neon.Purple
    "Rare" -> Neon.Cyan
    else -> Color(0xFF8E8E9A) // Common
}

private fun rarityGradient(rarity: String): Brush = when (rarity) {
    "Mythic" -> Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFFFF8A3D)))
    "Legendary" -> Brush.linearGradient(listOf(Neon.Gold, Color(0xFFFF8A00)))
    "Epic" -> Brush.linearGradient(listOf(Neon.Purple, Color(0xFF6C63FF)))
    "Rare" -> Brush.linearGradient(listOf(Neon.Cyan, Color(0xFF3D7EFF)))
    else -> Brush.linearGradient(listOf(Color(0xFF9E9E9E), Color(0xFF6E6E6E)))
}

// Palet sapuan warna buat border berputar - dipakai khusus rarity tinggi (Epic ke atas)
private fun rarityShimmerColors(rarity: String): List<Color> = when (rarity) {
    "Mythic" -> listOf(Color(0xFFFF3D6E), Neon.Gold, Neon.Purple, Color(0xFFFF3D6E))
    "Legendary" -> listOf(Neon.Gold, Color(0xFFFFF3B0), Color(0xFFFF8A00), Neon.Gold)
    "Epic" -> listOf(Neon.Purple, Color(0xFF6C63FF), Neon.Cyan, Neon.Purple)
    "Rare" -> listOf(Neon.Cyan, Color(0xFF3D7EFF), Neon.Purple, Neon.Cyan)
    else -> listOf(Color(0xFF8E8E9A), Color(0xFF8E8E9A).copy(alpha = 0.35f), Color(0xFF8E8E9A))
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
    rotateScope(angle) {
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

// ── Overlay holografik yang menyapu dari kiri ke kanan terus-menerus, buat kesan kartu "berkilau" ──
@Composable
private fun Modifier.holoSweep(active: Boolean, delayMillis: Int = 0, periodMillis: Int = 1800): Modifier {
    if (!active) return this
    val infinite = rememberInfiniteTransition(label = "holoSweep")
    val translate by infinite.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )
    return this.drawWithContent {
        drawContent()
        val bandWidth = size.width * 0.35f
        val x = translate * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Neon.Cyan.copy(alpha = 0.15f), Color.Transparent),
                start = Offset(x - bandWidth, 0f),
                end = Offset(x + bandWidth, size.height)
            )
        )
    }
}

/** Glow radial di belakang komponen manapun. */
@Composable
private fun BoxScope.GlowBehind(color: Color, sizeFraction: Float = 1f, baseAlpha: Float = 0.3f) {
    val infinite = rememberInfiniteTransition(label = "glow")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxSize(sizeFraction)
            .scale(pulse)
            .background(Brush.radialGradient(listOf(color.copy(alpha = baseAlpha), Color.Transparent)))
    )
}

// ============================================================================
//  BACKGROUND — grid diagonal halus + partikel melayang, kesan "arena digital"
// ============================================================================
@Composable
private fun NeonBackdrop(modifier: Modifier = Modifier) {
    val particles = remember {
        List(22) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2f + 1f) }
    }
    val infinite = rememberInfiniteTransition(label = "backdrop")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "drift"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Neon.Deep2, Neon.Void)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 44.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            particles.forEach { (px, py, r) ->
                val yy = ((py - drift + 1f) % 1f) * size.height
                drawCircle(
                    color = if (px > 0.6f) Neon.Magenta.copy(alpha = 0.35f) else Neon.Cyan.copy(alpha = 0.3f),
                    radius = r.dp.toPx(),
                    center = Offset(px * size.width, yy)
                )
            }
        }
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
        containerColor = Neon.Void,
        topBar = {
            TopAppBar(
                title = { Text("Gacha Karakter", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NeonBackdrop()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Kartu saldo DM ──
                NeonBalanceCard(diamondBalance = diamondBalance, onTopUp = onTopUpClick)

                // ── Tab Gacha / Koleksi ──
                NeonTabRow(
                    selectedTab = selectedTab,
                    koleksiCount = collection.size,
                    onSelect = { selectedTab = it }
                )

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        HexPortal(isRolling = isRolling)

                        Spacer(modifier = Modifier.height(28.dp))

                        if (errorMessage != null) {
                            Text(errorMessage ?: "", color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        NeonPrimaryButton(
                            label = "Gacha x1 · $GACHA_COST_SINGLE DM",
                            isLoading = isRolling,
                            enabled = !isRolling,
                            onClick = { doRoll(1, GACHA_COST_SINGLE) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        NeonOutlineButton(
                            label = "Gacha x$GACHA_MULTI_COUNT · $GACHA_COST_MULTI DM",
                            enabled = !isRolling,
                            onClick = { doRoll(GACHA_MULTI_COUNT, GACHA_COST_MULTI / GACHA_MULTI_COUNT) }
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        RarityOddsBar()
                    }
                } else {
                    KoleksiGrid(collection)
                }
            }
        }
    }

    // ── Overlay reveal hasil gacha ──
    revealResults?.let { results ->
        GachaRevealDialog(results = results, onDismiss = { revealResults = null })
    }
}

// ============================================================================
//  SALDO DM — kartu kaca dengan border sapuan neon
// ============================================================================
@Composable
private fun NeonBalanceCard(diamondBalance: Int, onTopUp: () -> Unit) {
    val angle = rememberRotatingAngle(5000)
    val infinite = rememberInfiniteTransition(label = "diamondTilt")
    val diamondTilt by infinite.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Neon.Glass, Color.White.copy(alpha = 0.03f))))
            .rotatingGradientBorder(
                colors = listOf(Neon.Cyan.copy(alpha = 0.5f), Neon.Purple.copy(alpha = 0.5f), Neon.Magenta.copy(alpha = 0.5f), Neon.Cyan.copy(alpha = 0.5f)),
                angle = angle,
                cornerRadius = 16.dp,
                strokeWidth = 1.2.dp
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Diamond,
                contentDescription = null,
                tint = Neon.Cyan,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = diamondTilt }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("$diamondBalance DM", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Neon.Cyan.copy(alpha = 0.18f), Neon.Purple.copy(alpha = 0.18f))))
                .clickable { onTopUp() }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text("+ Isi DM", color = Neon.Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ============================================================================
//  TAB ROW KUSTOM — indikator glow
// ============================================================================
@Composable
private fun NeonTabRow(selectedTab: Int, koleksiCount: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Gacha", "Koleksi ($koleksiCount)")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedTab == index
            val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "bgAlpha")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Neon.Cyan.copy(alpha = 0.22f * bgAlpha), Neon.Purple.copy(alpha = 0.22f * bgAlpha))
                        )
                    )
                    .then(
                        if (selected) Modifier.border(1.dp, Neon.Cyan.copy(alpha = 0.5f), RoundedCornerShape(11.dp))
                        else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ============================================================================
//  HEX PORTAL — pengganti orb bulat, hexagon berlapis + ring berputar
// ============================================================================
private fun hexagonPath(sizePx: Float): Path {
    val path = Path()
    val radius = sizePx / 2f
    for (i in 0 until 6) {
        val angle = Math.toRadians((60 * i - 90).toDouble())
        val x = radius + radius * cos(angle).toFloat()
        val y = radius + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun HexPortal(isRolling: Boolean) {
    val infinite = rememberInfiniteTransition(label = "hexPortal")
    val ringAngle by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "ring"
    )
    val ringAngleReverse by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "ringR"
    )
    val floatY by infinite.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val corePulse by infinite.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val rollScale by animateFloatAsState(
        targetValue = if (isRolling) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rollScale"
    )

    Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        GlowBehind(color = Neon.Cyan, sizeFraction = 1f, baseAlpha = 0.3f)

        Canvas(modifier = Modifier.size(190.dp).rotate(ringAngle)) {
            val hex = hexagonPath(size.minDimension)
            drawPath(
                path = hex,
                brush = Brush.sweepGradient(listOf(Neon.Cyan, Neon.Purple, Neon.Magenta, Neon.Cyan)),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Canvas(modifier = Modifier.size(160.dp).rotate(ringAngleReverse)) {
            val hex = hexagonPath(size.minDimension)
            drawPath(
                path = hex,
                brush = Brush.sweepGradient(listOf(Neon.Gold.copy(alpha = 0.7f), Color.Transparent, Neon.Gold.copy(alpha = 0.7f))),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .size(128.dp)
                .offset(y = floatY.dp)
                .scale(rollScale * corePulse)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Neon.Deep2, Color(0xFF1B2E4D))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = Neon.Cyan, modifier = Modifier.size(52.dp))
        }
    }
}

// ============================================================================
//  TOMBOL — neon solid & neon outline dengan glow
// ============================================================================
@Composable
private fun NeonPrimaryButton(label: String, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Neon.Cyan, Color(0xFF4FA8FF))))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun NeonOutlineButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val angle = rememberRotatingAngle(3400)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press2")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(16.dp))
            .background(Neon.Gold.copy(alpha = 0.08f))
            .rotatingGradientBorder(
                colors = listOf(Neon.Gold, Color(0xFFFFF3B0), Color(0xFFFF8A00), Neon.Gold),
                angle = angle,
                cornerRadius = 16.dp,
                strokeWidth = 1.6.dp
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Neon.Gold, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Neon.Gold, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

// ============================================================================
//  BAR PELUANG — stacked-bar berwarna + legenda (pengganti teks polos)
// ============================================================================
@Composable
private fun RarityOddsBar() {
    val odds = listOf(
        Triple("Mythic", 1, Color(0xFFFF3D6E)),
        Triple("Legendary", 4, Neon.Gold),
        Triple("Epic", 12, Neon.Purple),
        Triple("Rare", 28, Neon.Cyan),
        Triple("Common", 55, Color(0xFF8E8E9A))
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            odds.forEach { (_, pct, color) ->
                Box(
                    modifier = Modifier
                        .weight(pct.toFloat())
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.85f))
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            odds.forEach { (name, pct, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$name $pct%", color = Color.White.copy(alpha = 0.55f), fontSize = 9.5.sp)
                }
            }
        }
    }
}

// ============================================================================
//  TAB "KOLEKSI" — kartu holografik (logic & data sama, cuma visual)
// ============================================================================
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

@Composable
private fun KoleksiCard(name: String, rarity: String, imageUrl: String?, count: Int, index: Int) {
    val color = rarityColor(rarity)
    val premium = isPremiumRarity(rarity)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    val tiltZ by animateFloatAsState(if (pressed) -3f else 0f, label = "tilt")

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index % 12) * 60L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + scaleIn(
            initialScale = 0.7f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
        val angle = if (premium) rememberRotatingAngle(3000) else 0f

        Column(
            modifier = Modifier
                .scale(pressScale)
                .graphicsLayer { rotationZ = tiltZ }
                .clickable(interactionSource = interaction, indication = null) { }
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Neon.CardBase, Color(0xFF0E0E17))))
                .then(
                    if (premium) {
                        Modifier.rotatingGradientBorder(
                            colors = rarityShimmerColors(rarity),
                            angle = angle,
                            cornerRadius = 16.dp,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Modifier.border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    }
                )
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .holoSweep(active = premium, delayMillis = index * 100)
                )
                Text(
                    rarity.uppercase(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color.copy(alpha = 0.9f))
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
                            .padding(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(4.dp))
                Text(rarity, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
//  DIALOG REVEAL — "Karakter Didapat!" (logic sama, visual burst partikel baru)
// ============================================================================
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

    val bColor = rarityColor(bestRarity)
    val premiumBest = isPremiumRarity(bestRarity)
    val particles = remember { List(16) { Random.nextFloat() to Random.nextFloat() } }
    val infinite = rememberInfiniteTransition(label = "burst")
    val particleT by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "t"
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (revealedCount >= results.size) onDismiss() }) {
        Box(contentAlignment = Alignment.Center) {
            if (revealedCount > 0) {
                val burstScale by infinite.animateFloat(
                    initialValue = 0.95f, targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "burstScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(1.35f)
                        .aspectRatio(1f)
                        .scale(burstScale)
                        .background(Brush.radialGradient(listOf(bColor.copy(alpha = 0.28f), Color.Transparent)))
                )
                if (premiumBest) {
                    Canvas(modifier = Modifier.fillMaxWidth(1.4f).aspectRatio(1f)) {
                        particles.forEachIndexed { i, (px, py) ->
                            val t = (particleT + px) % 1f
                            val radius = size.minDimension / 2f * t
                            val angleP = py * 2f * Math.PI.toFloat()
                            val cx = size.width / 2f + cos(angleP) * radius
                            val cy = size.height / 2f + sin(angleP) * radius
                            drawCircle(
                                color = bColor.copy(alpha = (1f - t) * 0.8f),
                                radius = 2.5.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Neon.CardBase, Neon.Deep1)))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (revealedCount >= results.size && premiumBest) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = bColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            if (results.size == 1) "Karakter Didapat!" else "Hasil Gacha x${results.size}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp
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

                Spacer(modifier = Modifier.height(18.dp))

                if (revealedCount >= results.size) {
                    NeonPrimaryButton("Tutup", isLoading = false, enabled = true, onClick = onDismiss)
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
    val width = if (big) 210.dp else 122.dp
    val rarity = result.rarity
    val color = rarityColor(rarity)
    val premium = isPremiumRarity(rarity)
    val angle = if (premium) rememberRotatingAngle(2400) else 0f

    val infinite = rememberInfiniteTransition(label = "cardPulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (rarity == "Mythic") 1.04f else 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .width(width)
            .scale(pulseScale)
            .clip(RoundedCornerShape(18.dp))
            .background(rarityGradient(rarity))
            .then(
                if (premium) {
                    Modifier.rotatingGradientBorder(
                        colors = rarityShimmerColors(rarity),
                        angle = angle,
                        cornerRadius = 18.dp,
                        strokeWidth = 2.6.dp
                    )
                } else Modifier
            )
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(15.dp))
                .background(Neon.CardBase)
        ) {
            AsyncImage(
                model = result.image_url,
                contentDescription = result.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .holoSweep(active = premium)
            )
            if (result.is_new) {
                Text(
                    "BARU",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Neon.Magenta)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(9.dp)) {
            Text(result.rarity.uppercase(), color = Color.White, fontSize = if (big) 13.sp else 10.sp, fontWeight = FontWeight.Black)
            Text(result.name, color = Color.White, fontSize = if (big) 15.sp else 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            result.anime_title?.let {
                Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = if (big) 11.sp else 9.sp, maxLines = 1)
            }
        }
    }
}
