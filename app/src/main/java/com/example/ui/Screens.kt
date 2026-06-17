package com.example.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.example.network.*
import com.example.ui.theme.getAccentColor
import com.example.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================================================================
// REUSABLE COMPONENTS
// ================================================================

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer_trans")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f)
        ),
        start = androidx.compose.ui.geometry.Offset(translateAnim - 300f, translateAnim - 300f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
    )
}

@Composable
fun AnimeCard(
    anime: AnimeRaw,
    accentColor: Color,
    onClick: () -> Unit,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean = true,
    onLoginRequired: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "card_scale"
    )
    val context = LocalContext.current

    Column(
        modifier = modifier
            .testTag("anime_card_${anime.slug}")
            .width(115.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .drawBehind {
                    // Glow shadow bawah card
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                            startY = size.height * 0.6f,
                            endY = size.height + 12f
                        )
                    )
                }
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Image Poster
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(anime.poster)
                    .crossfade(300)
                    .build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Subtle overlay for depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.4f)
                            )
                        )
                    )
            )

            // Type Badge (Top-left) - Orange bg, black bold text as per spec
            anime.type?.let { typeString ->
                if (typeString.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF8C00)) // AccentOrange or FF8C00 in theme
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = typeString,
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Bookmark Icon (Top-right)
            IconButton(
                onClick = { if (isLoggedIn) onBookmarkToggle() else onLoginRequired() },
                modifier = Modifier
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .align(Alignment.TopEnd)
                    .testTag("bookmark_btn_${anime.slug}")
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) accentColor else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Episode Badge (Bottom-left) - Dense dark semi-transparent bg
            anime.episode?.let { epString ->
                if (epString.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = epString,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Status or Day Badge (Bottom-right) - e.g., Fire emojis or Selesai v
            anime.status_or_day?.let { statusString ->
                if (statusString.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusString,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title text styled as per spec: text-[11px] font-bold line-clamp-2
        Text(
            text = anime.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(
            onClick = onSeeAllClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "LIHAT SEMUA",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

// ================================================================
// 1. HOME SCREEN
// ================================================================

@Composable
fun DonationCard(
    donations: List<Donation>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latest = donations.firstOrNull() ?: return
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1a1a1a)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, accentColor.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("☕", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Support Terbaru",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accentColor
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // List donasi
            donations.forEachIndexed { index, donation ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            donation.supporter_name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            donation.supporter_name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            buildString {
                                append("${donation.amount} ${donation.unit ?: "cup"}")
                                if ((donation.total_amount ?: 0) > 0) {
                                    append(" · Rp${donation.total_amount?.let { formatRupiah(it) }}")
                                }
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (!donation.message.isNullOrEmpty()) {
                            Text(
                                "\"${donation.message}\"",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        relativeTimeShort(donation.created_at),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
                if (index < donations.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 42.dp, top = 8.dp, end = 0.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}

fun formatRupiah(amount: Int): String {
    return when {
        amount >= 1_000_000 -> "${amount / 1_000_000}jt"
        amount >= 1_000 -> "${amount / 1_000}rb"
        else -> amount.toString()
    }
}

fun relativeTimeShort(createdAt: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(createdAt.take(19)) ?: java.util.Date()
        val diffMin = (java.util.Date().time - date.time) / 60000
        when {
            diffMin < 60 -> "${diffMin}m"
            diffMin < 1440 -> "${diffMin / 60}j"
            else -> "${diffMin / 1440}h"
        }
    } catch (e: Exception) { "" }
}

@Composable
private fun shimmerAlpha(index: Int = 0): Float {
    val t = rememberInfiniteTransition(label = "sh$index")
    val a by t.animateFloat(
        initialValue = 0.3f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing, delayMillis = index * 80),
            repeatMode = RepeatMode.Reverse
        ), label = "a$index"
    )
    return a
}

@Composable
fun ShimmerAnimeCard(index: Int = 0) {
    val a = shimmerAlpha(index)
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = a * 0.15f)
    Column {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(color))
        Spacer(modifier = Modifier.height(7.dp))
        Box(modifier = Modifier.fillMaxWidth(0.85f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(modifier = Modifier.height(5.dp))
        Box(modifier = Modifier.fillMaxWidth(0.55f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
fun ShimmerGrid(columns: Int = 3, count: Int = 12) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(count) { i -> ShimmerAnimeCard(index = i) }
    }
}

@Composable
fun LoadingScreen(message: String = "Memuat data anime...") {
    val bg = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Hero placeholder
        val a0 = shimmerAlpha(0)
        Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(bg.copy(alpha = a0 * 0.13f)))
        Spacer(modifier = Modifier.height(16.dp))
        // Section label
        val aL = shimmerAlpha(1)
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(bg.copy(alpha = aL * 0.13f)))
            Box(modifier = Modifier.width(60.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(bg.copy(alpha = aL * 0.1f)))
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Row 1
        Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { i ->
                val c = bg.copy(alpha = shimmerAlpha(i + 2) * 0.14f)
                Column(modifier = Modifier.width(110.dp)) {
                    Box(modifier = Modifier.width(110.dp).height(155.dp).clip(RoundedCornerShape(10.dp)).background(c))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.85f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(c))
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.55f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(c))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        // Section label 2
        val aL2 = shimmerAlpha(7)
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(100.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(bg.copy(alpha = aL2 * 0.13f)))
            Box(modifier = Modifier.width(60.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(bg.copy(alpha = aL2 * 0.1f)))
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Row 2
        Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { i ->
                val c = bg.copy(alpha = shimmerAlpha(i + 8) * 0.14f)
                Column(modifier = Modifier.width(110.dp)) {
                    Box(modifier = Modifier.width(110.dp).height(155.dp).clip(RoundedCornerShape(10.dp)).background(c))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.85f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(c))
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.55f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(c))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun HomeScreen(
    viewModel: AnikuViewModel,
    navController: NavController,
    onNavigateToDetail: (String) -> Unit,
    onSeeAllClicked: (String) -> Unit
) {
    val isHomeLoading by viewModel.isHomeLoading.collectAsState()
    val homeError by viewModel.homeError.collectAsState()
    val ongoingList by viewModel.homeOngoing.collectAsState()
    val recentList by viewModel.homeRecent.collectAsState()
    val popularList by viewModel.homePopular.collectAsState()
    val moviesList by viewModel.homeMovies.collectAsState()
    val slidesList by viewModel.featuredSlides.collectAsState()
    val activeAnnouncement by viewModel.activeAnnouncement.collectAsState()
    val donations by viewModel.donations.collectAsState()
    val hasNewDonation by viewModel.hasNewDonation.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null
    val watchHistory by viewModel.watchHistory.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Login Diperlukan", fontWeight = FontWeight.Bold) },
            text = { Text("Kamu perlu login untuk menggunakan fitur ini. Daftar gratis sekarang!") },
            confirmButton = {
                Button(onClick = { showLoginDialog = false; navController.navigate("auth") }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) {
                    Text("Login / Daftar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("Nanti Saja") }
            }
        )
    }

    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val latestVersion by viewModel.latestVersion.collectAsState()
    val downloadUrl by viewModel.downloadUrl.collectAsState()
    val releaseBody by viewModel.releaseBody.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Tampilkan popup sekali saat update tersedia
    LaunchedEffect(updateAvailable) {
        if (updateAvailable) showUpdateDialog = true
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { /* force update - tidak bisa dismiss */ },
            containerColor = Color(0xFF1A1A2E),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                            model = context.packageManager.getApplicationIcon(context.packageName),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Update Diperlukan!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                var showChangelog by remember { mutableStateOf(false) }
                Column {
                    Text(
                        text = "Versi terbaru $latestVersion sudah tersedia dan wajib diinstall. Perbarui sekarang untuk melanjutkan menggunakan Aniku.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Aplikasi tidak bisa digunakan sebelum update.",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (releaseBody.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        // Tombol toggle changelog
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .clickable { showChangelog = !showChangelog }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lihat perubahan",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (showChangelog) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Expandable changelog
                        androidx.compose.animation.AnimatedVisibility(visible = showChangelog) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(12.dp)
                            ) {
                                val markdownLines = releaseBody.lines()
                                markdownLines.forEach { line ->
                                    when {
                                        line.startsWith("### ") -> {
                                            Text(
                                                text = line.removePrefix("### "),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                            )
                                        }
                                        line.startsWith("## ") -> {
                                            Text(
                                                text = line.removePrefix("## "),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                            )
                                        }
                                        line.startsWith("- ") -> {
                                            val raw = line.removePrefix("- ")
                                            val annotated = buildAnnotatedString {
                                                append("• ")
                                                var i = 0
                                                while (i < raw.length) {
                                                    if (raw[i] == '*' && i + 1 < raw.length && raw[i + 1] == '*') {
                                                        val end = raw.indexOf("**", i + 2)
                                                        if (end != -1) {
                                                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                                                append(raw.substring(i + 2, end))
                                                            }
                                                            i = end + 2
                                                        } else { append(raw[i]); i++ }
                                                    } else { append(raw[i]); i++ }
                                                }
                                            }
                                            Text(
                                                text = annotated,
                                                color = Color.LightGray,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                        line.startsWith("---") -> {
                                            HorizontalDivider(
                                                color = Color.White.copy(alpha = 0.15f),
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                        line.startsWith("> ") -> {
                                            Text(
                                                text = line.removePrefix("> "),
                                                color = Color.LightGray.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                                            )
                                        }
                                        line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                                        else -> {
                                            Text(
                                                text = line,
                                                color = Color.LightGray,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.downloadUpdate(downloadUrl, latestVersion)
                        Toast.makeText(context, "Download dimulai, cek notifikasi", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Sekarang $latestVersion", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = null
        )
    }

    var slideIndex by remember { mutableStateOf(0) }

    // Auto-sliding Hero (3 seconds cycle)
    // Hero banner → use ongoing[0] as first slide
    val sliderItems = remember(ongoingList, slidesList) {
        val list = mutableListOf<AnimeRaw>()
        if (ongoingList.isNotEmpty()) {
            list.add(ongoingList[0])
            val processedSlugs = mutableSetOf(ongoingList[0].slug)
            slidesList.forEach { slide ->
                if (!processedSlugs.contains(slide.anime_slug)) {
                    list.add(AnimeRaw(title = slide.anime_title ?: "", slug = slide.anime_slug, poster = slide.anime_poster ?: ""))
                    processedSlugs.add(slide.anime_slug)
                }
            }
            ongoingList.drop(1).forEach { anime ->
                if (!processedSlugs.contains(anime.slug)) {
                    list.add(anime)
                    processedSlugs.add(anime.slug)
                }
            }
        }
        list.take(5)
    }

    LaunchedEffect(sliderItems) {
        if (sliderItems.isNotEmpty()) {
            while (true) {
                delay(3500)
                slideIndex = (slideIndex + 1) % sliderItems.size
            }
        }
    }

    if (isHomeLoading) {
        LoadingScreen("Memuat data anime...")
    } else if (homeError != null) {
        val currentSource by viewModel.dataSource.collectAsState()
        val servers = listOf(
            "Dayynime-v1" to "Server 1 (Utama)",
            "Dayynime-v2" to "Server 2 (Alternatif)"
        )
        Box(modifier = Modifier.fillMaxSize()) {
            // Tombol settings tetap bisa diakses di pojok kanan atas
            IconButton(
                onClick = { navController.navigate("settings") },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Pengaturan",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = homeError ?: "Gagal memuat data. Periksa koneksi internet Anda.",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Coba ganti server di bawah ini",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Server switcher
                servers.forEach { (key, label) ->
                    val isActive = currentSource == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) accentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                            .clickable(enabled = !isActive) {
                                viewModel.changeDataSource(key)
                                viewModel.loadHomeData()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isActive) accentColor else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                color = if (isActive) accentColor else Color.White,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isActive) Text("Aktif sekarang", color = accentColor.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                        if (!isActive) Text("Gunakan", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.loadHomeData() },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Coba Lagi", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Edeg-to-Edge Hero Billboard Slider
            if (sliderItems.isNotEmpty()) {
                item {
                    val activeSlide = sliderItems.getOrNull(slideIndex)
                    if (activeSlide != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                        ) {
                            // Crossfade antar slide
                            androidx.compose.animation.Crossfade(
                                targetState = activeSlide,
                                animationSpec = tween(600, easing = EaseInOutCubic),
                                label = "hero_crossfade"
                            ) { slide ->
                            // Bleeding Poster Graphic
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(slide.poster)
                                    .crossfade(400)
                                    .build(),
                                contentDescription = slide.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            }

                            // Immersive Linear Gradients
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.6f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.3f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            // Atmospheric Radial Bleeding overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    accentColor.copy(alpha = 0.15f),
                                                    Color.Transparent
                                                ),
                                                center = center,
                                                radius = size.width * 0.7f
                                            )
                                        )
                                    }
                            )

                            // Slider metadata info (Bottom aligned)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 24.dp)
                            ) {
                                // Dynamic Glassmorphism Categories Row (above title)
                                val genresToShow = activeSlide.genres?.filter { it.isNotBlank() } ?: listOf("Action", "Fantasy", "Adventure")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    genresToShow.take(3).forEach { genre ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }

                                androidx.compose.animation.AnimatedContent(
                                    targetState = activeSlide.title,
                                    transitionSpec = {
                                        fadeIn(tween(500)) togetherWith fadeOut(tween(200))
                                    },
                                    label = "hero_title"
                                ) { title ->
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { onNavigateToDetail(activeSlide.slug) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("hero_play_btn")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Tonton Sekarang", fontWeight = FontWeight.Bold)
                                    }

                                    val isHeroBookmarked = bookmarkedAnimes.any { it.slug == activeSlide.slug }
                                    IconButton(
                                        onClick = { viewModel.toggleBookmark(activeSlide.slug, activeSlide.title, activeSlide.poster) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .testTag("hero_bookmark_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (isHeroBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Bookmark Hero",
                                            tint = if (isHeroBookmarked) accentColor else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Dot selectors
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    sliderItems.forEachIndexed { idx, _ ->
                                        val isSelected = idx == slideIndex
                                        val dotWidthPx by animateFloatAsState(
                                            targetValue = if (isSelected) 20f else 6f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                            label = "dot_width_$idx"
                                        )
                                        val dotAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 1f else 0.3f,
                                            animationSpec = tween(300),
                                            label = "dot_alpha_$idx"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .width(dotWidthPx.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(if (isSelected) accentColor else Color.White.copy(alpha = dotAlpha))
                                                .clickable { slideIndex = idx }
                                        )
                                    }
                                }
                            }

                            // Header Bar (logo only - settings button is now a floating overlay)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "ANIKU",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 3.sp,
                                    style = LocalTextStyle.current.copy(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = accentColor.copy(alpha = 0.8f),
                                            blurRadius = 8f
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }


            // Donasi terbaru dari Saweria
            if (donations.isNotEmpty()) {
                item {
                    var donationDismissed by remember { mutableStateOf(false) }
                    if (!donationDismissed) {
                        DonationCard(
                            donations = donations.take(3),
                            onRefresh = { viewModel.loadDonations() },
                            onDismiss = { donationDismissed = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }


            activeAnnouncement?.let { ann ->
                item {
                    var dismissed by remember { mutableStateOf(false) }
                    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                    if (!dismissed) {
                        AnimatedVisibility(
                            visibleState = visible,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(animationSpec = tween(300)),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .fillMaxWidth()
                            ) {
                                // Bottom glow
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(12.dp)
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 6.dp)
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color(0x33E53935), Color.Transparent)
                                            ),
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(Color(0xFF1C0A0A), Color(0xFF141414), Color(0xFF1A0D0D))
                                                ),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(Color(0x44E53935), Color(0x22E53935), Color(0x44E53935))
                                                ),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                    ) {
                                        // Shimmer top line
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .height(1.dp)
                                                .align(Alignment.TopCenter)
                                                .background(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(Color.Transparent, Color(0x88FF6464), Color.Transparent)
                                                    )
                                                )
                                        )
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            // Icon box
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(
                                                        brush = Brush.linearGradient(
                                                            colors = listOf(Color(0x44E53935), Color(0x22B71C1C))
                                                        ),
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .border(1.dp, Color(0x44E53935), RoundedCornerShape(14.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("📢", fontSize = 22.sp)
                                            }
                                            // Content
                                            Column(modifier = Modifier.weight(1f)) {
                                                // Badge with pulse dot
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .background(Color(0x22E53935), RoundedCornerShape(6.dp))
                                                        .border(1.dp, Color(0x33E53935), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                                    val pulseAlpha by infiniteTransition.animateFloat(
                                                        initialValue = 1f, targetValue = 0.3f,
                                                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                                        label = "pulseAlpha"
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .background(Color(0xFFFF5252).copy(alpha = pulseAlpha), CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        "PENGUMUMAN",
                                                        color = Color(0xFFFF6B6B),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        letterSpacing = 1.5.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(5.dp))
                                                Text(
                                                    text = ann.title,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = ann.message,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 11.5.sp,
                                                    maxLines = Int.MAX_VALUE,
                                                    overflow = TextOverflow.Visible
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Close button
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                        .clickable { dismissed = true },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("✕", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                                                }
                                                // Download button (only if download_url exists)
                                                if (!ann.download_url.isNullOrBlank()) {
                                                    val ctx = LocalContext.current
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                brush = Brush.linearGradient(
                                                                    colors = listOf(Color(0xFFE53935), Color(0xFFB71C1C))
                                                                ),
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ann.download_url))
                                                                ctx.startActivity(intent)
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "Download",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section Riwayat: Terakhir Ditonton
            if (watchHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terakhir Ditonton",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        TextButton(onClick = { viewModel.clearWatchHistory() }) {
                            Text("Hapus", color = accentColor, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchHistory, key = { "${it.animeSlug}_${it.episodeSlug}" }) { item ->
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable { onNavigateToDetail(item.animeSlug) }
                            ) {
                                Box {
                                    AsyncImage(
                                        model = item.animePoster,
                                        contentDescription = item.animeTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(155.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    // Badge episode
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.episodeTitle,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.animeTitle,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Sedang Tayang
            item { SectionHeader(title = "Sedang Tayang", onSeeAllClick = { onSeeAllClicked("Ongoing") }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ongoingList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { showLoginDialog = true }
                        )
                    }
                }
            }

            // Section 2: Terbaru
            item { SectionHeader(title = "Terbaru", onSeeAllClick = { onSeeAllClicked("Latest") }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { showLoginDialog = true }
                        )
                    }
                }
            }

            // Section 3: Terpopuler
            item { SectionHeader(title = "Terpopuler", onSeeAllClick = { onSeeAllClicked("Popular") }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(popularList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { showLoginDialog = true }
                        )
                    }
                }
            }

            // Section 4: Anime Movie
            item { SectionHeader(title = "Anime Movie", onSeeAllClick = { onSeeAllClicked("Movie") }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(moviesList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { showLoginDialog = true }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // Floating Settings Button (sticky, tidak ikut scroll)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(end = 16.dp, top = 12.dp)
                .size(38.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .clickable { navController.navigate("settings") }
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.Center
        ) {
            if (!session.avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = session.avatarUrl,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        } // end Box
    }
}

// ================================================================
// 2. SEARCH SCREEN
// ================================================================

@Composable
fun SearchScreen(
    viewModel: AnikuViewModel,
    onNavigateToDetail: (String) -> Unit,
    onLoginRequired: () -> Unit = {}
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val popularList by viewModel.searchPopular.collectAsState()
    val isLoading by viewModel.isSearchLoading.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val gridLayout by viewModel.gridLayout.collectAsState()
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Input Bar
        TextField(
            value = query,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding()
                .testTag("search_input"),
            placeholder = { Text("Cari Anime Kesukaanmu...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                cursorColor = accentColor,
                focusedIndicatorColor = accentColor,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (isLoading) {
            LoadingScreen("Mencari anime...")
        } else if (query.isEmpty()) {
            // Hot Anime Section
            Text(
                text = "Rekomendasi Terpopuler",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (gridLayout == "List") {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(popularList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeListCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { onLoginRequired() }
                        )
                    }
                }
            } else {
                val columns = if (gridLayout == "3") 3 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(popularList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            modifier = Modifier.fillMaxWidth(),
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { onLoginRequired() }
                        )
                    }
                }
            }
        } else if (results.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Anime tidak ditemukan.", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            // Live Search Query results
            if (gridLayout == "List") {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeListCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { onLoginRequired() }
                        )
                    }
                }
            } else {
                val columns = if (gridLayout == "3") 3 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = { onNavigateToDetail(anim.slug) },
                            isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                            onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                            modifier = Modifier.fillMaxWidth(),
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = { onLoginRequired() }
                        )
                    }
                }
            }
        }
    }
}

// ================================================================
// 3. EXPLORE SCREEN
// ================================================================

@Composable
fun ExploreScreen(
    viewModel: AnikuViewModel,
    onNavigateToDetail: (String) -> Unit,
    onLoginRequired: () -> Unit = {}
) {
    val activeTab by viewModel.exploreTab.collectAsState()
    val activeGenre by viewModel.selectedGenreSlug.collectAsState()
    val genresList by viewModel.genres.collectAsState()
    val itemsList by viewModel.exploreAnimes.collectAsState()
    val isLoading by viewModel.isExploreLoading.collectAsState()
    val hasNext by viewModel.exploreHasNext.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val gridLayout by viewModel.gridLayout.collectAsState()
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null

    val tabs = listOf("Ongoing", "Completed", "Movie", "Latest")
    val activeTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = activeTabIndex,
        pageCount = { tabs.size }
    )

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // Detect endless scroll
    val shouldLoadMore = remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val listTotal = listState.layoutInfo.totalItemsCount
            val listLastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            (total > 0 && lastVisible >= total - 2) || (listTotal > 0 && listLastVisible >= listTotal - 2)
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasNext && !isLoading) {
            viewModel.loadNextExplorePage()
        }
    }

    LaunchedEffect(Unit) {
        if (itemsList.isEmpty()) {
            viewModel.loadExplorePage()
        }
    }

    // Sync: swipe → update ViewModel (load data tab baru)
    LaunchedEffect(pagerState.currentPage) {
        val swipedTab = tabs[pagerState.currentPage]
        if (swipedTab != activeTab) {
            viewModel.setExploreTab(swipedTab)
        }
    }

    // Sync: tap tab → scroll pager
    LaunchedEffect(activeTab) {
        val idx = tabs.indexOf(activeTab).coerceAtLeast(0)
        if (pagerState.currentPage != idx) {
            pagerState.animateScrollToPage(idx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Filters Ongoing | Completed | Movie | Terbaru
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEach { tabName ->
                val isSelected = activeTab == tabName && activeGenre == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .clickable { viewModel.setExploreTab(tabName) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (tabName == "Latest") "Terbaru" else tabName,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Genre chips — berlaku untuk tab aktif
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val isAllSelected = activeGenre == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAllSelected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isAllSelected) accentColor else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectGenre(null) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Semua Genre",
                        color = if (isAllSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            items(genresList, key = { "${it.slug}_${it.hashCode()}" }) { gen ->
                val isSelected = activeGenre == gen.slug
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectGenre(gen.slug) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = gen.name,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // HorizontalPager — konten per tab
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = activeGenre == null // nonaktifkan swipe saat filter genre aktif
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (itemsList.isEmpty() && isLoading) {
                    val columns = if (gridLayout == "3") 3 else 2
                    ShimmerGrid(columns = columns, count = if (columns == 3) 12 else 8)
                } else if (itemsList.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Daftar anime kosong.", color = Color.Gray)
                    }
                } else if (gridLayout == "List") {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(itemsList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                            AnimeListCard(
                                anime = anim,
                                accentColor = accentColor,
                                onClick = { onNavigateToDetail(anim.slug) },
                                isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                                onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                                isLoggedIn = isLoggedIn,
                                onLoginRequired = { onLoginRequired() }
                            )
                        }
                        if (isLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                } else {
                    val columns = if (gridLayout == "3") 3 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(itemsList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                            AnimeCard(
                                anime = anim,
                                accentColor = accentColor,
                                onClick = { onNavigateToDetail(anim.slug) },
                                isBookmarked = bookmarkedAnimes.any { it.slug == anim.slug },
                                onBookmarkToggle = { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (isLoading) {
                            item(span = { GridItemSpan(columns) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================================================================
// ANIME LIST CARD (untuk layout List)
// ================================================================

@Composable
fun AnimeListCard(
    anime: AnimeRaw,
    accentColor: Color,
    onClick: () -> Unit,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean = true,
    onLoginRequired: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(70.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = anime.poster,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            anime.type?.let { typeString ->
                if (typeString.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF8C00))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(typeString, color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            anime.status?.let {
                Text(it, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            anime.episode?.let {
                Text("Ep: $it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
        IconButton(
            onClick = { if (isLoggedIn) onBookmarkToggle() else onLoginRequired() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (isBookmarked) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}



@Composable
fun ScheduleScreen(
    viewModel: AnikuViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    val activeDay by viewModel.selectedDay.collectAsState()
    val scheduleMap by viewModel.scheduleMap.collectAsState()
    val isLoading by viewModel.isScheduleLoading.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val currentSource by viewModel.dataSource.collectAsState()

    val days = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
    val activeDayIndex = days.indexOf(activeDay).coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = activeDayIndex,
        pageCount = { days.size }
    )

    // Sync: swipe → update ViewModel
    LaunchedEffect(pagerState.currentPage) {
        val swipedDay = days[pagerState.currentPage]
        if (swipedDay != activeDay) {
            viewModel.selectDay(swipedDay)
        }
    }

    // Sync: tap tab → scroll pager
    LaunchedEffect(activeDay) {
        val idx = days.indexOf(activeDay).coerceAtLeast(0)
        if (pagerState.currentPage != idx) {
            pagerState.animateScrollToPage(idx)
        }
    }

    LaunchedEffect(currentSource) {
        viewModel.clearScheduleCache()
        viewModel.fetchScheduleData()

    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Day selector header — tap untuk pindah, swipe juga bisa
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            days.forEach { d ->
                val isSelected = activeDay == d
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewModel.selectDay(d) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = d,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (isLoading) {
            LoadingScreen("Memuat jadwal tayang...")
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val day = days[page]
                val dayList = scheduleMap[day] ?: emptyList()

                if (dayList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(52.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Hari $day tidak ada jadwal tayang.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(dayList, key = { "${it.slug}_${it.hashCode()}" }) { anim ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onNavigateToDetail(anim.slug) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = anim.poster,
                                    contentDescription = anim.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 60.dp, height = 90.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = anim.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    anim.episode?.let { ep ->
                                        Text(
                                            text = "Tayang: $ep",
                                            color = accentColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    anim.type?.let { t ->
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = t,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = accentColor,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================================================================
// 5. BOOKMARK SCREEN
// ================================================================

@Composable
fun BookmarkScreen(
    viewModel: AnikuViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    val bookmarksList by viewModel.bookmarks.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val gridLayout by viewModel.gridLayout.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshBookmarks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App title
        Text(
            text = "Daftar Bookmark Saya",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
        )

        if (bookmarksList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Daftar Bookmark Anda Kosong",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cari anime kesukaanmu dan tandai sebagai favorit untuk disimpan di sini.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (gridLayout == "List") {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bookmarksList, key = { "${it.slug}_${it.hashCode()}" }) { bookmarked ->
                    val backingRaw = AnimeRaw(
                        title = bookmarked.title,
                        slug = bookmarked.slug,
                        poster = bookmarked.poster,
                        type = bookmarked.type,
                        episode = bookmarked.episode
                    )
                    AnimeListCard(
                        anime = backingRaw,
                        accentColor = accentColor,
                        onClick = { onNavigateToDetail(bookmarked.slug) },
                        isBookmarked = true,
                        onBookmarkToggle = {
                            viewModel.toggleBookmark(bookmarked.slug, bookmarked.title, bookmarked.poster)
                        }
                    )
                }
            }
        } else {
            val columns = if (gridLayout == "3") 3 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bookmarksList, key = { "${it.slug}_${it.hashCode()}" }) { bookmarked ->
                    val backingRaw = AnimeRaw(
                        title = bookmarked.title,
                        slug = bookmarked.slug,
                        poster = bookmarked.poster,
                        type = bookmarked.type,
                        episode = bookmarked.episode
                    )
                    AnimeCard(
                        anime = backingRaw,
                        accentColor = accentColor,
                        onClick = { onNavigateToDetail(bookmarked.slug) },
                        isBookmarked = true,
                        onBookmarkToggle = {
                            viewModel.toggleBookmark(bookmarked.slug, bookmarked.title, bookmarked.poster)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ================================================================
// 6. ANIME DETAIL SCREEN
// ================================================================

@Composable
fun AnimeDetailScreen(
    slug: String,
    viewModel: AnikuViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onNavigateToWatch: (String, String) -> Unit
) {
    val detail by viewModel.animeDetail.collectAsState()
    val isDetailLoading by viewModel.isDetailLoading.collectAsState()
    val detailError by viewModel.detailError.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null
    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Login Diperlukan", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text("Kamu perlu login untuk menggunakan fitur ini. Daftar gratis sekarang!", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            },
            confirmButton = {
                Button(
                    onClick = { showLoginDialog = false; navController.navigate("auth") },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Login / Daftar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text("Nanti Saja", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }


    var isSynopsisExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(slug) {
        viewModel.loadAnimeDetail(slug)
        com.example.AnikuAnalytics.trackAnimeOpened(slug, detail?.title ?: slug)
    }

    if (isDetailLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = accentColor, strokeWidth = 2.5.dp, modifier = androidx.compose.ui.Modifier.size(36.dp))
                androidx.compose.material3.Text("Memuat detail anime...", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f))
            }
        }
    } else if (detailError != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = detailError ?: "Terjadi kesalahan", color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) {
                Text("Kembali")
            }
        }
    } else {
        detail?.let { d ->
            val isBookmarked = bookmarkedAnimes.any { it.slug == slug }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Background Poster with Dark Gradient overlays
                AsyncImage(
                    model = d.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                // Scrollable details contents
                val detailVisible = remember { MutableTransitionState(false).apply { targetState = true } }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(220.dp))

                    // Detail Row Cards
                    AnimatedVisibility(
                        visibleState = detailVisible,
                        enter = fadeIn(tween(400, delayMillis = 100)) +
                                slideInVertically(
                                    initialOffsetY = { it / 6 },
                                    animationSpec = tween(400, delayMillis = 100, easing = EaseOutCubic)
                                )
                    ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            // Title & Type Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    d.synonym?.let { syn ->
                                        if (syn.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = syn, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accentColor)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = d.type ?: "TV",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Rating & Status Badges
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = d.rating ?: "0.0", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = d.status ?: "Airing", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Studio Extra info chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val infos = listOf(
                                    "Studio: ${d.studio ?: "-"}",
                                    "Musim: ${d.season ?: "-"}",
                                    "Durasi: ${d.duration ?: "-"}",
                                    "Rilis: ${d.aired ?: "-"}"
                                )
                                infos.forEach { inf ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(text = inf, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bookmark Toggle & Tonton Button Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (!isLoggedIn) {
                                            showLoginDialog = true
                                        } else {
                                            val firstEp = d.episodes?.firstOrNull()?.slug
                                            if (!firstEp.isNullOrEmpty()) {
                                                onNavigateToWatch(firstEp, d.title)
                                            } else {
                                                Toast.makeText(context, "Tidak ada episode tersedia", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("detail_play_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mulai Tonton", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (!isLoggedIn) showLoginDialog = true
                                        else viewModel.toggleBookmark(slug, d.title, d.poster, d.type)
                                    },
                                    modifier = Modifier.testTag("detail_bookmark_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Simpan",
                                        tint = if (isBookmarked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (!isLoggedIn) {
                                            showLoginDialog = true
                                        } else {
                                            viewModel.setPendingSharedAnime(
                                                com.example.network.SharedAnimeRef(
                                                    slug = slug,
                                                    title = d.title,
                                                    poster = d.poster,
                                                    type = d.type
                                                )
                                            )
                                            navController.navigate("create_post")
                                        }
                                    },
                                    modifier = Modifier.testTag("detail_share_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Bagikan ke Feed",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Synopsis section
                            Text(text = "Sinopsis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = d.synopsis ?: "Tidak ada sinopsis.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { isSynopsisExpanded = !isSynopsisExpanded }
                            )
                            Text(
                                text = if (isSynopsisExpanded) "Sembunyikan" else "Baca Selengkapnya...",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Genres chips
                            Text(text = "Genre", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                d.genres?.forEach { gen ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.setExploreTab("Ongoing")
                                                viewModel.selectGenre(gen.slug)
                                                navController.navigate("explore")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = gen.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Episode listing (newest first)
                            Text(
                                text = "Daftar Episode (${d.episodes?.size ?: 0})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val episodesList = d.episodes ?: emptyList()
                            if (episodesList.isEmpty()) {
                                Text(text = "Belum ada episode tersedia.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
                            } else {
                                val groupSize = 30
                                val totalEps = episodesList.size
                                val groups = if (totalEps > groupSize) {
                                    (0 until totalEps step groupSize).map { start ->
                                        val end = minOf(start + groupSize - 1, totalEps - 1)
                                        Pair(start, end)
                                    }
                                } else null
                                var selectedGroup by remember(episodesList) { mutableStateOf(0) }

                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Semua Episode", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                                    Text("$totalEps Ep", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
                                }

                                // Group range tabs
                                if (groups != null && groups.size > 1) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        itemsIndexed(groups) { idx, (start, end) ->
                                            val epStart = episodesList.getOrNull(start)?.name?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (start + 1)
                                            val epEnd = episodesList.getOrNull(end)?.name?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (end + 1)
                                            val isSelected = selectedGroup == idx
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { selectedGroup = idx }
                                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = "$epStart-$epEnd",
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                // Grid pills
                                val displayEps = if (groups != null) {
                                    val (start, end) = groups[selectedGroup]
                                    episodesList.subList(start, end + 1)
                                } else episodesList

                                val cols = 5
                                val rowCount = (displayEps.size + cols - 1) / cols
                                for (row in 0 until rowCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (col in 0 until cols) {
                                            val epIndex = row * cols + col
                                            if (epIndex < displayEps.size) {
                                                val ep = displayEps[epIndex]
                                                val epNum = ep.name.replace(Regex("[^0-9]"), "").ifEmpty { "-" }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1.3f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .clickable { if (!isLoggedIn) showLoginDialog = true else onNavigateToWatch(ep.slug, d.title) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("E$epNum", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    } // end AnimatedVisibility
                }

                // Floating controller back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 16.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }
    }
}

// ================================================================
// 7. WATCH / STREAMING SCREEN
// ================================================================

@Composable
fun WatchScreen(
    episodeSlug: String,
    animeTitle: String,
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    var currentEpisodeSlug by remember { mutableStateOf(episodeSlug) }
    val streams by viewModel.streams.collectAsState()
    val activeStreamUrl by viewModel.activeStreamUrl.collectAsState()
    val selectedIndex by viewModel.selectedStreamIndex.collectAsState()
    val isStreamLoading by viewModel.isStreamLoading.collectAsState()
    val streamError by viewModel.streamError.collectAsState()
    val episodeTitle by viewModel.streamEpisodeTitle.collectAsState()
    val detail by viewModel.animeDetail.collectAsState() // Hold backing episode listing
    val currentAnimeSlug by viewModel.currentAnimeSlug.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(currentEpisodeSlug) {
        viewModel.loadEpisodeStream(currentEpisodeSlug)
        com.example.AnikuAnalytics.trackEpisodeWatched(animeTitle, currentEpisodeSlug)
    }

    // Catat riwayat saat episodeTitle sudah tersedia
    LaunchedEffect(episodeTitle) {
        val title = episodeTitle ?: ""
        if (title.isNotEmpty()) {
            viewModel.addToWatchHistory(
                animeSlug = currentAnimeSlug,
                animeTitle = animeTitle,
                animePoster = detail?.poster ?: "",
                episodeSlug = currentEpisodeSlug,
                episodeTitle = title
            )
        }
    }

    // Clear stream state saat WatchScreen ditinggal
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearStreamState()
        }
    }

    val activity = LocalContext.current as? android.app.Activity
    var isFullscreen by remember { mutableStateOf(false) }

    // Handle back press to exit fullscreen
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
    }

    // Effect to handle orientation & system UI when fullscreen changes
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App controls Header row
        if (!isFullscreen) Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = animeTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = episodeTitle ?: "Memuat...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Webview embed stream container
        Box(
            modifier = if (isFullscreen) Modifier.fillMaxSize().background(Color.Black)
                       else Modifier.fillMaxWidth().height(220.dp).background(Color.Black)
        ) {
            if (isStreamLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else if (streamError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = streamError ?: "Gagal memutar video", color = Color.White, modifier = Modifier.padding(16.dp))
                }
            } else if (!activeStreamUrl.isNullOrEmpty()) {
                // Render embed stream player - otakuzone style fullscreen
                var customView by remember { mutableStateOf<android.view.View?>(null) }
                var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

                val handleHideCustomView = {
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                }

                if (customView != null) {
                    BackHandler { handleHideCustomView() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = {
                                customView?.apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                } ?: android.view.View(it)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    var isWebViewLoading by remember { mutableStateOf(true) }
                    Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    // Domain video player yang diizinkan
                                    private val allowedDomains = listOf(
                                        // Vidhide semua subdomain
                                        "vidhide.com", "vidhidepro.com", "vidhideplus.com",
                                        // Filemoon
                                        "filemoon.sx", "filemoon.in", "filemoon.to",
                                        // Filedon
                                        "filedon.co", "filedon.com",
                                        // Dood
                                        "dood.watch", "doodstream.com", "dood.to",
                                        "dood.so", "dood.cx", "dood.la",
                                        // Streamtape
                                        "streamtape.com", "streamtape.co",
                                        // Upload services
                                        "mp4upload.com", "yourupload.com",
                                        // Mega embed
                                        "mega.nz", "mega.co.nz",
                                        // Blogger/Google video embed resmi
                                        "blogger.com", "blogspot.com",
                                        "googlevideo.com", "googleapis.com",
                                        // CDN & player assets
                                        "gstatic.com", "jwplatform.com", "jwpcdn.com",
                                        "akamaized.net", "cloudfront.net", "fastly.net",
                                        "cdnjs.cloudflare.com", "cloudflare.com",
                                        // Animasu & API source
                                        "animasu.cc", "sanka.my.id",
                                        // Abysscdn
                                        "abysscdn.com",
                                        // Samehadaku servers
                                        "samehadaku.how", "v2.samehadaku.how",
                                        "wibufile.com", "wibu.io",
                                        "pixeldrain.com",
                                        "letsupload.io", "letsupload.cc",
                                        "krakenfiles.com",
                                        "gofile.io",
                                        "acefile.co",
                                        "mediafire.com",
                                        "mir.cr",
                                        "nakamaxyz.com", "nakama.to",
                                        "premium.to",
                                        "pucuk.eu.org",
                                        // General CDN
                                        "cdn.jsdelivr.net", "unpkg.com"
                                    )
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val host = request.url?.host?.lowercase() ?: return false
                                        val isAllowed = allowedDomains.any { host.endsWith(it) }
                                        if (!isAllowed) {
                                            android.util.Log.w("AnikuWebView", "Blocked redirect: $url")
                                            return true
                                        }
                                        return false
                                    }
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isWebViewLoading = true
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                        super.onShowCustomView(view, callback)
                                        customView = view
                                        customViewCallback = callback
                                    }
                                    override fun onHideCustomView() {
                                        super.onHideCustomView()
                                        handleHideCustomView()
                                    }
                                }
                            }
                        },
                        update = { view ->
                            val url = activeStreamUrl ?: return@AndroidView
                            val headers = mapOf(
                                "Referer" to "https://v2.samehadaku.how/",
                                "Origin" to "https://v2.samehadaku.how",
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            )
                            view.loadUrl(url, headers)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Loading overlay di atas WebView
                    if (isWebViewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = accentColor, strokeWidth = 3.dp)
                                Text(
                                    "Sedang memuat video...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    } // end Box wrapper
                }
            }
        }

        // Fullscreen enter button (shown below video when not fullscreen)
        if (!isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp, top = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .clickable { isFullscreen = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⛶", color = Color.White, fontSize = 14.sp)
                        Text("Layar Penuh", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Horizontal quality tags selection
        if (!isFullscreen && streams.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                streams.forEachIndexed { i, q ->
                    val isSelected = selectedIndex == i
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.selectStreamQuality(i) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = q.name,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Previous and Next Episode controls
        detail?.episodes?.let { eps ->
            val currentIndex = eps.indexOfFirst { it.slug == currentEpisodeSlug }
            if (currentIndex != -1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Previous ep (index + 1 karena list descending)
                    val hasPrev = currentIndex < eps.size - 1
                    Button(
                        onClick = {
                            val newEp = eps.getOrNull(currentIndex + 1)
                            if (newEp != null) {
                                currentEpisodeSlug = newEp.slug
                            }
                        },
                        enabled = hasPrev,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("< Sebelumnya")
                    }

                    // Next ep (index - 1 karena list descending)
                    val hasNext = currentIndex > 0
                    Button(
                        onClick = {
                            val newEp = eps.getOrNull(currentIndex - 1)
                            if (newEp != null) {
                                currentEpisodeSlug = newEp.slug
                            }
                        },
                        enabled = hasNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Selanjutnya >")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (!isFullscreen) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Episode List — Bstation grid pill style
        if (!isFullscreen) {
        val eps = detail?.episodes ?: emptyList()
        val groupSize = 30
        val totalEps = eps.size
        val groups = if (totalEps > groupSize) {
            (0 until totalEps step groupSize).map { start ->
                val end = minOf(start + groupSize - 1, totalEps - 1)
                Pair(start, end)
            }
        } else null

        var selectedGroup by remember(eps) { mutableStateOf(0) }

        // Auto-select group containing current episode
        LaunchedEffect(currentEpisodeSlug, eps) {
            if (groups != null) {
                val idx = eps.indexOfFirst { it.slug == currentEpisodeSlug }
                if (idx >= 0) selectedGroup = idx / groupSize
            }
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Semua Episode",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "$totalEps Episode",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }

        // Group range tabs
        if (groups != null && groups.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                itemsIndexed(groups) { idx, (start, end) ->
                    val epStart = eps.getOrNull(start)?.name?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (start + 1)
                    val epEnd = eps.getOrNull(end)?.name?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (end + 1)
                    val isSelected = selectedGroup == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedGroup = idx }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "$epStart-$epEnd",
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Grid pills
        val displayEps = if (groups != null) {
            val (start, end) = groups[selectedGroup]
            eps.subList(start, end + 1)
        } else eps

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(displayEps, key = { idx, item -> "${item.slug}_${idx}" }) { _, item ->
                val isActive = item.slug == currentEpisodeSlug
                val epNum = item.name.replace(Regex("[^0-9]"), "").ifEmpty { "-" }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) accentColor
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (isActive) accentColor else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { currentEpisodeSlug = item.slug },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isActive) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "E$epNum",
                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        } // end if (!isFullscreen)
    }
}

// ================================================================
// 8. AUTH SCREENS (LOGIN / REGISTER)
// ================================================================

@Composable
fun AuthScreen(
    viewModel: AnikuViewModel,
    onAuthSuccess: () -> Unit,
    onGuestMode: () -> Unit
) {
    var isLoginTab by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stylized Cinematic Logo
            Text(
                text = "Aniku",
                color = accentColor,
                fontWeight = FontWeight.Black,
                fontSize = 44.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "Cinema-grade Anime Portal",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(44.dp))

            // Card Form
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Segment control Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isLoginTab) accentColor else Color.Transparent)
                                .clickable { isLoginTab = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Masuk", color = if (isLoginTab) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isLoginTab) accentColor else Color.Transparent)
                                .clickable { isLoginTab = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Daftar", color = if (!isLoginTab) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    authError?.let { err ->
                        Text(text = err, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }

                    // Email Field
                    Text("Email", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().testTag("auth_email"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Username field (only in signup)
                    if (!isLoginTab) {
                        Text("Username", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth().testTag("auth_username"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Password Field
                    Text("Sandi", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().testTag("auth_password"),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submission action
                    Button(
                        onClick = {
                            if (isLoginTab) {
                                viewModel.login(email.trim(), password.trim(), onAuthSuccess)
                            } else {
                                viewModel.register(email.trim(), password.trim(), username.trim(), onAuthSuccess)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        enabled = !authLoading
                    ) {
                        if (authLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(if (isLoginTab) "Masuk" else "Daftar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Guest Mode link
            TextButton(onClick = onGuestMode) {
                Text(
                    text = "Gunakan Mode Tamu",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ================================================================
// 9. PROFILE SCREEN (USER EDIT)
// ================================================================

@Composable
fun ProfileScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val sess by viewModel.session.collectAsState()
    val isDark by viewModel.isDark.collectAsState()
    val isUploading by viewModel.isUploadingAvatar.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var usernameEditor by remember { mutableStateOf(sess.username ?: "") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(uri) { processing ->
                if (!processing) {
                    Toast.makeText(context, "Avatar berhasil diunggah!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Toolbar header elevation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Profil Pengguna",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive user Avatar
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (sess.avatarUrl.isNullOrEmpty()) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                } else {
                    AsyncImage(
                        model = sess.avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Loader overlay during Cloudinary uploads
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Sentuh Foto Untuk Mengubah", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 11.sp)

            Spacer(modifier = Modifier.height(30.dp))

            // User Info Cards
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Email", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text(text = sess.email ?: "-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Nama Pengguna (Username)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = usernameEditor,
                        onValueChange = { usernameEditor = it },
                        modifier = Modifier.fillMaxWidth().testTag("profile_username_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.updateProfileUsername(usernameEditor) {
                                Toast.makeText(context, "Username berhasil diubah!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("profile_save_btn")
                    ) {
                        Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // User role notice badge
            if (sess.isAdmin) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF331E1E) else Color(0xFFFFEBEE))
                        .border(1.dp, Color.Red, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = "Tingkatan Pengguna: Administrator (ADMIN)", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout Button
            Button(
                onClick = {
                    viewModel.logout {
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0x33FF0000) else Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_logout_btn")
            ) {
                Text("Keluar (Logout)", color = Color.Red, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ================================================================
// 10. ADMIN PANEL SCREEN
// ================================================================

@Composable
fun AdminPanelScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val users by viewModel.adminUsers.collectAsState()
    val announcements by viewModel.adminAnnouncements.collectAsState()
    val featured by viewModel.adminFeatured.collectAsState()
    val blacklist by viewModel.adminBlacklist.collectAsState()
    val isLoading by viewModel.isAdminLoading.collectAsState()
    val banStatusMessage by viewModel.banStatusMessage.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    LaunchedEffect(banStatusMessage) {
        banStatusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Users, 1: Announcements, 2: Slider, 3: Blacklist

    // Announcements adding inputs state
    var annTitle by remember { mutableStateOf("") }
    var annMessage by remember { mutableStateOf("") }
    var annDownloadUrl by remember { mutableStateOf("") }

    // Manual Slider adding state
    var sliderSlug by remember { mutableStateOf("") }
    var sliderTitle by remember { mutableStateOf("") }
    var sliderPoster by remember { mutableStateOf("") }
    var sliderOrder by remember { mutableStateOf("0") }

    // Blacklist adding state
    var blacklistSlug by remember { mutableStateOf("") }
    var blacklistTitle by remember { mutableStateOf("") }
    var blacklistReason by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAdminDetails()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Panel Kontrol Admin",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Kelola pengguna, konten, dan pengaturan aplikasi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Subheader Tab selection (horizontal scroll, pill-style chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sections = listOf(
                "Manajemen User" to Icons.Default.Group,
                "Pengumuman" to Icons.Default.Campaign,
                "Hero Slider" to Icons.Default.ViewCarousel,
                "Blacklist Anime" to Icons.Default.Block
            )
            sections.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedTab == index
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selectedTab = index }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

        if (isLoading) {
            LoadingScreen("Memuat data...")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Section A: Users List
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("List Seluruh Pengguna", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("${users.size} pengguna", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        users.forEach { usr ->
                            val isBanned = usr.is_banned == true
                            val statusColor = if (isBanned) Color(0xFFFF5252) else Color(0xFF4CAF50)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Avatar with status ring
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, statusColor, CircleShape)
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AvatarCircle(
                                                avatarUrl = usr.avatar_url,
                                                username = usr.username ?: "?",
                                                size = 44.dp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = usr.username ?: "Tamu",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                if (usr.is_admin == true) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    AdminBadge()
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(statusColor)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = if (isBanned) "Banned" else "Aktif",
                                                    color = statusColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "  •  ${if (usr.is_admin == true) "Admin" else "Pengguna"}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.toggleUserBanStatus(usr) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isBanned) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            elevation = ButtonDefaults.buttonElevation(0.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isBanned) Icons.Default.LockOpen else Icons.Default.Block,
                                                contentDescription = null,
                                                tint = if (isBanned) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isBanned) "Aktifkan" else "Ban",
                                                color = if (isBanned) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        // Reset password option triggers recovery email link from Supabase auth
                                        IconButton(
                                            onClick = {
                                                viewModel.sendAuthRecovery(usr.id) { sent ->
                                                    if (sent) Toast.makeText(context, "Recovery email sent successfully", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Password reset link", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Section B: Announcements Create Form
                        Text("Tambah Pengumuman Baru", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        TextField(
                            value = annTitle,
                            onValueChange = { annTitle = it },
                            placeholder = { Text("Judul Pengumuman") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = annMessage,
                            onValueChange = { annMessage = it },
                            placeholder = { Text("Isi Pesan Pengumuman") },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = annDownloadUrl,
                            onValueChange = { annDownloadUrl = it },
                            placeholder = { Text("Link Download (opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (annTitle.isNotBlank() && annMessage.isNotBlank()) {
                                    viewModel.saveAnnouncement(null, annTitle, annMessage, true, annDownloadUrl.ifBlank { null })
                                    annTitle = ""
                                    annMessage = ""
                                    annDownloadUrl = ""
                                    Toast.makeText(context, "Pengumuman berhasil dipublikasi!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Publikasikan Tambah Pengumuman")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Daftar Pengumuman Aktif", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        
                        if (announcements.isEmpty()) {
                            Text("Tidak ada pengumuman.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp))
                        } else {
                            announcements.forEach { ann ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = ann.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            Text(text = ann.message, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { viewModel.deleteAnnouncement(ann.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Section C: Featured Slider Overwrite Hero Manual
                        Text("Tambahkan Hero Banner Baru", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        TextField(
                            value = sliderSlug,
                            onValueChange = { sliderSlug = it },
                            placeholder = { Text("Slug Anime (Unik)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = sliderTitle,
                            onValueChange = { sliderTitle = it },
                            placeholder = { Text("Judul Anime") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = sliderPoster,
                            onValueChange = { sliderPoster = it },
                            placeholder = { Text("Link URL Poster") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = sliderOrder,
                            onValueChange = { sliderOrder = it },
                            placeholder = { Text("Nomor Indeks Urutan (0, 1, 2...)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val idx = sliderOrder.toIntOrNull() ?: 0
                                if (sliderSlug.isNotBlank()) {
                                    viewModel.saveFeaturedAnime(sliderSlug.trim(), sliderTitle.trim(), sliderPoster.trim(), idx)
                                    sliderSlug = ""
                                    sliderTitle = ""
                                    sliderPoster = ""
                                    sliderOrder = "0"
                                    Toast.makeText(context, "Featured Anime ditambahkan!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tambahkan ke Hero Banner")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("List Manual Hero Banner Aktif", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        
                        if (featured.isEmpty()) {
                            Text("Tidak ada list manual. Banner otomatis menggunakan ongoing anime.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp))
                        } else {
                            featured.forEach { ft ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = ft.anime_poster,
                                            contentDescription = null,
                                            modifier = Modifier.size(45.dp, 65.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = ft.anime_title ?: ft.anime_slug, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            Text(text = "Order Index: ${ft.order_index ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { viewModel.deleteFeaturedAnime(ft.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // Section D: Blacklist Management
                        Text("Blacklist / Aturan Sembunyikan Anime", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        TextField(
                            value = blacklistSlug,
                            onValueChange = { blacklistSlug = it },
                            placeholder = { Text("Slug Anime Yang Ingin Disembunyikan") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = blacklistTitle,
                            onValueChange = { blacklistTitle = it },
                            placeholder = { Text("Judul Anime (Opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = blacklistReason,
                            onValueChange = { blacklistReason = it },
                            placeholder = { Text("Alasan disembunyikan (Opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (blacklistSlug.isNotBlank()) {
                                    viewModel.saveBlacklistAnime(blacklistSlug.trim(), blacklistTitle.trim(), blacklistReason.trim())
                                    blacklistSlug = ""
                                    blacklistTitle = ""
                                    blacklistReason = ""
                                    Toast.makeText(context, "Anime berhasil diblacklist!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sembunyikan Anime")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Daftar Anime Yang Disembunyikan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        
                        if (blacklist.isEmpty()) {
                            Text("Tidak ada anime disembunyikan.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp))
                        } else {
                            blacklist.forEach { bl ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = bl.anime_title ?: bl.anime_slug, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            Text(text = "Slug: ${bl.anime_slug}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                                            bl.reason?.let { r ->
                                                if (r.isNotBlank()) {
                                                    Text(text = "Alasan: $r", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteBlacklistAnime(bl.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================================================================
// 11. SETTINGS SCREEN
// ================================================================

@Composable
private fun AnimatedSettingsItem(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 3 }
                )
    ) {
        content()
    }
}

@Composable
private fun SettingsNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconBgColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: AnikuViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val isDark by viewModel.isDark.collectAsState()
    val sess by viewModel.session.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val latestVersion by viewModel.latestVersion.collectAsState()
    val downloadUrl by viewModel.downloadUrl.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()
    val currentSource by viewModel.dataSource.collectAsState()

    // Header title scroll parallax
    val scrollState = rememberScrollState()
    val headerAlpha by animateFloatAsState(
        targetValue = if (scrollState.value > 80) 1f else 0f,
        animationSpec = tween(200), label = "headerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ── Hero Header ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Pengaturan",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Kelola akun & preferensi aplikasi",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Section A: Profile Card ──────────────────────────
                AnimatedSettingsItem(index = 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (sess.token.isNullOrEmpty()) {
                            // Guest state
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Masuk Sebagai Tamu",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Login untuk akses penuh",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Button(
                                    onClick = { navController.navigate("auth") },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("settings_login_btn")
                                ) {
                                    Text("Masuk", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        } else {
                            // Logged in state
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        AsyncImage(
                                            model = sess.avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface)
                                        )
                                        // Online dot
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                                .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sess.username ?: sess.email ?: "Pengguna",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (sess.isAdmin) Color(0xFFD32F2F).copy(alpha = 0.15f)
                                                        else accentColor.copy(alpha = 0.12f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (sess.isAdmin) "Admin" else "Member",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sess.isAdmin) Color(0xFFD32F2F) else accentColor
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { navController.navigate("profile") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_profile_btn"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ubah Profil", fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.logout {} },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f),
                                            contentColor = Color(0xFFEF5350)
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(0.dp)
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Keluar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Section B: Admin Panel ───────────────────────────
                if (!sess.token.isNullOrEmpty() && sess.isAdmin) {
                    AnimatedSettingsItem(index = 1) {
                        val pulseAnim = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by pulseAnim.animateFloat(
                            initialValue = 0.4f, targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ), label = "pulseAlpha"
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF2A1515) else Color(0xFFFFF0F0)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFD32F2F).copy(alpha = pulseAlpha), RoundedCornerShape(18.dp))
                                .clickable { navController.navigate("admin") }
                                .testTag("settings_admin_panel_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFD32F2F).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Panel Kontrol Admin",
                                        color = if (isDark) Color.White else Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Kelola Users, Banner Slider, & Blacklist",
                                        color = if (isDark) Color(0xFFEF9A9A) else Color(0xFFE53935),
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // ── Section Label: Preferensi ────────────────────────
                AnimatedSettingsItem(index = 2) {
                    Text(
                        text = "PREFERENSI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                AnimatedSettingsItem(index = 3) {
                    SettingsNavCard(
                        icon = Icons.Default.Palette,
                        iconBgColor = accentColor,
                        title = "Tampilan & Tema",
                        subtitle = "Tema, ukuran tulisan, layout & warna aksen",
                        onClick = { navController.navigate("tampilan") },
                        testTag = "settings_tampilan_btn"
                    )
                }

                AnimatedSettingsItem(index = 4) {
                    SettingsNavCard(
                        icon = Icons.Default.Apps,
                        iconBgColor = Color(0xFF29B6F6),
                        title = "Sumber Data",
                        subtitle = "Aktif: $currentSource",
                        onClick = { navController.navigate("sumber_data") }
                    )
                }

                AnimatedSettingsItem(index = 5) {
                    SettingsNavCard(
                        icon = Icons.Default.Security,
                        iconBgColor = Color(0xFF66BB6A),
                        title = "Keamanan",
                        subtitle = "Kunci aplikasi, PIN, sidik jari & info sesi",
                        onClick = { navController.navigate("keamanan") }
                    )
                }

                // ── Section Label: Dukung ────────────────────────────
                AnimatedSettingsItem(index = 6) {
                    Text(
                        text = "DUKUNG KAMI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // ── Donation Card ────────────────────────────────────
                AnimatedSettingsItem(index = 7) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF0F1F10) else Color(0xFFF1F8E9)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("☕", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Dukung Aniku",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isDark) Color.White else Color(0xFF2E7D32)
                                    )
                                    Text(
                                        "Bantu kelangsungan aplikasi",
                                        fontSize = 12.sp,
                                        color = if (isDark) Color(0xFF81C784) else Color(0xFF43A047)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFF4CAF50).copy(alpha = 0.15f))
                            // Saweria row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF1B3A1B) else Color(0xFFDCEDC8))
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://saweria.co/Dayynime")))
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("☕", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Saweria", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (isDark) Color.White else Color(0xFF2E7D32))
                                    Text("saweria.co/Dayynime", fontSize = 11.sp, color = if (isDark) Color(0xFF81C784) else Color(0xFF43A047))
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            }
                            // Trakteer row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF3A1A1A) else Color(0xFFFFE0E0))
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://trakteer.id/Dayynimee")))
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🧡", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Trakteer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (isDark) Color.White else Color(0xFF7D2E2E))
                                    Text("trakteer.id/Dayynimee", fontSize = 11.sp, color = if (isDark) Color(0xFFEF9A9A) else Color(0xFFD32F2F))
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // ── Section Label: Aplikasi ──────────────────────────
                AnimatedSettingsItem(index = 8) {
                    Text(
                        text = "APLIKASI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // ── Version Card ─────────────────────────────────────
                AnimatedSettingsItem(index = 9) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF16162A) else Color(0xFFF3E5F5)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = context.packageManager.getApplicationIcon(context.packageName),
                                    contentDescription = "App Icon",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Versi Aplikasi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isDark) Color.White else Color(0xFF4A148C)
                                    )
                                    Text(
                                        "Aniku v${BuildConfig.VERSION_NAME}",
                                        fontSize = 12.sp,
                                        color = if (isDark) Color(0xFFCE93D8) else Color(0xFF6A1B9A)
                                    )
                                }
                                if (updateAvailable) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFD32F2F))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Update!", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Terbaru ✓", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (updateCheckMessage.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = updateCheckMessage,
                                    color = if (updateAvailable) Color(0xFFEF5350) else Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (updateAvailable && downloadUrl.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        viewModel.downloadUpdate(downloadUrl, latestVersion)
                                        Toast.makeText(context, "Download dimulai, cek notifikasi", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download $latestVersion", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedButton(
                                onClick = { viewModel.checkForUpdate() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                enabled = !isCheckingUpdate
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = accentColor, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Memeriksa...", fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cek Update", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // ── Footer ───────────────────────────────────────────
                AnimatedSettingsItem(index = 10) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Aniku v${BuildConfig.VERSION_NAME}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        Text(
                            "Developer: Dayynime",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/profile.php?id=61588359607423")))
                            }
                        ) {
                            Text("Kunjungi Facebook Developer", color = accentColor.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Sticky TopBar saat scroll ────────────────────────────────
        AnimatedVisibility(
            visible = headerAlpha > 0.5f,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150), initialOffsetY = { -it }),
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150), targetOffsetY = { -it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        "Pengaturan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
fun TampilanScreen(
    viewModel: AnikuViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val isDark by viewModel.isDark.collectAsState()
    val textScale by viewModel.textSize.collectAsState()
    val activeAccent by viewModel.accentColorName.collectAsState()
    val activeGridLayout by viewModel.gridLayout.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TopHeader
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Tampilan & Tema",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section C: Theme preferences toggle
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pilihan Tema", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tema Gelap (Dark Mode)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = isDark,
                            onCheckedChange = { viewModel.toggleDarkMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            // Section D: Text size setting selectors
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ukuran Tulisan", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val sizes = listOf("Kecil", "Sedang", "Besar")
                        sizes.forEach { sz ->
                            val isSelected = textScale == sz
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.changeTextSize(sz) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sz,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section E: Grid Layout Selector
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Layout Kartu Anime", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val layouts = listOf(
                            "2" to Icons.Default.GridView,
                            "3" to Icons.Default.Apps,
                            "List" to Icons.Default.ViewList
                        )
                        layouts.forEach { (layout, icon) ->
                            val isSelected = activeGridLayout == layout
                            val label = when (layout) {
                                "2" -> "Grid 2"
                                "3" -> "Grid 3"
                                else -> "List"
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.changeGridLayout(layout) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section F: Color Accent Selector row
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pilihan Warna Aksen", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val accentList = listOf(
                            "Red" to Color(0xFFE53935),
                            "Green" to Color(0xFF4CAF50),
                            "Blue" to Color(0xFF2196F3),
                            "Purple" to Color(0xFF9C27B0),
                            "Orange" to Color(0xFFFF8C00)
                        )
                        accentList.forEach { (name, colHex) ->
                            val isSelected = activeAccent == name
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colHex)
                                    .border(3.dp, if (isSelected) (if (isDark) Color.White else Color.Black) else Color.Transparent, CircleShape)
                                    .clickable { viewModel.changeAccentColor(name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SumberDataScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val currentSource by viewModel.dataSource.collectAsState()
    val accentColorName by viewModel.accentColorName.collectAsState()
    val accentColor = getAccentColor(accentColorName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header — sama persis dengan Tampilan & Tema
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Sumber Data",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Pilih sumber anime yang digunakan",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val sources = listOf(
                "Dayynime-v1" to "Sumber utama (server 1)",
                "Dayynime-v2" to "Sumber alternatif (server 2)"
            )

            sources.forEach { (sourceKey, sourceDesc) ->
                val isSelected = currentSource == sourceKey
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            accentColor.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, accentColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.changeDataSource(sourceKey)
                            viewModel.loadHomeData()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sourceKey,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                sourceDesc,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isSelected,
                            onCheckedChange = {
                                viewModel.changeDataSource(sourceKey)
                                viewModel.loadHomeData()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TopSupporterScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val donations by viewModel.donations.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    // Group by supporter_name dan jumlahkan total_amount
    val leaderboard = donations
        .groupBy { it.supporter_name }
        .map { (name, list) -> name to list.sumOf { it.total_amount ?: 0 } }
        .sortedByDescending { it.second }

    LaunchedEffect(Unit) {
        viewModel.loadDonations()
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text("Top Supporter", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (leaderboard.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☕", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Belum ada supporter",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Jadilah yang pertama support Aniku!",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏆", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Hall of Fame",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            "Terima kasih sudah support Aniku!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                itemsIndexed(leaderboard) { index, (name, total) ->
                    val medal = when (index) {
                        0 -> "🥇"
                        1 -> "🥈"
                        2 -> "🥉"
                        else -> "${index + 1}."
                    }
                    val cardBg = when (index) {
                        0 -> Color(0xFF2a2000)
                        1 -> Color(0xFF1a1a1a)
                        2 -> Color(0xFF1a1500)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val nameColor = when (index) {
                        0 -> Color(0xFFFFD700)
                        1 -> Color(0xFFB0BEC5)
                        2 -> Color(0xFFCD7F32)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = if (index == 0) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                medal,
                                fontSize = if (index < 3) 24.sp else 16.sp,
                                modifier = Modifier.width(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = nameColor
                                )
                            }
                            Text(
                                formatRupiah(total),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = nameColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
