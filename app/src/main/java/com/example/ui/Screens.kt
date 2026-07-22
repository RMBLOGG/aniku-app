package com.example.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.util.orDefault
import com.example.util.nullIfBlank
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
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
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

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

// Scrim gradients dibikin SEKALI sebagai top-level constant (bukan tiap recomposition/tiap card),
// biar gak alokasi Brush baru + overdraw ganda tiap card muncul di layar pas scroll.
private val CardWideScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
)
private val CardPosterScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))
)
// Gabungan dari 2 gradient yang tadinya ditumpuk (drawBehind + background) jadi 1 gradient aja
private val CardRoundedScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.5f))
)

@Composable
fun AnimeCard(
    anime: AnimeRaw,
    accentColor: Color,
    onClick: () -> Unit,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean = true,
    onLoginRequired: () -> Unit = {},
    viewerCount: Int = 0,
    cardStyle: String = "Rounded"
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
    // Dulu ImageRequest dibikin ulang tiap recomposition (tiap card recompose gara-gara
    // viewerCounts/bookmark berubah) → alokasi objek sia-sia tiap kali. Sekarang di-remember,
    // cuma dibikin ulang kalau URL poster-nya beneran ganti.
    val posterRequest = remember(anime.poster) {
        ImageRequest.Builder(context).data(anime.poster).crossfade(300).build()
    }

    // corner radius berdasarkan style
    val cornerRadius = when (cardStyle) {
        "Sharp"  -> 4.dp
        "Poster" -> 12.dp
        "Wide"   -> 10.dp
        else     -> 16.dp // Rounded
    }

    // Wide (16:9) — layout berbeda
    if (cardStyle == "Wide") {
        Column(
            modifier = modifier
                .width(160.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(cornerRadius))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(CardWideScrim))
                anime.type?.let {
                    Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF8C00)).padding(horizontal = 5.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)) {
                        Text(it, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
                anime.episode?.let {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text(it, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { if (isLoggedIn) onBookmarkToggle() else onLoginRequired() },
                    modifier = Modifier.padding(4.dp).size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape).align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Bookmark", tint = if (isBookmarked) accentColor else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(anime.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp))
            anime.type?.let {
                Text(it + " · Sub Indo", color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                    fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        return
    }

    // Poster Only style — title overlay di dalam poster
    if (cardStyle == "Poster") {
        Box(
            modifier = modifier
                .width(115.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(cornerRadius))
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = anime.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(CardPosterScrim))
            anime.type?.let {
                Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFF8C00)).padding(horizontal = 5.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)) {
                    Text(it, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            IconButton(
                onClick = { if (isLoggedIn) onBookmarkToggle() else onLoginRequired() },
                modifier = Modifier.padding(2.dp).size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape).align(Alignment.TopEnd)
            ) {
                Icon(imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Bookmark", tint = if (isBookmarked) accentColor else Color.White,
                    modifier = Modifier.size(14.dp))
            }
            anime.episode?.let {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 26.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.6f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(it, color = Color.White, fontSize = 8.sp)
                }
            }
            Text(anime.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            if (viewerCount > 0) {
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF4CAF50), CircleShape))
                        Text(if (viewerCount >= 1000) "${viewerCount/1000}k" else "$viewerCount",
                            color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Rounded + Sharp — layout sama, beda corner radius
    Column(
        modifier = modifier
            .testTag("anime_card_${anime.slug}")
            .width(115.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(cornerRadius))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = anime.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dulu ada 2 gradient ditumpuk (drawBehind + background) → sekarang 1 gradient shared aja,
            // efek visualnya sama (gelap ke bawah) tapi draw call & alokasi Brush jauh lebih ringan.
            Box(modifier = Modifier.fillMaxSize().background(CardRoundedScrim))

            // Type Badge
            anime.type?.let { typeString ->
                if (typeString.isNotBlank()) {
                    Box(modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF8C00)).padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)) {
                        Text(text = typeString, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            // Bookmark
            IconButton(
                onClick = { if (isLoggedIn) onBookmarkToggle() else onLoginRequired() },
                modifier = Modifier.padding(4.dp).size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape).align(Alignment.TopEnd)
                    .testTag("bookmark_btn_${anime.slug}")
            ) {
                Icon(imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Bookmark", tint = if (isBookmarked) accentColor else Color.White,
                    modifier = Modifier.size(16.dp))
            }

            // Episode Badge
            anime.episode?.let { epString ->
                if (epString.isNotBlank()) {
                    Box(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = epString, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status Badge
            anime.status_or_day?.let { statusString ->
                if (statusString.isNotBlank()) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = statusString, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Viewer count
            if (viewerCount > 0) {
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF4CAF50), CircleShape))
                        Text(text = if (viewerCount >= 1000) "${viewerCount / 1000}k" else "$viewerCount",
                            color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(text = anime.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp))
    }
}

// Ekstrak label episode ringkas ("Ep 201") dari episode_slug, dipakai buat keterangan
// asal komentar di widget "Komentar Terbaru" (Home) yang nampilin komentar lintas episode.
private fun episodeLabelFromSlug(slug: String): String {
    val match = Regex("episode[-_]?(\\d+)", RegexOption.IGNORE_CASE).find(slug)
    return if (match != null) "Ep ${match.groupValues[1]}" else slug
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
private fun CrownIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.06f, h * 0.95f)
            lineTo(w * 0.06f, h * 0.42f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.5f, h * 0.10f)
            lineTo(w * 0.72f, h * 0.62f)
            lineTo(w * 0.94f, h * 0.42f)
            lineTo(w * 0.94f, h * 0.95f)
            close()
        }
        drawPath(path, color = tint)
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.88f),
            size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.10f)
        )
        drawCircle(color = tint, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.42f))
        drawCircle(color = tint, radius = w * 0.065f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.10f))
        drawCircle(color = tint, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.94f, h * 0.42f))
    }
}

@Composable
private fun HomeQuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "quickActionScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TopLeaderboardCard(
    topUsers: List<ProfileDto>,
    topSupporters: List<SupporterEntry> = emptyList(),
    accentColor: Color,
    onUserClick: (ProfileDto) -> Unit,
    onSeeAllClick: () -> Unit,
    onSeeAllSupportersClick: () -> Unit = onSeeAllClick,
    modifier: Modifier = Modifier
) {
    if (topUsers.isEmpty()) return

    // Dibagi per halaman isi 5 (podium: #4 #2 #1 #3 #5). Ini beneran dipecah dari
    // `topUsers` asli (bukan placeholder statis) — jadi begitu sumber datanya ganti
    // (season lain, server lain, dst) carousel-nya otomatis ikut ke-refresh karena
    // key `remember(topUsers)` di bawah ini.
    val pages = remember(topUsers) { topUsers.chunked(5) }
    // Slide pertama (index 0) khusus Top Supporter — cuma muncul kalau ada data donasi.
    // Sisanya (level leaderboard) digeser +1.
    val hasSupporterPage = topSupporters.isNotEmpty()
    val totalPages = pages.size + if (hasSupporterPage) 1 else 0
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val isOnSupporterPage = hasSupporterPage && pagerState.currentPage == 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1B1330), Color(0xFF14213F), Color(0xFF0A0E1A))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x55FFD54F), Color(0x334FC3F7), Color(0x22FFD54F))
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Radial glow lembut di belakang podium biar kesan premium, bukan flat.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(220.dp)
                .offset(y = (-60).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x33FFD54F), Color.Transparent)
                    )
                )
        )

        Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = if (isOnSupporterPage) onSeeAllSupportersClick else onSeeAllClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isOnSupporterPage) {
                        Text(text = "🏆", fontSize = 13.sp)
                    } else {
                        CrownIcon(
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFFFFD54F)
                        )
                    }
                    Text(
                        text = if (isOnSupporterPage) "TOP SUPPORTER" else "TOP LEADERBOARD",
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFE082), Color(0xFFFFC107))
                            )
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lihat Semua",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "  ›",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                // Efek swipe: halaman yang lagi di-scroll keluar mengecil & memudar,
                // halaman aktif full scale & full alpha — kesan carousel modern.
                val pageOffset = pagerState.currentPage - page + pagerState.currentPageOffsetFraction
                val distance = if (pageOffset < 0) -pageOffset else pageOffset
                val clamped = distance.coerceIn(0f, 1f)
                val pageModifier = Modifier.graphicsLayer {
                    scaleX = 1f - (0.14f * clamped)
                    scaleY = 1f - (0.14f * clamped)
                    alpha = 1f - (0.65f * clamped)
                }

                if (hasSupporterPage && page == 0) {
                    SupporterPodiumPage(
                        supporters = topSupporters.take(5),
                        modifier = pageModifier
                    )
                } else {
                    val levelPageIndex = if (hasSupporterPage) page - 1 else page
                    LeaderboardPodiumPage(
                        users = pages[levelPageIndex],
                        startRank = levelPageIndex * 5 + 1,
                        onUserClick = onUserClick,
                        modifier = pageModifier
                    )
                }
            }

            if (totalPages > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                PageIndicatorDots(
                    pageCount = totalPages,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// Satu halaman podium leaderboard: rank #1 di tengah (paling besar + mahkota),
// diapit #2/#3, lalu #4/#5 di ujung — susunan visual kek referensi "Top 5 Level".
@Composable
private fun LeaderboardPodiumPage(
    users: List<ProfileDto>,
    startRank: Int,
    onUserClick: (ProfileDto) -> Unit,
    modifier: Modifier = Modifier
) {
    // urutan slot kiri→kanan berdasarkan offset rank dari startRank: #4 #2 #1 #3 #5
    val slotOffsets = listOf(3, 1, 0, 2, 4)
    Row(
        // Tinggi 150dp — dihitung dari konten paling tinggi (rank #1: mahkota 18 + avatar
        // 60 + nama + level + badge ≈ 135dp) + buffer. HorizontalPager butuh tinggi pasti
        // per halaman (dia dipanggil di dalam LazyColumn yang scrollable), makanya nggak
        // bisa dibikin wrap-content — tapi angkanya sekarang cukup, nggak kepotong lagi.
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        slotOffsets.forEachIndexed { i, offset ->
            val user = users.getOrNull(offset)
            if (user != null) {
                val rank = startRank + offset
                PodiumAvatar(
                    rank = rank,
                    user = user,
                    size = when (offset) {
                        0 -> 60.dp
                        1, 2 -> 50.dp
                        else -> 42.dp
                    },
                    // Muncul bergantian dari luar ke dalam biar berasa hidup tiap
                    // halaman carousel di-render — bukan langsung nongol semua.
                    entranceDelayMs = when (offset) {
                        0 -> 150
                        1, 2 -> 80
                        else -> 0
                    },
                    entranceKey = "$startRank-$rank",
                    onClick = { onUserClick(user) }
                )
            } else {
                Spacer(modifier = Modifier.width(58.dp))
            }
        }
    }
}

// Data satu baris Top Supporter buat podium: nama, total donasi, dan avatar_url
// kalau nama donaturnya matched sama akun terdaftar (userDirectory) — kalau ga match,
// avatarUrl null dan fallback ke lingkaran inisial.
private data class SupporterEntry(
    val name: String,
    val amount: Int,
    val avatarUrl: String? = null
)

// Halaman podium khusus Top Supporter (donasi) — dipakai sebagai slide PERTAMA
// di carousel TOP LEADERBOARD. Susunan sama kayak podium level: #4 #2 #1 #3 #5.
// Avatar pakai foto asli kalau nama donatur match sama akun terdaftar, kalau ga
// ketemu (donasi anonim/nama beda) fallback ke lingkaran inisial gold/silver/bronze.
@Composable
private fun SupporterPodiumPage(
    supporters: List<SupporterEntry>,
    modifier: Modifier = Modifier
) {
    val slotOffsets = listOf(3, 1, 0, 2, 4)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        slotOffsets.forEachIndexed { i, offset ->
            val supporter = supporters.getOrNull(offset)
            if (supporter != null) {
                val rank = offset + 1
                SupporterPodiumAvatar(
                    rank = rank,
                    name = supporter.name,
                    amount = supporter.amount,
                    avatarUrl = supporter.avatarUrl,
                    size = when (offset) {
                        0 -> 60.dp
                        1, 2 -> 50.dp
                        else -> 42.dp
                    },
                    entranceDelayMs = when (offset) {
                        0 -> 150
                        1, 2 -> 80
                        else -> 0
                    },
                    entranceKey = "supporter-$rank"
                )
            } else {
                Spacer(modifier = Modifier.width(58.dp))
            }
        }
    }
}

@Composable
private fun SupporterPodiumAvatar(
    rank: Int,
    name: String,
    amount: Int,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    entranceDelayMs: Int = 0,
    entranceKey: Any? = null
) {
    val ringBrush = when (rank) {
        1 -> Brush.linearGradient(colors = listOf(Color(0xFFFFF3B0), Color(0xFFFFC107), Color(0xFFFFD54F)))
        2 -> Brush.linearGradient(colors = listOf(Color(0xFFECEFF1), Color(0xFF90A4AE)))
        3 -> Brush.linearGradient(colors = listOf(Color(0xFFE6B98A), Color(0xFFB07040)))
        else -> Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.12f)))
    }
    val avatarFill = when (rank) {
        1 -> Brush.linearGradient(colors = listOf(Color(0xFFFFE082), Color(0xFFFFC107)))
        2 -> Brush.linearGradient(colors = listOf(Color(0xFFECEFF1), Color(0xFFB0BEC5)))
        3 -> Brush.linearGradient(colors = listOf(Color(0xFFE6B98A), Color(0xFFB07040)))
        else -> Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f)))
    }
    val chipColor = when (rank) {
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFFCFD8DC)
        3 -> Color(0xFFCE8B5B)
        else -> Color.White.copy(alpha = 0.22f)
    }
    val chipTextColor = if (rank <= 3) Color(0xFF1B1330) else Color.White

    var visible by remember(entranceKey) { mutableStateOf(false) }
    LaunchedEffect(entranceKey) {
        visible = false
        delay(entranceDelayMs.toLong())
        visible = true
    }

    val infinite = rememberInfiniteTransition(label = "supporterPodiumPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "supporterPulseAlpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + scaleIn(initialScale = 0.7f, animationSpec = tween(320))
    ) {
        Column(
            modifier = Modifier.width(66.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (rank == 1) {
                CrownIcon(
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { alpha = pulse },
                    tint = Color(0xFFFFD54F)
                )
                Spacer(modifier = Modifier.height(3.dp))
            } else {
                Spacer(modifier = Modifier.height(21.dp))
            }

            Box(
                modifier = Modifier
                    .size(size)
                    .then(
                        if (rank == 1) Modifier.graphicsLayer { alpha = 0.85f + (0.15f * pulse) }
                        else Modifier
                    )
                    .clip(CircleShape)
                    .background(if (avatarUrl.isNullOrBlank()) avatarFill else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))))
                    .border(if (rank == 1) 3.dp else 2.dp, ringBrush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNullOrBlank()) {
                    Text(
                        text = (name.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "?").uppercase(),
                        color = Color(0xFF1B1330),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.4f).sp
                    )
                } else {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = name.ifBlank { "Anonim" },
                color = if (rank == 1) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                fontSize = if (rank == 1) 11.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = formatRupiah(amount),
                color = Color(0xFFFFD54F).copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "#$rank",
                    color = chipTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PodiumAvatar(
    rank: Int,
    user: ProfileDto,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    entranceDelayMs: Int = 0,
    entranceKey: Any? = null
) {
    val ringBrush = when (rank) {
        1 -> Brush.linearGradient(colors = listOf(Color(0xFFFFF3B0), Color(0xFFFFC107), Color(0xFFFFD54F)))
        2 -> Brush.linearGradient(colors = listOf(Color(0xFFECEFF1), Color(0xFF90A4AE)))
        3 -> Brush.linearGradient(colors = listOf(Color(0xFFE6B98A), Color(0xFFB07040)))
        else -> Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.12f)))
    }
    // Solid, bukan gradient tipis — biar angka rank di dalamnya PASTI kebaca,
    // bukan cuma pill kosong kayak sebelumnya.
    val chipColor = when (rank) {
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFFCFD8DC)
        3 -> Color(0xFFCE8B5B)
        else -> Color.White.copy(alpha = 0.22f)
    }
    val chipTextColor = if (rank <= 3) Color(0xFF1B1330) else Color.White

    // Entrance: fade + scale-in bertahap
    var visible by remember(entranceKey) { mutableStateOf(false) }
    LaunchedEffect(entranceKey) {
        visible = false
        delay(entranceDelayMs.toLong())
        visible = true
    }

    // Pulse lembut khusus rank #1 — mahkota & ring "bernapas"
    val infinite = rememberInfiniteTransition(label = "podiumPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "podiumPulseAlpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + scaleIn(initialScale = 0.7f, animationSpec = tween(320))
    ) {
        Column(
            modifier = Modifier.width(66.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (rank == 1) {
                CrownIcon(
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { alpha = pulse },
                    tint = Color(0xFFFFD54F)
                )
                Spacer(modifier = Modifier.height(3.dp))
            } else {
                Spacer(modifier = Modifier.height(21.dp))
            }

            Box(
                modifier = Modifier
                    .size(size)
                    .then(
                        if (rank == 1) Modifier.graphicsLayer { alpha = 0.85f + (0.15f * pulse) }
                        else Modifier
                    )
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(if (rank == 1) 3.dp else 2.dp, ringBrush, CircleShape)
                    .clickable(onClick = onClick)
            ) {
                AsyncImage(
                    model = user.avatar_url,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = user.username.orDefault("Anonim"),
                color = if (rank == 1) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                fontSize = if (rank == 1) 11.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = "Lv. ${user.season_level ?: 1}",
                color = Color(0xFF4FC3F7),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "#$rank",
                    color = chipTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

// Titik indikator halaman untuk carousel podium — dot aktif melebar jadi pill
// gradasi gold, mirip indikator "Top 5 Level" di referensi.
@Composable
private fun PageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { i ->
            val active = i == currentPage
            val width by animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                animationSpec = tween(250),
                label = "dotWidth"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        brush = if (active)
                            Brush.horizontalGradient(colors = listOf(Color(0xFFFFD54F), Color(0xFF4FC3F7)))
                        else
                            Brush.horizontalGradient(colors = listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.2f)))
                    )
            )
        }
    }
}

@Composable
private fun TopUserRow(
    rank: Int,
    user: ProfileDto,
    accentColor: Color,
    delayMs: Int = 0,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(user.id, rank) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 5 })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.width(30.dp)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                AsyncImage(
                    model = user.avatar_url,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username.orDefault("Anonim"),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Level ${user.season_level ?: 1}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
            Text(
                text = "${user.season_xp ?: 0} XP",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
    val completedList by viewModel.homeCompleted.collectAsState()
    val todayScheduleList by viewModel.homeTodaySchedule.collectAsState()
    val cardStyle by viewModel.cardStyle.collectAsState()
    val slidesList by viewModel.featuredSlides.collectAsState()
    val activeAnnouncement by viewModel.activeAnnouncement.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    // Set slug utk lookup O(1), dihitung ulang cuma saat list bookmark berubah (bukan tiap item/tiap scroll)
    val bookmarkedSlugs = remember(bookmarkedAnimes) { bookmarkedAnimes.mapTo(HashSet(bookmarkedAnimes.size)) { it.slug } }
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null
    val unreadPrivateChatCount by viewModel.unreadPrivateChatCount.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    var showLoginDialog by remember { mutableStateOf(false) }
    // Lambda stabil dishare ke semua card di screen ini, jadi gak bikin instance baru tiap card/tiap recomposition
    val onShowLoginDialog = remember { { showLoginDialog = true } }
    val viewerCounts by viewModel.viewerCounts.collectAsState()
    val myClanDetail by viewModel.myClanDetail.collectAsState()
    val recentComments by viewModel.recentComments.collectAsState()
    val commentsEnabled by viewModel.remoteConfigManager.commentsEnabled.collectAsState()
    val clanTagMap by viewModel.clanTagMap.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecentComments()
        viewModel.loadClanTagMap()
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.loadMyClanMembership()
    }

    LaunchedEffect(ongoingList, recentList, popularList, slidesList) {
        val slugs = (ongoingList + recentList + popularList).map { it.slug } + slidesList.map { it.anime_slug }
        if (slugs.isNotEmpty()) viewModel.startViewerCountPolling(slugs.distinct())
    }

    LaunchedEffect(Unit) {
        viewModel.loadChatMessages()
    }

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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                                    .heightIn(max = 260.dp)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .verticalScroll(rememberScrollState())
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

    if (isHomeLoading) {
        LoadingScreen("Memuat data anime...")
    } else if (homeError != null) {
        val currentSource by viewModel.dataSource.collectAsState()
        val servers = listOf(
            "Dayynime-v1" to "Server 1 (Utama)",
            "Dayynime-v2" to "Server 2 (Alternatif)",
            "Dayynime-v3" to "Server 3 (Animekompi)",
            "Dayynime-v4" to "Server 4 (Donghua)"
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
                                viewModel.loadSearchPopular()
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
            // ── Hero: Profile card + EXP + Premium + Carousel + Search + Global Chat ──
            item {
                val seasonLevel by viewModel.seasonLevel.collectAsState()
                val seasonXp by viewModel.seasonXp.collectAsState()
                val diamondBalance by viewModel.diamondBalance.collectAsState()
                val userDirectory by viewModel.userDirectory.collectAsState()
                val chatMessages by viewModel.chatMessages.collectAsState()

                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        viewModel.loadSeasonProgress()
                        viewModel.refreshProfile()
                    }
                    viewModel.loadUserDirectory()
                }

                val currentLevelBaseXp = 20 * (seasonLevel - 1) * (seasonLevel - 1)
                val nextLevelXp = 20 * seasonLevel * seasonLevel
                val xpIntoLevel = (seasonXp - currentLevelBaseXp).coerceAtLeast(0)
                val xpNeededForLevel = (nextLevelXp - currentLevelBaseXp).coerceAtLeast(1)
                val xpProgress = (xpIntoLevel.toFloat() / xpNeededForLevel.toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF4A0E10),
                                    Color(0xFF2A0808),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                ) {
                    Column {
                        Spacer(modifier = Modifier.statusBarsPadding())
                        // ── Profile card (avatar + level + exp) ──
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                        // ── Profile row ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.25f))
                                    .clickable { if (isLoggedIn) navController.navigate("profile") else onShowLoginDialog() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoggedIn && !session.avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = session.avatarUrl,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = (session.username?.take(1)?.uppercase()) ?: "?",
                                        color = accentColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLoggedIn) session.username.orDefault("Otaku") else "Halo, Otaku!",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        )
                                        Text(
                                            text = "Lvl. $seasonLevel",
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (isLoggedIn) {
                                        session.userNumber?.let { num ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(Color.White.copy(alpha = 0.08f))
                                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "#$num",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        myClanDetail?.let { clan ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(Color(0xFF7B2FBF).copy(alpha = 0.3f), Color(0xFF2FA8BF).copy(alpha = 0.3f))
                                                        )
                                                    )
                                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Shield,
                                                    contentDescription = "Clan",
                                                    tint = Color(0xFF5FC9DE),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "[${clan.tag}]",
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Pengaturan
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { navController.navigate("settings") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Pengaturan",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Pertemanan / Private Chat
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { if (isLoggedIn) navController.navigate("friends") else onShowLoginDialog() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ChatBubbleOutline,
                                        contentDescription = "Pertemanan",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                if (unreadPrivateChatCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-3).dp)
                                            .heightIn(min = 16.dp)
                                            .widthIn(min = 16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                            .padding(horizontal = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadPrivateChatCount > 9) "9+" else "$unreadPrivateChatCount",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // ── EXP bar (masih di dalam profile card) ──
                        if (isLoggedIn) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Exp", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("$xpIntoLevel/$xpNeededForLevel", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(xpProgress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.7f), accentColor)))
                                )
                            }
                        }
                        } // tutup profile card

                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Diamond + Clan + Gacha ──
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Diamond dibikin compact (cuma ikon+angka, tanpa label "Diamond")
                            // biar dua tombol aksi di sebelahnya tetep dapet ruang yang layak.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { navController.navigate("diamond_topup") }
                                    .padding(horizontal = 11.dp, vertical = 9.dp)
                            ) {
                                Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(14.dp))
                                Text("$diamondBalance", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF641015), Color(0xFF8C1A20))))
                                    .clickable { if (isLoggedIn) navController.navigate("clans") else onShowLoginDialog() }
                                    .padding(vertical = 9.dp)
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Clan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF4A1B8C), Color(0xFF7B2FD6))))
                                    .clickable { if (isLoggedIn) navController.navigate("gacha") else onShowLoginDialog() }
                                    .padding(vertical = 9.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Gacha", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Premium / Top-Up banner ──
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .clickable { navController.navigate("diamond_topup") }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFB800), Color(0xFFFF7A00))))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text("Premium", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Top-Up Diamond", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isLoggedIn) "Saldo kamu: $diamondBalance Diamond" else "Isi Diamond, dukung Aniku & naik level",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // ── Hero carousel: featured anime ──
                        if (slidesList.isNotEmpty()) {
                            val pagerState = rememberPagerState(pageCount = { slidesList.size })
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) { page ->
                                val slide = slidesList[page]
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 10f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { onNavigateToDetail(slide.anime_slug) }
                                ) {
                                    AsyncImage(
                                        model = slide.anime_poster,
                                        contentDescription = slide.anime_title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                                    startY = 120f
                                                )
                                            )
                                    )
                                    // Rank badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text("#${page + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    // Viewer count chip
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = "%,d".format(viewerCounts[slide.anime_slug] ?: 0).replace(",", "."),
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = slide.anime_title.orDefault(""),
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(slidesList.size) { idx ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(if (pagerState.currentPage == idx) 18.dp else 6.dp, 6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (pagerState.currentPage == idx) accentColor
                                                else Color.White.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // ── Search bar (decorative, buka halaman pencarian) ──
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                                .clickable { navController.navigate("search") }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Cari Anime Di Sini", color = Color.White.copy(alpha = 0.4f), fontSize = 13.5.sp)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // ── Top Leaderboard / Top Supporter ──
                        val topUsers = remember(userDirectory) {
                            userDirectory.sortedByDescending { it.season_xp ?: 0 }.take(10)
                        }
                        val donationsForHome by viewModel.donations.collectAsState()
                        val topSupporters = remember(donationsForHome, userDirectory) {
                            donationsForHome
                                .groupBy { it.supporter_name }
                                .map { (name, list) -> (name ?: "Anonim") to list.sumOf { it.total_amount ?: 0 } }
                                .sortedByDescending { it.second }
                                .take(5)
                                .map { (name, amount) ->
                                    val matchedUser = userDirectory.firstOrNull {
                                        it.username?.trim()?.equals(name.trim(), ignoreCase = true) == true
                                    }
                                    SupporterEntry(
                                        name = name,
                                        amount = amount,
                                        avatarUrl = matchedUser?.avatar_url
                                    )
                                }
                        }

                        if (topUsers.isNotEmpty()) {
                            TopLeaderboardCard(
                                topUsers = topUsers,
                                topSupporters = topSupporters,
                                accentColor = accentColor,
                                onUserClick = { user -> navController.navigate("user_profile/${user.id}") },
                                onSeeAllClick = { navController.navigate("user_list") },
                                onSeeAllSupportersClick = { navController.navigate("top_supporter") },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Global chat preview ──
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "GLOBAL CHAT",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val latestMsg = chatMessages.lastOrNull()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (isLoggedIn) navController.navigate("chat") else onShowLoginDialog() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!latestMsg?.avatar_url.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = latestMsg?.avatar_url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = latestMsg?.username?.take(1)?.uppercase() ?: "?",
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(androidx.compose.ui.text.SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold)) {
                                            append(latestMsg?.username ?: "Belum ada obrolan")
                                        }
                                        if (latestMsg != null) {
                                            append(": ")
                                            withStyle(androidx.compose.ui.text.SpanStyle(color = Color.White.copy(alpha = 0.65f))) {
                                                append(latestMsg.message)
                                            }
                                        }
                                    },
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Buka Room Chat",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
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
                        TextButton(onClick = { navController.navigate("watch_history") }) {
                            Text("LIHAT SEMUA", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchHistory, key = { "${it.animeSlug}_${it.episodeSlug}" }) { item ->
                            // Variant B — Horizontal compact card
                            Box(
                                modifier = Modifier
                                    .width(260.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { onNavigateToDetail(item.animeSlug) }
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    // Poster kiri
                                    Box(
                                        modifier = Modifier
                                            .width(56.dp)
                                            .fillMaxHeight()
                                    ) {
                                        AsyncImage(
                                            model = item.animePoster,
                                            contentDescription = item.animeTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Fade ke kanan
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            MaterialTheme.colorScheme.surface
                                                        ),
                                                        startX = 0f,
                                                        endX = 140f
                                                    )
                                                )
                                        )
                                    }

                                    // Info tengah
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(start = 4.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.animeTitle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 15.sp
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(accentColor.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = item.episodeTitle,
                                                    color = accentColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            val relTime = run {
                                                val diff = System.currentTimeMillis() - item.watchedAt
                                                val m = diff / 60_000; val h = diff / 3_600_000; val d = diff / 86_400_000
                                                when { m < 1 -> "Baru saja"; m < 60 -> "${m}m lalu"; h < 24 -> "${h}j lalu"; d < 7 -> "${d}h lalu"; else -> "${d}h lalu" }
                                            }
                                            Text(
                                                text = relTime,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                            )
                                        }
                                    }

                                    // Play icon kanan
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircleOutline,
                                            contentDescription = "Play",
                                            tint = accentColor.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
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
                    itemsIndexed(ongoingList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                            isBookmarked = bookmarkedSlugs.contains(anim.slug),
                            onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = onShowLoginDialog,
                            viewerCount = viewerCounts[anim.slug] ?: 0,
                        cardStyle = cardStyle
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
                    itemsIndexed(recentList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                            isBookmarked = bookmarkedSlugs.contains(anim.slug),
                            onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = onShowLoginDialog,
                            viewerCount = viewerCounts[anim.slug] ?: 0,
                        cardStyle = cardStyle
                        )
                    }
                }
            }

            // Section 2.5: Komentar Terbaru — widget ringkas, horizontal-scroll biar gak makan tempat
            if (commentsEnabled && recentComments.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Komentar Terbaru",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        itemsIndexed(recentComments, key = { idx, it -> "${it.id}_${idx}" }) { _, c ->
                            val isAdmin = c.role == "admin" || c.is_admin == true
                            val isMod = c.role == "moderator"
                            val ringColor = when {
                                isAdmin -> Color(0xFFFFC107)
                                isMod -> Color(0xFFB388FF)
                                else -> Color.Transparent
                            }
                            val timeStr = remember(c.created_at) {
                                try {
                                    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                                    parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    val date = parser.parse(c.created_at.take(19)) ?: java.util.Date()
                                    val now = System.currentTimeMillis()
                                    val diffMin = (now - date.time) / 60000
                                    when {
                                        diffMin < 1 -> "Baru saja"
                                        diffMin < 60 -> "${diffMin}m lalu"
                                        diffMin < 1440 -> "${diffMin / 60}j lalu"
                                        else -> "${diffMin / 1440}h lalu"
                                    }
                                } catch (e: Exception) { "" }
                            }
                            ElevatedCard(
                                modifier = Modifier.width(260.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (ringColor != Color.Transparent)
                                                    Modifier.border(1.5.dp, ringColor, CircleShape).padding(1.5.dp)
                                                else Modifier
                                            )
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!c.avatar_url.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = c.avatar_url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                c.username.take(1).uppercase(),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            GlossyGradientText(
                                                text = c.username,
                                                colors = when {
                                                    isAdmin -> adminGradientColors
                                                    isMod -> moderatorGradientColors
                                                    else -> defaultNameGradientColors
                                                },
                                                fontSize = 12.sp
                                            )
                                            c.season_level?.let { lvl ->
                                                GlossyGradientText(text = "Lv.$lvl", colors = levelGradientColors, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                            c.user_number?.let { num ->
                                                GlossyGradientText(text = "#$num", colors = idGradientColors, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                            clanTagMap[c.user_id]?.let { (tag, _) -> ClanTagBadge(tag) }
                                            if (isAdmin) {
                                                GlossyGradientText(text = "ADMIN", colors = adminGradientColors, fontSize = 9.sp, letterSpacing = 0.4.sp)
                                            } else if (isMod) {
                                                GlossyGradientText(text = "MODERATOR", colors = moderatorGradientColors, fontSize = 9.sp, letterSpacing = 0.4.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        (c.anime_title)?.let { title ->
                                            Text(
                                                text = title,
                                                color = accentColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                        Text(
                                            text = c.message,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = timeStr,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontSize = 10.sp)
                                            Text(
                                                text = episodeLabelFromSlug(c.episode_slug),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            c.source?.let { src ->
                                                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontSize = 10.sp)
                                                Text(
                                                    text = src,
                                                    color = accentColor.copy(alpha = 0.85f),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
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

            // Section 3: Terpopuler — Yoredaze style dengan ranking
            item { SectionHeader(title = "Terpopuler", onSeeAllClick = { onSeeAllClicked("Popular") }) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    popularList.take(8).forEachIndexed { index, anim ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                                .clickable { onNavigateToDetail(anim.slug) }
                        ) {
                            // Background grayscale poster
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(anim.poster).crossfade(300).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                                ),
                                alpha = 0.45f,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Gradient kiri ke kanan
                            Box(modifier = Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(0.95f), Color.Black.copy(0.6f), Color.Black.copy(0.2f))
                                )
                            ))
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Poster kecil kiri
                                Box(modifier = Modifier.width(60.dp).fillMaxHeight()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(anim.poster).crossfade(300).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Ranking badge — warna berbeda untuk top 3
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .background(
                                                when (index) {
                                                    0 -> Color(0xFFFFB300) // emas
                                                    1 -> Color(0xFF9E9E9E) // perak
                                                    2 -> Color(0xFF8D6E63) // perunggu
                                                    else -> Color.Black.copy(0.7f)
                                                }
                                            )
                                            .align(Alignment.BottomCenter)
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("#${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                // Info tengah
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Dot merah + label ONGOING
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(modifier = Modifier.size(6.dp).background(accentColor, CircleShape))
                                        Text("POPULER", color = accentColor.copy(0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(anim.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        anim.type?.let {
                                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF8C00)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                                Text(it, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                        anim.score?.let { score ->
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(10.dp))
                                                Text(score, color = Color.White.copy(0.7f), fontSize = 9.sp)
                                            }
                                        }
                                        anim.episode?.let {
                                            Text(it, color = Color.White.copy(0.5f), fontSize = 9.sp)
                                        }
                                    }
                                }
                                // Arrow kanan
                                Box(modifier = Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier.size(24.dp).border(1.dp, Color.White.copy(0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Anime Movie — Landscape 16:9 card
            item { SectionHeader(title = "Film Anime", onSeeAllClick = { onSeeAllClicked("Movie") }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(moviesList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        // Landscape Movie Card — 16:9 sinematik
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                                .background(Color(0xFF161616))
                                .clickable { onNavigateToDetail(anim.slug) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(anim.poster).crossfade(300).build(),
                                contentDescription = anim.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Gradient overlay
                            Box(modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(0.85f)),
                                    startY = 80f
                                )
                            ))
                            // MOVIE badge
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accentColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Text("MOVIE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            }
                            // Score kanan atas
                            anim.score?.let { score ->
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(0.7f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(8.dp))
                                        Text(score, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            // Title + genre overlay bawah
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = anim.title,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 14.sp
                                )
                                val genreText = anim.genres?.take(2)?.joinToString(" · ")
                                if (!genreText.isNullOrBlank()) {
                                    Text(genreText, color = Color.White.copy(0.5f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Baru Tamat — Yoredaze style (grayscale banner)
            item { SectionHeader(title = "Baru Tamat", onSeeAllClick = { onSeeAllClicked("Completed") }) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    completedList.take(6).forEachIndexed { index, anim ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                                .clickable { onNavigateToDetail(anim.slug) }
                        ) {
                            // Background grayscale poster
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(anim.poster).crossfade(300).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                                ),
                                alpha = 0.45f,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Gradient kiri ke kanan
                            Box(modifier = Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(0.95f), Color.Black.copy(0.6f), Color.Black.copy(0.2f))
                                )
                            ))
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Poster kecil kiri
                                Box(modifier = Modifier.width(60.dp).fillMaxHeight()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(anim.poster).crossfade(300).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Ranking badge bawah
                                    Box(
                                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.7f)).align(Alignment.BottomCenter).padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("#${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                // Info tengah
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Dot biru + label
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF2196F3), CircleShape))
                                        Text("COMPLETED", color = Color(0xFF64B5F6), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(anim.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        anim.type?.let {
                                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF8C00)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                                Text(it, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                        anim.episode?.let {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(0.5f), modifier = Modifier.size(10.dp))
                                                Text(it, color = Color.White.copy(0.6f), fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                                // Arrow kanan
                                Box(modifier = Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier.size(24.dp).border(1.dp, Color.White.copy(0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(0.5f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 6: Jadwal Hari Ini — Yoredaze table style
            item {
                val dayNames = listOf("Minggu","Senin","Selasa","Rabu","Kamis","Jumat","Sabtu")
                val todayName = dayNames[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1]
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("ESTIMATED", color = Color.White.copy(0.3f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Jadwal Hari Ini", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text(todayName, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onSeeAllClicked("Schedule") }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("JADWAL LENGKAP", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Table container
                    if (todayScheduleList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)).border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(16.dp)).padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tidak ada jadwal tayang hari ini.", color = Color.White.copy(0.3f), fontSize = 13.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(16.dp))
                        ) {
                            todayScheduleList.take(8).forEachIndexed { idx, anim ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (idx % 2 == 0) Color(0xFF111111) else Color(0xFF0E0E0E))
                                        .clickable { onNavigateToDetail(anim.slug) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Jam estimasi
                                    Text(
                                        text = anim.estimation ?: "--:--",
                                        color = if (anim.estimation != null) Color.White else Color.White.copy(0.3f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(44.dp)
                                    )
                                    // Divider vertikal
                                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.1f)))
                                    // Judul
                                    Text(
                                        text = anim.title,
                                        color = Color.White.copy(0.85f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Episode badge
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = anim.episode?.replace("Episode ", "EP ") ?: "-",
                                            color = Color.White.copy(0.7f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (idx < todayScheduleList.take(8).size - 1) {
                                    Divider(color = Color.White.copy(0.05f), thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
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
    val isLoadingMore by viewModel.isSearchLoadingMore.collectAsState()
    val hasNext by viewModel.searchHasNext.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    // Set slug utk lookup O(1), dihitung ulang cuma saat list bookmark berubah (bukan tiap item/tiap scroll)
    val bookmarkedSlugs = remember(bookmarkedAnimes) { bookmarkedAnimes.mapTo(HashSet(bookmarkedAnimes.size)) { it.slug } }
    val accentColor = MaterialTheme.colorScheme.primary
    val cardStyle by viewModel.cardStyle.collectAsState()
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
                    itemsIndexed(popularList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        AnimeListCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                            isBookmarked = bookmarkedSlugs.contains(anim.slug),
                            onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = onLoginRequired
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
                    itemsIndexed(popularList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                            isBookmarked = bookmarkedSlugs.contains(anim.slug),
                            onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                            modifier = Modifier.fillMaxWidth(),
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = onLoginRequired,
                            cardStyle = cardStyle
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
            val searchGridState = rememberLazyGridState()
            val searchListState = rememberLazyListState()

            val shouldLoadMoreSearch = remember {
                derivedStateOf {
                    val gridTotal = searchGridState.layoutInfo.totalItemsCount
                    val gridLastVisible = searchGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val listTotal = searchListState.layoutInfo.totalItemsCount
                    val listLastVisible = searchListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    (gridTotal > 0 && gridLastVisible >= gridTotal - 2) || (listTotal > 0 && listLastVisible >= listTotal - 2)
                }
            }

            LaunchedEffect(shouldLoadMoreSearch.value, query) {
                if (shouldLoadMoreSearch.value && hasNext && !isLoading && !isLoadingMore) {
                    viewModel.loadNextSearchPage()
                }
            }

            if (gridLayout == "List") {
                LazyColumn(
                    state = searchListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(results, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        AnimeListCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                            isBookmarked = bookmarkedSlugs.contains(anim.slug),
                            onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = onLoginRequired
                        )
                    }
                    if (isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            } else {
                val columns = if (gridLayout == "3") 3 else 2
                LazyVerticalGrid(
                    state = searchGridState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(results, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                        AnimeCard(
                            anime = anim,
                            accentColor = accentColor,
                            onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                            isBookmarked = bookmarkedSlugs.contains(anim.slug),
                            onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                            modifier = Modifier.fillMaxWidth(),
                            isLoggedIn = isLoggedIn,
                            onLoginRequired = onLoginRequired,
                            cardStyle = cardStyle
                        )
                    }
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp))
                            }
                        }
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
    val genresListRaw by viewModel.genres.collectAsState()
    val blacklistedGenreSlugs by viewModel.blacklistedGenreSlugs.collectAsState()
    val genresList = remember(genresListRaw, blacklistedGenreSlugs) {
        genresListRaw.filterNot { blacklistedGenreSlugs.contains(it.slug) }
    }
    val itemsList by viewModel.exploreAnimes.collectAsState()
    val isLoading by viewModel.isExploreLoading.collectAsState()
    val hasNext by viewModel.exploreHasNext.collectAsState()
    val bookmarkedAnimes by viewModel.bookmarks.collectAsState()
    // Set slug utk lookup O(1), dihitung ulang cuma saat list bookmark berubah (bukan tiap item/tiap scroll)
    val bookmarkedSlugs = remember(bookmarkedAnimes) { bookmarkedAnimes.mapTo(HashSet(bookmarkedAnimes.size)) { it.slug } }
    val cardStyle by viewModel.cardStyle.collectAsState()
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
            itemsIndexed(genresList, key = { idx, it -> "${it.slug}_${idx}" }) { _, gen ->
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
                        itemsIndexed(itemsList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                            AnimeListCard(
                                anime = anim,
                                accentColor = accentColor,
                                onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                                isBookmarked = bookmarkedSlugs.contains(anim.slug),
                                onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                                isLoggedIn = isLoggedIn,
                                onLoginRequired = onLoginRequired
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
                        itemsIndexed(itemsList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
                            AnimeCard(
                                anime = anim,
                                accentColor = accentColor,
                                onClick = remember(anim.slug) { { onNavigateToDetail(anim.slug) } },
                                isBookmarked = bookmarkedSlugs.contains(anim.slug),
                                onBookmarkToggle = remember(anim.slug) { { viewModel.toggleBookmark(anim.slug, anim.title, anim.poster) } },
                                modifier = Modifier.fillMaxWidth(),
                                cardStyle = cardStyle
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
                        itemsIndexed(dayList, key = { idx, it -> "${it.slug}_${idx}" }) { _, anim ->
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
    val cardStyle by viewModel.cardStyle.collectAsState()
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
                itemsIndexed(bookmarksList, key = { idx, it -> "${it.slug}_${idx}" }) { _, bookmarked ->
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
                        onClick = remember(bookmarked.slug) { { onNavigateToDetail(bookmarked.slug) } },
                        isBookmarked = true,
                        onBookmarkToggle = remember(bookmarked.slug) {
                            { viewModel.toggleBookmark(bookmarked.slug, bookmarked.title, bookmarked.poster) }
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
                itemsIndexed(bookmarksList, key = { idx, it -> "${it.slug}_${idx}" }) { _, bookmarked ->
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
                        onClick = remember(bookmarked.slug) { { onNavigateToDetail(bookmarked.slug) } },
                        isBookmarked = true,
                        onBookmarkToggle = remember(bookmarked.slug) {
                            { viewModel.toggleBookmark(bookmarked.slug, bookmarked.title, bookmarked.poster) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cardStyle = cardStyle
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
    // Set slug utk lookup O(1), dihitung ulang cuma saat list bookmark berubah (bukan tiap item/tiap scroll)
    val bookmarkedSlugs = remember(bookmarkedAnimes) { bookmarkedAnimes.mapTo(HashSet(bookmarkedAnimes.size)) { it.slug } }
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null
    var showLoginDialog by remember { mutableStateOf(false) }
    val watchHistory by viewModel.watchHistory.collectAsState()
    val watchedEpisodeSlugs = remember(watchHistory, slug) {
        watchHistory.filter { it.animeSlug == slug }.map { it.episodeSlug }.toSet()
    }

    LaunchedEffect(Unit) { viewModel.refreshWatchHistory() }

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
            val isBookmarked = bookmarkedSlugs.contains(slug)
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
                                                val isWatched = watchedEpisodeSlugs.contains(ep.slug)
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
                                                    // Checkmark watched indicator
                                                    if (isWatched) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(3.dp)
                                                                .size(14.dp)
                                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                                .background(Color(0xFF4CAF50)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Watched",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(9.dp)
                                                            )
                                                        }
                                                    }
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
// 6b. NOBAR DIALOG (Create / Join Room)
// ================================================================

@Composable
fun NobarDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Buat Room, 1 = Join Room
    var joinCode by remember { mutableStateOf("") }
    val accentColor = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Nobar (Nonton Bareng)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Nonton episode ini bareng teman secara realtime.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Tab switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(4.dp)
                ) {
                    listOf("Buat Room", "Join Room").forEachIndexed { index, label ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    Text(
                        text = "Kamu jadi host. Cuma kamu yang bisa play/pause/seek — teman yang join akan otomatis ikut posisi videomu.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onCreateRoom,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Buat Room", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(6) },
                        label = { Text("Kode Room") },
                        placeholder = { Text("Contoh: XKQP91") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { if (joinCode.isNotBlank()) onJoinRoom(joinCode) },
                        enabled = !isLoading && joinCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Join Room", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ================================================================
// 6c. NOBAR LIST SCREEN — daftar semua room Nobar yang sedang aktif
// ================================================================

@Composable
fun NobarListScreen(
    viewModel: AnikuViewModel,
    onJoinRoom: (episodeSlug: String, animeTitle: String, roomCode: String) -> Unit = { _, _, _ -> }
) {
    val activeRooms by viewModel.activeNobarRooms.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    // State untuk dialog join
    var selectedRoom by remember { mutableStateOf<NobarManager.ActiveRoomSummary?>(null) }
    var joinCodeInput by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        viewModel.startObservingActiveNobarRooms()
        onDispose { viewModel.stopObservingActiveNobarRooms() }
    }

    // Dialog masukkan kode room
    selectedRoom?.let { room ->
        AlertDialog(
            onDismissRequest = {
                selectedRoom = null
                joinCodeInput = ""
                joinError = null
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    text = "Masuk ke Room",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = room.animeTitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = {
                            joinCodeInput = it.uppercase().take(8)
                            joinError = null
                        },
                        label = { Text("Kode Room") },
                        placeholder = { Text("Contoh: ABC123") },
                        singleLine = true,
                        isError = joinError != null,
                        supportingText = joinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            focusedLabelColor = accentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (joinCodeInput.isBlank()) {
                            joinError = "Kode room tidak boleh kosong"
                        } else {
                            onJoinRoom(room.episodeSlug, room.animeTitle, joinCodeInput.trim())
                            selectedRoom = null
                            joinCodeInput = ""
                            joinError = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Join", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedRoom = null
                    joinCodeInput = ""
                    joinError = null
                }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Text(
                text = "Nobar",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Nonton bareng yang sedang berlangsung sekarang.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        if (activeRooms.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Belum Ada Nobar Aktif",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Buat room dari halaman nonton episode untuk mulai nobar bareng teman.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(activeRooms, key = { it.roomCode }) { room ->
                    NobarRoomCard(
                        room = room,
                        accentColor = accentColor,
                        onClick = { selectedRoom = room }
                    )
                }
            }
        }
    }
}

@Composable
fun NobarRoomCard(
    room: NobarManager.ActiveRoomSummary,
    accentColor: Color,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = room.animePoster,
                contentDescription = room.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Indikator "LIVE" — room ini sedang aktif sekarang
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Red)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)
            ) {
                Text("LIVE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.animeTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (room.episodeTitle.isNotBlank()) {
                Text(
                    text = room.episodeTitle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Host: ${room.hostUsername}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (room.dataSource.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val sourceLabel = when (room.dataSource) {
                    "Dayynime-v1" -> "Server 1 (Utama)"
                    "Dayynime-v2" -> "Server 2 (Alternatif)"
                    "Dayynime-v3" -> "Server 3 (Animekompi)"
                    "Dayynime-v4" -> "Server 4 (Donghua)"
                    else -> room.dataSource
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sourceLabel,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${room.memberCount}",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Baris satu komentar episode, dipakai ulang buat komentar utama maupun balasan (isReply=true).
@Composable
private fun EpisodeCommentRow(
    c: EpisodeComment,
    isMe: Boolean,
    accentColor: Color,
    clanTagMap: Map<String, Pair<String, String?>>,
    isReply: Boolean = false,
    onReplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val timeStr = remember(c.created_at) {
        try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = parser.parse(c.created_at.take(19)) ?: java.util.Date()
            val formatter = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())
            formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
            formatter.format(date)
        } catch (e: Exception) { "" }
    }
    val isAdmin = c.role == "admin" || c.is_admin == true
    val isMod = c.role == "moderator"
    val ringColor = when {
        isAdmin -> Color(0xFFFFC107)
        isMod -> Color(0xFFB388FF)
        else -> Color.Transparent
    }
    val avatarSize = if (isReply) 28.dp else 36.dp

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Avatar
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .then(
                    if (ringColor != Color.Transparent)
                        Modifier.border(1.5.dp, ringColor, CircleShape).padding(1.5.dp)
                    else Modifier
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            if (!c.avatar_url.isNullOrEmpty()) {
                AsyncImage(model = c.avatar_url, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Text(c.username.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                GlossyGradientText(
                    text = c.username,
                    colors = when {
                        isAdmin -> adminGradientColors
                        isMod -> moderatorGradientColors
                        else -> defaultNameGradientColors
                    },
                    fontSize = 13.sp
                )
                c.season_level?.let { lvl ->
                    GlossyGradientText(text = "Lv.$lvl", colors = levelGradientColors, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                c.user_number?.let { num ->
                    GlossyGradientText(text = "#$num", colors = idGradientColors, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                clanTagMap[c.user_id]?.let { (tag, _) -> ClanTagBadge(tag) }
                if (isAdmin) {
                    GlossyGradientText(text = "ADMIN", colors = adminGradientColors, fontSize = 10.sp, letterSpacing = 0.4.sp)
                } else if (isMod) {
                    GlossyGradientText(text = "MODERATOR", colors = moderatorGradientColors, fontSize = 10.sp, letterSpacing = 0.4.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            c.reply_to_username?.let { replyTo ->
                Text(
                    "Membalas @$replyTo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = accentColor.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(c.message, fontSize = 13.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(timeStr, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                c.source?.let { src ->
                    Text(src, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accentColor.copy(alpha = 0.85f))
                }
                Text(
                    "Balas",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onReplyClick)
                )
                if (isMe) {
                    Text(
                        "Hapus",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onDeleteClick)
                    )
                }
            }
        }
    }
}

// ================================================================
// 7. WATCH / STREAMING SCREEN
// ================================================================

// Tombol aksi player (Layar Penuh / Nobar / Download): icon di atas, label di bawah,
// dibungkus card pill rounded — dipakai di action row bawah player biar tampilannya
// senada sama aplikasi streaming profesional, bukan icon kecil numpuk di title bar.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun WatchScreen(
    episodeSlug: String,
    animeTitle: String,
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    autoJoinRoomCode: String? = null,
    onLoginRequired: () -> Unit = {}
) {
    var currentEpisodeSlug by remember { mutableStateOf(episodeSlug) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null
    val streams by viewModel.streams.collectAsState()
    val activeStreamUrl by viewModel.activeStreamUrl.collectAsState()
    val isDirectStream by viewModel.isDirectStream.collectAsState()
    val resolvedHeaders by viewModel.resolvedHeaders.collectAsState()
    val selectedIndex by viewModel.selectedStreamIndex.collectAsState()
    val isStreamLoading by viewModel.isStreamLoading.collectAsState()
    val streamError by viewModel.streamError.collectAsState()
    val episodeTitle by viewModel.streamEpisodeTitle.collectAsState()
    val detail by viewModel.animeDetail.collectAsState() // Hold backing episode listing
    val currentAnimeSlug by viewModel.currentAnimeSlug.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val watchHistory by viewModel.watchHistory.collectAsState()
    val watchedEpisodeSlugs = remember(watchHistory, currentAnimeSlug) {
        watchHistory.filter { it.animeSlug == currentAnimeSlug }.map { it.episodeSlug }.toSet()
    }

    // Gate: WebView embed (sumber audio yang bisa dobel dengan ExoPlayer) hanya
    // dibuat SETELAH user tap tombol play. Reset setiap ganti episode supaya
    // user harus tap lagi untuk episode baru (mencegah WebView auto-load saat
    // app masih di episode sebelumnya, yang sebelumnya jadi penyebab dobel suara).
    var userStartedPlayback by remember(currentEpisodeSlug) { mutableStateOf(false) }

    LaunchedEffect(currentEpisodeSlug) {
        viewModel.loadEpisodeStream(currentEpisodeSlug)
        com.example.AnikuAnalytics.trackEpisodeWatched(animeTitle, currentEpisodeSlug)
        viewModel.joinAsViewer(viewModel.currentAnimeSlug.value)
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
            viewModel.leaveAsViewer()
        }
    }

    // XP nonton — dihitung dari lama waktu AKTIF user stay di WatchScreen, BUKAN dari
    // durasi/posisi video. Satu mekanisme yang sama buat direct stream (ExoPlayer) maupun
    // WebView (Mega/Filedon/Wibufile/dll), jadi gak ada lagi logic per-jenis-sumber yang
    // beda-beda dan gampang punya celah (durasi gak kebaca, coroutine ke-cancel duluan, dst).
    // Playback dianggap "mulai" begitu activeStreamUrl kebaca:
    //  - direct stream: autoplay (playWhenReady = true), jadi langsung dianggap mulai
    //  - WebView: baru dianggap mulai setelah user tap tombol play (userStartedPlayback)
    // Timer pause otomatis saat app di background (lifecycle ON_PAUSE), biar gak bisa
    // "curang" cuma buka lalu tinggal — walau tetap gak 100% akurat, ini cukup untuk
    // anti-abuse dasar.
    // CATATAN: server insertWatchEvent pakai on_conflict = "user_id,episode_slug" +
    // ignore-duplicates, jadi 1 episode = MAX 1 baris/XP walau di sini dicoba kirim tiap
    // interval. Ini disengaja — bukan "makin lama nonton makin banyak XP", tapi "coba
    // kirim ulang tiap interval sampai berhasil ke-catat", biar lebih tahan terhadap
    // gagal-kirim sesaat (network blip dsb) dibanding cuma 1x nembak di 1 momen doang.
    val xpIntervalSeconds = 3 * 60 // checkpoint tiap 3 menit aktif nonton
    val xpMaxAttempts = 4 // berhenti nyoba setelah ±12 menit (cukup buat hampir semua durasi episode)
    var xpTicksSent by remember(currentEpisodeSlug) { mutableIntStateOf(0) }
    var activeSeconds by remember(currentEpisodeSlug) { mutableIntStateOf(0) }
    run {
        val watchLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        var isScreenActive by remember { mutableStateOf(true) }
        DisposableEffect(watchLifecycleOwner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> isScreenActive = true
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> isScreenActive = false
                    else -> {}
                }
            }
            watchLifecycleOwner.lifecycle.addObserver(obs)
            onDispose { watchLifecycleOwner.lifecycle.removeObserver(obs) }
        }

        // PENTING: LaunchedEffect cuma di-key ke currentEpisodeSlug (bukan ke activeStreamUrl
        // atau status "udah mulai atau belum"). Kalau di-key ke activeStreamUrl, ganti server/
        // kualitas (selectStreamQuality) yang sempet nge-null-in activeStreamUrl bakal
        // nge-restart efeknya dan reset progress activeSeconds balik ke 0 tiap kali user
        // ganti server — makanya activeStreamUrl/isDirectStream/userStartedPlayback dibaca
        // LANGSUNG tiap tick di dalam loop (semua state Compose, selalu kebaca versi terbaru),
        // bukan di-capture sekali di luar sebagai val.
        LaunchedEffect(currentEpisodeSlug) {
            while (xpTicksSent < xpMaxAttempts) {
                kotlinx.coroutines.delay(1_000)
                val playbackHasStarted = !activeStreamUrl.isNullOrEmpty() && (isDirectStream || userStartedPlayback)
                if (isScreenActive && playbackHasStarted) activeSeconds++
                if (activeSeconds >= xpIntervalSeconds * (xpTicksSent + 1)) {
                    xpTicksSent++
                    viewModel.reportWatchEvent(currentAnimeSlug, currentEpisodeSlug, checkpointNumber = xpTicksSent)
                }
            }
        }
    }

    val activity = LocalContext.current as? android.app.Activity
    var isFullscreen by remember { mutableStateOf(false) }

    // Jaga layar tetap nyala selama di WatchScreen
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }


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

    // ── Nobar (Watch Party) state ──
    val nobarRoom by viewModel.nobarRoom.collectAsState()
    val nobarError by viewModel.nobarError.collectAsState()
    val isNobarLoading by viewModel.isNobarLoading.collectAsState()
    var showNobarDialog by remember { mutableStateOf(false) }
    val nobarSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(nobarError) {
        nobarError?.let {
            nobarSnackbarHostState.showSnackbar(it)
            viewModel.clearNobarError()
        }
    }

    // Auto-join kalau dibuka dari halaman daftar Nobar (tap salah satu room di list).
    // Peringatan kalau host ganti server ke mode WebView saat room Nobar masih aktif —
    // sync ke member akan diam-diam berhenti kalau ini tidak diberi tahu.
    LaunchedEffect(isDirectStream, nobarRoom?.roomCode) {
        if (nobarRoom != null && viewModel.isNobarHost && !isDirectStream) {
            nobarSnackbarHostState.showSnackbar(
                "Server ini tidak mendukung sync Nobar. Member tidak akan ikut posisi videomu sampai kamu pindah ke server lain."
            )
        }
    }

    LaunchedEffect(autoJoinRoomCode) {
        if (autoJoinRoomCode != null) {
            viewModel.joinNobarRoom(autoJoinRoomCode) { /* error sudah ditangani via nobarError */ }
        }
    }

    // Kalau host pindah episode lewat tombol Sebelumnya/Selanjutnya, sinkronkan
    // perubahan episode itu juga ke room (member ikut pindah episode).
    LaunchedEffect(currentEpisodeSlug, nobarRoom?.roomCode) {
        val room = nobarRoom
        if (room != null && viewModel.isNobarHost && room.episodeSlug != currentEpisodeSlug) {
            // Episode lokal sudah berubah duluan (host), broadcast nanti ditangani
            // saat ExoPlayer baru siap — lihat nobarUpdatePlayback di blok ExoPlayer.
        }
    }

    // Member (bukan host): kalau room pindah episode, ikuti otomatis.
    LaunchedEffect(nobarRoom?.episodeSlug) {
        val room = nobarRoom
        if (room != null && !viewModel.isNobarHost && room.episodeSlug.isNotEmpty() &&
            room.episodeSlug != currentEpisodeSlug
        ) {
            currentEpisodeSlug = room.episodeSlug
        }
    }

    // Leave room otomatis kalau keluar dari WatchScreen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveNobarRoom()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .let { if (!isFullscreen) it.verticalScroll(rememberScrollState()) else it }
    ) {
        // App controls Header row — Material3 surface app bar
        if (!isFullscreen) Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = animeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episodeTitle ?: "Memuat...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Cuma nampilin badge status kompak di title bar kalau room Nobar lagi aktif.
                // Aksi utama (mulai Nobar / Download) dipindah ke action bar di bawah player
                // biar nggak numpuk kecil-kecil di pojok atas.
                if (nobarRoom != null) {
                    AssistChip(
                        onClick = { viewModel.leaveNobarRoom() },
                        label = {
                            Text(
                                text = "${nobarRoom?.roomCode} · ${nobarRoom?.memberCount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = "Nobar aktif",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                }
            }
        }

        if (showNobarDialog) {
            NobarDialog(
                isLoading = isNobarLoading,
                onDismiss = { showNobarDialog = false },
                onCreateRoom = {
                    viewModel.createNobarRoom(
                        animeSlug = currentAnimeSlug,
                        animeTitle = animeTitle,
                        animePoster = detail?.poster ?: "",
                        episodeSlug = currentEpisodeSlug,
                        episodeTitle = episodeTitle ?: ""
                    ) { code ->
                        if (code != null) {
                            showNobarDialog = false
                            clipboardManager.setText(AnnotatedString(code))
                            coroutineScope.launch {
                                nobarSnackbarHostState.showSnackbar("Room dibuat! Kode $code disalin, share ke temanmu.")
                            }
                        }
                    }
                },
                onJoinRoom = { code ->
                    viewModel.joinNobarRoom(code) { success ->
                        if (success) showNobarDialog = false
                    }
                }
            )
        }

        SnackbarHost(hostState = nobarSnackbarHostState)

        // Webview embed stream container
        Box(
            modifier = if (isFullscreen) Modifier.fillMaxSize().background(Color.Black)
                       else Modifier.fillMaxWidth().height(220.dp).background(Color.Black)
        ) {
            if (isStreamLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = accentColor)
                        Text(
                            text = "Tunggu sebentar lagi, video sedang dimuat...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else if (streamError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = streamError ?: "Gagal memutar video", color = Color.White, modifier = Modifier.padding(16.dp))
                }
            } else if (!activeStreamUrl.isNullOrEmpty()) {
                if (isDirectStream) {
                    // ── Design A: Glass · Merah · Modern + PiP ──
                    val ctx = LocalContext.current
                    val activity = ctx as? android.app.Activity

                    val exoPlayer = remember(activeStreamUrl) {
                        val defaultHeaders = mapOf(
                            "Referer" to "https://v2.samehadaku.how/",
                            "Origin" to "https://v2.samehadaku.how",
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        )
                        val headers = if (resolvedHeaders.isNotEmpty()) resolvedHeaders else defaultHeaders
                        // Pakai OkHttpDataSource (bukan DefaultHttpDataSource bawaan) supaya
                        // request streaming manifest/segment ExoPlayer ikut lewat OkHttpClient
                        // yang sama-sama punya fallback DNS-over-HTTPS kaya di VideoExtractor.
                        // Kalau DNS domain video/CDN-nya di-block ISP user (kasus umum buat
                        // ok.ru dkk), tanpa ini ExoPlayer bakal macet/loading-mulu karena nyoba
                        // resolve pakai DNS sistem doang - beda sama proses extract link video
                        // yang udah duluan fallback DoH.
                        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
                            com.example.network.VideoExtractor.streamingHttpClient
                        )
                            .setDefaultRequestProperties(headers)
                        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(ctx, httpDataSourceFactory)
                        val url = activeStreamUrl!!
                        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000).build()
                        ExoPlayer.Builder(ctx)
                            .setLoadControl(loadControl)
                            .setMediaSourceFactory(
                                if (url.contains(".m3u8"))
                                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                                else
                                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                            )
                            .build().apply {
                                setMediaItem(MediaItem.fromUri(url))
                                prepare()
                                playWhenReady = true
                                // Member yang join room saat host sudah nonton beberapa menit
                                // langsung diseek ke posisi terkini, bukan mulai dari 00:00.
                                val room = nobarRoom
                                if (room != null && room.hostUid != session.userId) {
                                    seekTo(NobarManager.estimateCurrentPositionMs(room))
                                } else {
                                    // Balik dari mini player (in-app floating PIP) -> lanjut
                                    // dari posisi terakhir, bukan mulai dari 00:00 lagi.
                                    viewModel.consumePendingResumeMs()?.let { resumeMs -> seekTo(resumeMs) }
                                }
                            }
                    }

                    // Register ke MainActivity untuk PiP
                    DisposableEffect(exoPlayer) {
                        com.example.MainActivity.pipExoPlayer = exoPlayer
                        com.example.MainActivity.isWatchingDirectStream = true
                        onDispose {
                            exoPlayer.release()
                            com.example.MainActivity.pipExoPlayer = null
                            com.example.MainActivity.isWatchingDirectStream = false
                        }
                    }

                    // State
                    var isPlaying by remember { mutableStateOf(true) }
                    var showControls by remember { mutableStateOf(true) }
                    var isBuffering by remember { mutableStateOf(true) }
                    var currentPosition by remember { mutableStateOf(0L) }
                    var duration by remember { mutableStateOf(0L) }
                    var playbackSpeed by remember { mutableStateOf(1f) }
                    var showSpeedMenu by remember { mutableStateOf(false) }
                    var isLocked by remember { mutableStateOf(false) }
                    var doubleTapSide by remember { mutableStateOf<String?>(null) }

                    // PiP state
                    val isPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pipMode = remember { mutableStateOf(false) }
                        DisposableEffect(activity) {
                            val listener = androidx.core.app.PictureInPictureModeChangedInfo::class
                            onDispose {}
                        }
                        // track via lifecycle
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                                    pipMode.value = activity?.isInPictureInPictureMode == true
                                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    pipMode.value = false
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(obs)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                        }
                        pipMode.value
                    } else false

                    // Member di room Nobar tidak boleh kontrol playback — hanya host yang bisa.
                    // Tap/seek tetap terdeteksi tapi tidak dieksekusi, dan kasih info ke user.
                    val canControlPlayback = nobarRoom == null || viewModel.isNobarHost

                    // Listener
                    DisposableEffect(exoPlayer) {
                        val listener = object : Player.Listener {
                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                                // Host: broadcast segera setiap kali play/pause berubah,
                                // supaya member tidak menunggu tick periodik untuk ikut pause/play.
                                if (nobarRoom != null && viewModel.isNobarHost) {
                                    viewModel.nobarUpdatePlayback(playing, exoPlayer.currentPosition)
                                }
                            }
                            override fun onPlaybackStateChanged(state: Int) {
                                isBuffering = state == Player.STATE_BUFFERING
                            }
                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                // Sebelumnya error ExoPlayer (403 diblok, token/link expired,
                                // manifest gagal di-parse, timeout, dsb) ketelen diam-diam -
                                // layar cuma stuck hitam 00:00/00:00 tanpa info apa-apa.
                                // Sekarang error asli (termasuk HTTP status code kalau ada,
                                // via error.cause) dimunculin ke streamError biar keliatan
                                // penyebab pastinya, bukan nebak-nebak.
                                val cause = error.cause
                                val detail = when (cause) {
                                    is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException ->
                                        "HTTP ${cause.responseCode} dari server (${cause.dataSpec.uri})"
                                    else -> cause?.message ?: error.message
                                }
                                android.util.Log.e("ExoPlayerError", "Gagal play: ${error.errorCodeName} - $detail", error)
                                viewModel.setStreamError("Gagal memutar video: ${error.errorCodeName}\n$detail")
                            }
                        }
                        exoPlayer.addListener(listener)
                        onDispose { exoPlayer.removeListener(listener) }
                    }

                    // Progress tick
                    LaunchedEffect(exoPlayer) {
                        while (true) {
                            currentPosition = exoPlayer.currentPosition
                            duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                            // Host: broadcast posisi berkala (menangkap perubahan dari seek
                            // manual lewat slider/tap -10s/+10s, yang tidak memicu onIsPlayingChanged).
                            if (nobarRoom != null && viewModel.isNobarHost) {
                                viewModel.nobarUpdatePlayback(exoPlayer.isPlaying, exoPlayer.currentPosition)
                            }
                            kotlinx.coroutines.delay(if (nobarRoom != null && viewModel.isNobarHost) 2_000 else 500)
                        }
                    }

                    // Member (bukan host): ikuti state play/pause/posisi dari room secara realtime.
                    LaunchedEffect(nobarRoom?.isPlaying, nobarRoom?.positionMs, nobarRoom?.updatedAt) {
                        val room = nobarRoom
                        if (room == null || viewModel.isNobarHost) return@LaunchedEffect
                        val targetPositionMs = NobarManager.estimateCurrentPositionMs(room)
                        val drift = kotlin.math.abs(exoPlayer.currentPosition - targetPositionMs)
                        if (drift > NobarManager.SYNC_TOLERANCE_MS) {
                            exoPlayer.seekTo(targetPositionMs)
                        }
                        if (room.isPlaying && !exoPlayer.isPlaying) {
                            exoPlayer.play()
                        } else if (!room.isPlaying && exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        }
                    }

                    // Auto-hide controls
                    LaunchedEffect(showControls, isPlaying) {
                        if (showControls && isPlaying && !isPiP) {
                            kotlinx.coroutines.delay(3000)
                            showControls = false
                        }
                    }

                    // Double tap dismiss
                    LaunchedEffect(doubleTapSide) {
                        if (doubleTapSide != null) {
                            kotlinx.coroutines.delay(600)
                            doubleTapSide = null
                        }
                    }

                    fun formatMs(ms: Long): String {
                        val s = ms / 1000
                        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
                        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
                    }

                    fun enterPiP() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val params = android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(android.util.Rational(16, 9))
                                .build()
                            activity?.enterPictureInPictureMode(params)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

                        // Video surface
                        AndroidView(
                            factory = { c ->
                                PlayerView(c).apply {
                                    player = exoPlayer
                                    useController = false
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Buffering
                        if (isBuffering && !isPiP) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                            }
                        }

                        // Double tap ripple
                        if (doubleTapSide != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.38f)
                                    .align(if (doubleTapSide == "left") Alignment.CenterStart else Alignment.CenterEnd)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        if (doubleTapSide == "left") "« 10s" else "10s »",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Tap / double-tap gesture area
                        if (!isPiP) {
                            var lastTapTime by remember { mutableStateOf(0L) }
                            var lastTapSide by remember { mutableStateOf("") }
                            Box(modifier = Modifier.fillMaxSize().pointerInput(isLocked) {
                                detectTapGestures(onTap = { offset ->
                                    if (isLocked) return@detectTapGestures
                                    val now = System.currentTimeMillis()
                                    val side = if (offset.x < size.width / 2f) "left" else "right"
                                    if (now - lastTapTime < 300 && lastTapSide == side) {
                                        if (canControlPlayback) {
                                            if (side == "left") exoPlayer.seekTo(exoPlayer.currentPosition - 10_000)
                                            else exoPlayer.seekTo(exoPlayer.currentPosition + 10_000)
                                            doubleTapSide = side
                                            showControls = false
                                        } else {
                                            coroutineScope.launch {
                                                nobarSnackbarHostState.showSnackbar("Hanya host yang bisa kontrol video di room Nobar")
                                            }
                                        }
                                    } else {
                                        showControls = !showControls
                                    }
                                    lastTapTime = now; lastTapSide = side
                                })
                            })
                        }

                        // Controls overlay — hidden in PiP
                        if (!isPiP) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showControls,
                                enter = androidx.compose.animation.fadeIn(tween(200)),
                                exit = androidx.compose.animation.fadeOut(tween(200)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                0f to Color.Black.copy(alpha = 0.2f),
                                                0.4f to Color.Transparent,
                                                1f to Color.Black.copy(alpha = 0.7f)
                                            )
                                        )
                                ) {
                                    if (!isLocked) {
                                        // Top bar — chip kualitas + PiP button
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.TopStart)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Quality chip
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("720p", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = Color.Black.copy(alpha = 0.4f),
                                                        labelColor = Color.White.copy(alpha = 0.9f)
                                                    ),
                                                    border = AssistChipDefaults.assistChipBorder(
                                                        enabled = true,
                                                        borderColor = Color.White.copy(alpha = 0.15f)
                                                    ),
                                                    modifier = Modifier.height(28.dp)
                                                )
                                                // Badge XP checkpoint — cuma nongol selama masih ada
                                                // kesempatan checkpoint tersisa (xpTicksSent < xpMaxAttempts),
                                                // biar user tau nonton dia beneran "kehitung" bukan cuma nebak.
                                                if (xpTicksSent < xpMaxAttempts) {
                                                    val secondsToNextTick = (xpIntervalSeconds * (xpTicksSent + 1) - activeSeconds).coerceAtLeast(0)
                                                    val mm = secondsToNextTick / 60
                                                    val ss = secondsToNextTick % 60
                                                    AssistChip(
                                                        onClick = {},
                                                        label = {
                                                            Text(
                                                                "⚡ $xpTicksSent/$xpMaxAttempts · ${mm}:${ss.toString().padStart(2, '0')}",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        },
                                                        colors = AssistChipDefaults.assistChipColors(
                                                            containerColor = Color.Black.copy(alpha = 0.4f),
                                                            labelColor = Color.White.copy(alpha = 0.9f)
                                                        ),
                                                        border = AssistChipDefaults.assistChipBorder(
                                                            enabled = true,
                                                            borderColor = Color.White.copy(alpha = 0.15f)
                                                        ),
                                                        modifier = Modifier.height(28.dp)
                                                    )
                                                }
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Next episode button — cuma nongol kalau ada episode
                                                // berikutnya, biar user gak perlu keluar fullscreen dulu
                                                // buat lanjut nonton episode selanjutnya.
                                                val epsForNav = detail?.episodes
                                                val navIndex = epsForNav?.indexOfFirst { it.slug == currentEpisodeSlug } ?: -1
                                                val nextEp = if (navIndex > 0) epsForNav?.getOrNull(navIndex - 1) else null
                                                if (nextEp != null) {
                                                    FilledIconButton(
                                                        onClick = { currentEpisodeSlug = nextEp.slug },
                                                        colors = IconButtonDefaults.filledIconButtonColors(
                                                            containerColor = Color.Black.copy(alpha = 0.4f),
                                                            contentColor = Color.White
                                                        ),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.SkipNext, contentDescription = "Episode selanjutnya", modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                // Fullscreen toggle — biar bisa masuk/keluar layar penuh
                                                // langsung dari overlay player, gak perlu scroll ke tombol
                                                // "Layar Penuh" yang ke-hidden pas lagi fullscreen.
                                                FilledIconButton(
                                                    onClick = { isFullscreen = !isFullscreen },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = Color.Black.copy(alpha = 0.4f),
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                        contentDescription = if (isFullscreen) "Keluar layar penuh" else "Layar penuh",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                // PiP button (sistem Android — app keluar ke floating window OS)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    FilledIconButton(
                                                        onClick = { enterPiP() },
                                                        colors = IconButtonDefaults.filledIconButtonColors(
                                                            containerColor = Color.Black.copy(alpha = 0.4f),
                                                            contentColor = Color.White
                                                        ),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                // Mini player in-app — video tetap muter di kotak kecil,
                                                // tapi TETAP di dalam Aniku (beda sama PiP sistem di atas),
                                                // jadi user masih bisa buka halaman lain (home, search, dll).
                                                FilledIconButton(
                                                    onClick = {
                                                        if (isFullscreen) {
                                                            isFullscreen = false
                                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                                                        }
                                                        val urlNow = activeStreamUrl
                                                        if (!urlNow.isNullOrEmpty()) {
                                                            viewModel.openMiniPlayer(
                                                                AnikuViewModel.MiniPlayerData(
                                                                    animeSlug = currentAnimeSlug,
                                                                    animeTitle = animeTitle,
                                                                    episodeSlug = currentEpisodeSlug,
                                                                    episodeTitle = episodeTitle ?: "",
                                                                    streamUrl = urlNow,
                                                                    headers = resolvedHeaders,
                                                                    startPositionMs = exoPlayer.currentPosition
                                                                )
                                                            )
                                                        }
                                                        onBack()
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = Color.Black.copy(alpha = 0.4f),
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Mini player", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        // Center controls
                                        Row(
                                            modifier = Modifier.align(Alignment.Center),
                                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // -10s
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        if (canControlPlayback) {
                                                            exoPlayer.seekTo(exoPlayer.currentPosition - 10_000)
                                                        } else {
                                                            coroutineScope.launch {
                                                                nobarSnackbarHostState.showSnackbar("Hanya host yang bisa kontrol video di room Nobar")
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White.copy(0.85f), modifier = Modifier.size(28.dp))
                                                }
                                                Text("-10s", fontSize = 9.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp)
                                            }
                                            // Play/Pause
                                            FilledIconButton(
                                                onClick = {
                                                    if (canControlPlayback) {
                                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                        showControls = true
                                                    } else {
                                                        coroutineScope.launch {
                                                            nobarSnackbarHostState.showSnackbar("Hanya host yang bisa kontrol video di room Nobar")
                                                        }
                                                    }
                                                },
                                                colors = IconButtonDefaults.filledIconButtonColors(
                                                    containerColor = accentColor,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.size(60.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            // +10s
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        if (canControlPlayback) {
                                                            exoPlayer.seekTo(exoPlayer.currentPosition + 10_000)
                                                        } else {
                                                            coroutineScope.launch {
                                                                nobarSnackbarHostState.showSnackbar("Hanya host yang bisa kontrol video di room Nobar")
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White.copy(0.85f), modifier = Modifier.size(28.dp))
                                                }
                                                Text("+10s", fontSize = 9.sp, color = Color.White.copy(0.6f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp)
                                            }
                                        }

                                        // Bottom bar
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            // Progress bar
                                            Slider(
                                                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                                onValueChange = {
                                                    if (canControlPlayback) {
                                                        exoPlayer.seekTo((it * duration).toLong())
                                                    } else {
                                                        coroutineScope.launch {
                                                            nobarSnackbarHostState.showSnackbar("Hanya host yang bisa kontrol video di room Nobar")
                                                        }
                                                    }
                                                },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = accentColor,
                                                    activeTrackColor = accentColor,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(24.dp)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Timestamp
                                                Text(
                                                    "${formatMs(currentPosition)} / ${formatMs(duration)}",
                                                    color = Color.White.copy(0.75f),
                                                    fontSize = 11.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Speed pill
                                                    Box {
                                                        AssistChip(
                                                            onClick = { showSpeedMenu = true },
                                                            label = { Text("${playbackSpeed}×", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                            colors = AssistChipDefaults.assistChipColors(
                                                                containerColor = Color.Black.copy(alpha = 0.4f),
                                                                labelColor = accentColor
                                                            ),
                                                            border = AssistChipDefaults.assistChipBorder(
                                                                enabled = true,
                                                                borderColor = Color.White.copy(alpha = 0.15f)
                                                            ),
                                                            modifier = Modifier.height(28.dp)
                                                        )
                                                        DropdownMenu(
                                                            expanded = showSpeedMenu,
                                                            onDismissRequest = { showSpeedMenu = false },
                                                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                        ) {
                                                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Text(
                                                                            "${speed}×",
                                                                            color = if (speed == playbackSpeed) accentColor else MaterialTheme.colorScheme.onSurface,
                                                                            fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                                                            fontSize = 13.sp
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        playbackSpeed = speed
                                                                        exoPlayer.setPlaybackSpeed(speed)
                                                                        showSpeedMenu = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Lock button — always visible
                                    FilledIconButton(
                                        onClick = { isLocked = !isLocked },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (isLocked) accentColor.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 10.dp)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = "Lock",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!userStartedPlayback) {
                    // ── Gate: tunggu user tap play sebelum WebView dibuat ──
                    // WebView baru di-mount setelah ini true, jadi tidak ada
                    // jendela waktu di mana WebView autoplay tanpa sepengetahuan user.
                    Box(modifier = Modifier.fillMaxSize()) {
                        val posterUrl = detail?.poster
                        if (!posterUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = animeTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                            )
                        }
                        IconButton(
                            onClick = { userStartedPlayback = true },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .background(accentColor, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Putar",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else {
                    // ── WebView: untuk URL embed (iframe, dll) ──
                    // Dibuat hanya setelah userStartedPlayback = true (lihat gate di atas).
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
                                            // Eksplisit matiin dukungan popup/new-window — banyak server
                                            // gratisan (gdriveplayer.to dkk) suka pasang iklan popunder
                                            // yang manggil window.open(). Default WebView udah gak bikin
                                            // window baru selama ini gak di-set true, tapi di-set eksplisit
                                            // di sini biar jelas & gak kebalik gak sengaja di masa depan.
                                            javaScriptCanOpenWindowsAutomatically = false
                                            setSupportMultipleWindows(false)
                                        }
                                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        webViewClient = object : WebViewClient() {
                                            private val allowedDomains = listOf(
                                                "vidhide.com", "vidhidepro.com", "vidhideplus.com",
                                                "filemoon.sx", "filemoon.in", "filemoon.to",
                                                "filedon.co", "filedon.com",
                                                "dood.watch", "doodstream.com", "dood.to",
                                                "dood.so", "dood.cx", "dood.la",
                                                "streamtape.com", "streamtape.co",
                                                "mp4upload.com", "yourupload.com",
                                                "mega.nz", "mega.co.nz",
                                                "blogger.com", "blogspot.com",
                                                "googlevideo.com", "googleapis.com",
                                                "youtube.com", "youtube.googleapis.com",
                                                "gstatic.com", "jwplatform.com", "jwpcdn.com",
                                                "akamaized.net", "cloudfront.net", "fastly.net",
                                                "cdnjs.cloudflare.com", "cloudflare.com",
                                                "animasu.cc", "sanka.my.id",
                                                "abysscdn.com",
                                                "samehadaku.how", "v2.samehadaku.how",
                                                "sankavollerei.web.id", "sankavollerei.com",
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
                                                "cdn.jsdelivr.net", "unpkg.com",
                                                // GDRIVE / GDRIVE HD — perlu di-whitelist karena player-nya
                                                // dirender via iframe sub-frame ke domain ini sendiri.
                                                "gdriveplayer.to",
                                                // Shortlink Animasu (kadang ganti-ganti provider)
                                                "short.icu", "short.ink"
                                            )

                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: android.webkit.WebResourceRequest?
                                            ): android.webkit.WebResourceResponse? {
                                                val url = request?.url?.toString() ?: return null
                                                // Intercept googlevideo.com/videoplayback — URL MP4 Blogger/YouTube
                                                if (url.contains("googlevideo.com/videoplayback") &&
                                                    url.contains("mime=video") &&
                                                    !url.contains("mime=video/webm")) {
                                                    android.util.Log.d("AnikuWebView", "Intercepted Blogger/YT video: ${url.take(100)}")
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        // Bisukan WebView TEPAT sebelum pindah ke ExoPlayer, supaya
                                                        // tidak ada jeda di mana keduanya bersuara bersamaan.
                                                        // Sengaja ringan (1 statement, tanpa observer) agar tidak
                                                        // mengganggu render WebView.
                                                        try {
                                                            view?.evaluateJavascript(
                                                                "document.querySelectorAll('video,audio').forEach(function(e){e.muted=true;e.pause();});",
                                                                null
                                                            )
                                                        } catch (_: Exception) {}
                                                        view?.onPause()
                                                        viewModel.switchToDirectStream(
                                                            url = url,
                                                            headers = mapOf(
                                                                "Referer" to "https://www.blogger.com/",
                                                                "Origin" to "https://www.blogger.com",
                                                                "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                                            )
                                                        )
                                                    }
                                                }
                                                return null
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: return true
                                                val scheme = request.url?.scheme?.lowercase()
                                                // Skema selain http/https (intent://, market://, whatsapp://,
                                                // dll) sering dipakai iklan buat "kabur" dari WebView ke app
                                                // lain / Play Store. Host-nya bisa gagal ke-parse jadi null,
                                                // jadi block eksplisit di sini, bukan cuma andelin host==null.
                                                if (scheme != "http" && scheme != "https") {
                                                    android.util.Log.w("AnikuWebView", "Blocked non-http scheme: $url")
                                                    return true
                                                }
                                                val host = request.url?.host?.lowercase() ?: run {
                                                    android.util.Log.w("AnikuWebView", "Blocked unparseable host: $url")
                                                    return true
                                                }
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
                                            override fun onCreateWindow(
                                                view: WebView?,
                                                isDialog: Boolean,
                                                isUserGesture: Boolean,
                                                resultMsg: android.os.Message?
                                            ): Boolean {
                                                android.util.Log.w("AnikuWebView", "Blocked popup window (onCreateWindow)")
                                                return false
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
                                onRelease = { view ->
                                    // Pastikan WebView benar-benar mati (bukan cuma di-detach) saat
                                    // Compose membuang node ini, misal saat pindah ke ExoPlayer.
                                    try {
                                        view.stopLoading()
                                        view.onPause()
                                        view.loadUrl("about:blank")
                                        view.destroy()
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )
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
        }

        // Action bar di bawah player: Layar Penuh, Nobar, Download — dipindah ke sini
        // (dulu numpuk kecil di pojok kanan atas) biar layoutnya rapi kayak aplikasi
        // streaming profesional (Netflix/Crunchyroll style row of actions).
        if (!isFullscreen) {
            val downloadFeatureEnabled by viewModel.remoteConfigManager.downloadEnabled.collectAsState()
            val canDownload = downloadFeatureEnabled && isDirectStream &&
                com.example.network.VideoDownloadManager.isDownloadableUrl(activeStreamUrl)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Fullscreen,
                    label = "Layar Penuh",
                    onClick = { isFullscreen = true }
                )

                if (nobarRoom != null) {
                    PlayerActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Groups,
                        label = "${nobarRoom?.roomCode} · ${nobarRoom?.memberCount}",
                        highlighted = true,
                        onClick = { viewModel.leaveNobarRoom() },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(nobarRoom?.roomCode ?: ""))
                            coroutineScope.launch {
                                nobarSnackbarHostState.showSnackbar("Kode room disalin: ${nobarRoom?.roomCode}")
                            }
                        }
                    )
                } else {
                    PlayerActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Groups,
                        label = "Nobar",
                        onClick = {
                            if (isDirectStream) {
                                showNobarDialog = true
                            } else {
                                coroutineScope.launch {
                                    nobarSnackbarHostState.showSnackbar(
                                        "Nobar belum bisa dipakai di server ini. Coba pindah ke server lain dulu."
                                    )
                                }
                            }
                        }
                    )
                }

                if (canDownload) {
                    PlayerActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Download,
                        label = "Unduh",
                        onClick = {
                            if (!isLoggedIn) {
                                onLoginRequired()
                                return@PlayerActionButton
                            }
                            val started = viewModel.startEpisodeDownload(
                                url = activeStreamUrl!!,
                                headers = resolvedHeaders,
                                animeSlug = currentAnimeSlug,
                                animeTitle = animeTitle,
                                animePoster = detail?.poster ?: "",
                                episodeSlug = currentEpisodeSlug,
                                episodeTitle = episodeTitle ?: currentEpisodeSlug
                            )
                            coroutineScope.launch {
                                nobarSnackbarHostState.showSnackbar(
                                    if (started) "Download dimulai, cek notifikasi."
                                    else "Gagal memulai download."
                                )
                            }
                        }
                    )
                }
            }
        }

        // ── Info Anime: poster, judul, badge, sinopsis, statistik & progres ──
        if (!isFullscreen) detail?.let { d ->
            var isSynopsisExpanded by remember { mutableStateOf(false) }
            val eps = d.episodes ?: emptyList()
            val currentIdx = eps.indexOfFirst { it.slug == currentEpisodeSlug }
            val epNumLabel = eps.getOrNull(currentIdx)?.name
                ?.replace(Regex("[^0-9]"), "")
                ?.ifEmpty { null }
                ?: (if (currentIdx >= 0) "${currentIdx + 1}" else "-")
            val seriesProgress = if (eps.isNotEmpty() && currentIdx >= 0) {
                // Episode list biasanya urut descending (terbaru duluan)
                ((eps.size - currentIdx).toFloat() / eps.size.toFloat()).coerceIn(0f, 1f)
            } else 0f

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                // Poster + judul + badge
                Row(verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        model = d.poster,
                        contentDescription = d.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 84.dp, height = 112.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = d.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Episode $epNumLabel",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(d.rating ?: "0.0", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    d.type ?: "TV",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    d.status ?: "Ongoing",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            d.genres?.take(4)?.forEach { g ->
                                Surface(shape = RoundedCornerShape(6.dp), color = accentColor.copy(alpha = 0.15f)) {
                                    Text(
                                        g.name,
                                        color = accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sinopsis
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "SINOPSIS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = d.synopsis ?: "Tidak ada sinopsis.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isSynopsisExpanded = !isSynopsisExpanded }
                )
                if ((d.synopsis?.length ?: 0) > 120) {
                    Text(
                        text = if (isSynopsisExpanded) "Sembunyikan" else "Baca Selengkapnya...",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Statistik ringkas: total eps, tipe, episode berjalan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val stats = listOf(
                        "${eps.size}" to "TOTAL EPS",
                        (d.type ?: "TV") to "TIPE",
                        epNumLabel to "EPISODE"
                    )
                    stats.forEach { (value, label) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(label, fontSize = 10.sp, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progres menonton (berdasarkan posisi episode dalam series)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "PROGRES MENONTON",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${(seriesProgress * 100).toInt()}%",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { seriesProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // XP checkpoint episode ini — beda dari "Progres Menonton" di atas (yang
                // basisnya posisi episode dalam series). Ini basisnya lama nonton AKTIF di
                // episode yang lagi dibuka sekarang (lihat catatan run{} XP di atas).
                val xpCycleProgress = if (xpTicksSent >= xpMaxAttempts) 1f else {
                    ((activeSeconds - xpIntervalSeconds * xpTicksSent).toFloat() / xpIntervalSeconds).coerceIn(0f, 1f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "XP CHECKPOINT EPISODE INI",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (xpTicksSent >= xpMaxAttempts) "Selesai" else "$xpTicksSent/$xpMaxAttempts",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { xpCycleProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Horizontal server/quality selection
        if (!isFullscreen && streams.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
                Text(
                    text = "Pilih Server",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    streams.forEachIndexed { i, q ->
                        val isSelected = selectedIndex == i
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectStreamQuality(i) },
                            label = { Text(q.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = accentColor
                            )
                        )
                    }
                }
            }
        }

        // Previous and Next Episode controls
        if (!isFullscreen) detail?.episodes?.let { eps ->
            val currentIndex = eps.indexOfFirst { it.slug == currentEpisodeSlug }
            if (currentIndex != -1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Previous ep (index + 1 karena list descending)
                    val hasPrev = currentIndex < eps.size - 1
                    OutlinedButton(
                        onClick = {
                            val newEp = eps.getOrNull(currentIndex + 1)
                            if (newEp != null) {
                                currentEpisodeSlug = newEp.slug
                            }
                        },
                        enabled = hasPrev,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sebelumnya", fontSize = 13.sp)
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Selanjutnya", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (!isFullscreen) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Semua Episode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$totalEps Episode",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Group range tabs
        if (groups != null && groups.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                itemsIndexed(groups) { idx, (start, end) ->
                    val epStart = eps.getOrNull(start)?.name?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (start + 1)
                    val epEnd = eps.getOrNull(end)?.name?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: (end + 1)
                    val isSelected = selectedGroup == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGroup = idx },
                        label = { Text("$epStart-$epEnd", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = accentColor
                        )
                    )
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(displayEps, key = { idx, item -> "${item.slug}_${idx}" }) { _, item ->
                val isActive = item.slug == currentEpisodeSlug
                val isWatched = !isActive && watchedEpisodeSlugs.contains(item.slug)
                val epNum = item.name.replace(Regex("[^0-9]"), "").ifEmpty { "-" }
                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .clickable { currentEpisodeSlug = item.slug },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = if (isActive) 0.dp else 1.dp,
                    border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        if (isWatched) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = Color.White,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end if (!isFullscreen)

        // ── Komentar Episode (Material3) ────────────────────────
        val commentsEnabled by viewModel.remoteConfigManager.commentsEnabled.collectAsState()
        if (!isFullscreen && commentsEnabled) {
            val episodeComments by viewModel.episodeComments.collectAsState()
            val isCommentsLoading by viewModel.isEpisodeCommentsLoading.collectAsState()
            val isPostingComment by viewModel.isPostingEpisodeComment.collectAsState()
            val clanTagMap by viewModel.clanTagMap.collectAsState()
            val session by viewModel.session.collectAsState()
            var commentInput by remember { mutableStateOf("") }
            var deleteTargetId by remember { mutableStateOf<String?>(null) }
            var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to username

            LaunchedEffect(currentEpisodeSlug) {
                viewModel.loadEpisodeComments(currentEpisodeSlug)
                viewModel.loadClanTagMap()
            }

            val episodeCommentError by viewModel.episodeCommentError.collectAsState()
            val commentErrorContext = LocalContext.current
            LaunchedEffect(episodeCommentError) {
                episodeCommentError?.let {
                    Toast.makeText(commentErrorContext, it, Toast.LENGTH_LONG).show()
                    viewModel.clearEpisodeCommentError()
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Komentar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (episodeComments.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "${episodeComments.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Input komentar
                    if (session.token.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text("Login untuk kasih komentar", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    } else {
                        if (replyTarget != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Reply,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Membalas @${replyTarget?.second}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Batal balas",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { replyTarget = null }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = { if (it.length <= 500) commentInput = it },
                                placeholder = { Text(if (replyTarget != null) "Tulis balasan..." else "Tulis komentar...", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                maxLines = 4,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                            FilledIconButton(
                                enabled = !isPostingComment && commentInput.isNotBlank(),
                                onClick = {
                                    if (commentInput.isNotBlank()) {
                                        viewModel.postEpisodeComment(
                                            currentEpisodeSlug,
                                            commentInput,
                                            currentAnimeSlug,
                                            animeTitle,
                                            animePoster = detail?.poster,
                                            parentCommentId = replyTarget?.first,
                                            replyToUsername = replyTarget?.second
                                        )
                                        commentInput = ""
                                        replyTarget = null
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            ) {
                                if (isPostingComment) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Kirim", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Daftar komentar
                    when {
                        isCommentsLoading && episodeComments.isEmpty() -> {
                            Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accentColor, strokeWidth = 2.dp)
                            }
                        }
                        episodeComments.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text("Belum ada komentar. Jadi yang pertama!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            val topLevel = remember(episodeComments) {
                                episodeComments.reversed().filter { it.parent_comment_id == null }
                            }
                            Column {
                                topLevel.forEachIndexed { index, c ->
                                    val replies = remember(episodeComments, c.id) {
                                        episodeComments.filter { it.parent_comment_id == c.id }
                                    }
                                    EpisodeCommentRow(
                                        c = c,
                                        isMe = c.user_id == session.userId,
                                        accentColor = accentColor,
                                        clanTagMap = clanTagMap,
                                        onReplyClick = { replyTarget = c.id to c.username },
                                        onDeleteClick = { deleteTargetId = c.id }
                                    )
                                    replies.forEach { r ->
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Spacer(modifier = Modifier.width(30.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(20.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                EpisodeCommentRow(
                                                    c = r,
                                                    isMe = r.user_id == session.userId,
                                                    accentColor = accentColor,
                                                    clanTagMap = clanTagMap,
                                                    isReply = true,
                                                    onReplyClick = { replyTarget = c.id to r.username },
                                                    onDeleteClick = { deleteTargetId = r.id }
                                                )
                                            }
                                        }
                                    }

                                    if (index != topLevel.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            if (deleteTargetId != null) {
                AlertDialog(
                    onDismissRequest = { deleteTargetId = null },
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                    title = { Text("Hapus Komentar?") },
                    text = { Text("Komentar yang dihapus tidak bisa dikembalikan.") },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteTargetId?.let { viewModel.deleteEpisodeComment(currentEpisodeSlug, it) }
                            deleteTargetId = null
                        }) { Text("Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteTargetId = null }) { Text("Batal") }
                    }
                )
            }
        }
    }
}

// ================================================================
// 7b. RESET PASSWORD SCREEN (dari deep link email)
// ================================================================

@Composable
fun ResetPasswordScreen(
    accessToken: String?,
    viewModel: AnikuViewModel,
    onDone: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
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
            Text(
                "Buat Password Baru",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (accessToken.isNullOrBlank()) {
                Text(
                    "Link reset password tidak valid atau sudah kedaluwarsa. Coba kirim ulang dari halaman login.",
                    fontSize = 13.sp,
                    color = Color(0xFFFF5252),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDone) {
                    Text("Kembali")
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; errorMsg = null },
                label = { Text("Password baru") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMsg = null },
                label = { Text("Konfirmasi password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            errorMsg?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFFFF5252))
            }
            successMsg?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFF4CAF50))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    when {
                        newPassword.length < 6 -> errorMsg = "Password minimal 6 karakter"
                        newPassword != confirmPassword -> errorMsg = "Konfirmasi password tidak cocok"
                        else -> {
                            loading = true
                            errorMsg = null
                            viewModel.updatePasswordWithToken(accessToken, newPassword) { success, err ->
                                loading = false
                                if (success) {
                                    successMsg = "Password berhasil diubah, silakan login ulang"
                                } else {
                                    errorMsg = err ?: "Gagal mengubah password"
                                }
                            }
                        }
                    }
                },
                enabled = !loading && successMsg == null,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (successMsg != null) "Selesai" else "Simpan Password")
                }
            }

            if (successMsg != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDone) {
                    Text("Kembali ke Login")
                }
            }
        }
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

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotLoading by remember { mutableStateOf(false) }
    var forgotResultMessage by remember { mutableStateOf<String?>(null) }

    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var googleLoading by remember { mutableStateOf(false) }

    // GANTI dengan Web Client ID (bukan Android Client ID) dari Google Cloud Console
    val googleWebClientId = "592000451797-8tq0be87569l9m2pll8b3jtv2k7ci4m5.apps.googleusercontent.com"

    fun launchGoogleSignIn() {
        coroutineScope.launch {
            googleLoading = true
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(googleWebClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = CredentialManager.create(context)
                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val googleIdTokenCredential = GoogleIdTokenCredential
                    .createFrom(result.credential.data)
                val idToken = googleIdTokenCredential.idToken

                viewModel.loginWithGoogle(idToken) {
                    googleLoading = false
                    onAuthSuccess()
                }
            } catch (e: GetCredentialException) {
                googleLoading = false
                Log.e("AnikuVM", "Google Sign-In dibatalkan/gagal: ${e.message}")
            } catch (e: Exception) {
                googleLoading = false
                Log.e("AnikuVM", "Google Sign-In Exception", e)
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "auth_ambient")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Ambient glow blobs (dekorasi latar, tidak flat) ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-70).dp, y = 20.dp)
                .size(220.dp)
                .blur(90.dp)
                .background(accentColor.copy(alpha = glowAlpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = 180.dp)
                .size(180.dp)
                .blur(80.dp)
                .background(accentColor.copy(alpha = glowAlpha * 0.7f), CircleShape)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo icon dengan pulsing glow + gradient
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.6f, animationSpec = tween(550, easing = EaseOutBack))
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer { scaleX = logoScale; scaleY = logoScale }
                        .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = accentColor, ambientColor = accentColor)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val appIconBitmap = remember {
                        val ctx = context
                        runCatching {
                            val drawable = ctx.packageManager.getApplicationIcon(ctx.packageName)
                            drawable.toBitmap(width = 192, height = 192).asImageBitmap()
                        }.getOrNull()
                    }
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "Aniku",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Aniku",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stylized Cinematic Logo
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 100)) +
                        slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(500, delayMillis = 100, easing = EaseOutCubic))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Cinema-grade Anime Portal",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontFamily = com.example.ui.theme.ComicBodyFont,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Card Form
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(550, delayMillis = 180)) +
                        slideInVertically(initialOffsetY = { it / 5 }, animationSpec = tween(550, delayMillis = 180, easing = EaseOutCubic))
            ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(accentColor.copy(alpha = 0.25f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Segment control Tab dengan indikator geser animasi
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp)
                    ) {
                        val tabWidth = maxWidth / 2
                        val indicatorOffset by animateDpAsState(
                            targetValue = if (isLoginTab) 0.dp else tabWidth,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "tabIndicator"
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .height(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.85f))))
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { isLoginTab = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val color by animateColorAsState(if (isLoginTab) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), label = "loginTabColor")
                                Text("Masuk", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { isLoginTab = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val color by animateColorAsState(if (!isLoginTab) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), label = "daftarTabColor")
                                Text("Daftar", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedVisibility(visible = authError != null) {
                        Text(text = authError ?: "", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }

                    // Email Field
                    Text("Email", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth().testTag("auth_email"),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = accentColor,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Username field (only in signup) dengan animasi tampil/hilang
                    AnimatedVisibility(
                        visible = !isLoginTab,
                        enter = fadeIn(tween(300)) + expandVertically(animationSpec = tween(300, easing = EaseOutCubic)),
                        exit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200))
                    ) {
                        Column {
                            Text("Username", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.fillMaxWidth().testTag("auth_username"),
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = accentColor,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // Password Field
                    var passwordVisible by remember { mutableStateOf(false) }
                    Text("Sandi", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().testTag("auth_password"),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = accentColor,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    AnimatedVisibility(visible = isLoginTab) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    forgotEmail = email.trim()
                                    forgotResultMessage = null
                                    showForgotPasswordDialog = true
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Lupa Password?", color = accentColor, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submission action — gradient button dengan animasi tekan
                    val submitInteractionSource = remember { MutableInteractionSource() }
                    val isPressed by submitInteractionSource.collectIsPressedAsState()
                    val submitScale by animateFloatAsState(if (isPressed) 0.97f else 1f, animationSpec = tween(120), label = "submitScale")
                    Button(
                        onClick = {
                            if (isLoginTab) {
                                viewModel.login(email.trim(), password.trim(), onAuthSuccess)
                            } else {
                                viewModel.register(email.trim(), password.trim(), username.trim(), onAuthSuccess)
                            }
                        },
                        interactionSource = submitInteractionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .graphicsLayer { scaleX = submitScale; scaleY = submitScale }
                            .testTag("auth_submit"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        enabled = !authLoading
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.8f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(targetState = authLoading, label = "submitContent") { loading ->
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Text(if (isLoginTab) "Masuk" else "Daftar", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Divider "atau"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        Text(
                            text = "  atau  ",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tombol Login dengan Google
                    OutlinedButton(
                        onClick = { launchGoogleSignIn() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !authLoading && !googleLoading
                    ) {
                        AnimatedContent(targetState = googleLoading, label = "googleContent") { loading ->
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "G",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = accentColor
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Lanjutkan dengan Google", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Guest Mode link
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 260))
            ) {
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

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showForgotPasswordDialog = false
                forgotResultMessage = null
            },
            title = { Text("Lupa Password") },
            text = {
                Column {
                    Text(
                        "Masukkan email akun kamu, link reset password akan dikirim ke email tersebut.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !forgotLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    forgotResultMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = if (msg.startsWith("Gagal")) Color(0xFFFF5252) else Color(0xFF4CAF50)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetEmail = forgotEmail.trim()
                        if (targetEmail.isEmpty()) {
                            forgotResultMessage = "Gagal: Email tidak boleh kosong"
                        } else {
                            forgotLoading = true
                            forgotResultMessage = null
                            viewModel.sendAuthRecovery(targetEmail) { success ->
                                forgotLoading = false
                                forgotResultMessage = if (success) {
                                    "Link reset password sudah dikirim, cek email kamu ya"
                                } else {
                                    "Gagal: Gagal mengirim email, coba lagi nanti"
                                }
                            }
                        }
                    },
                    enabled = !forgotLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    if (forgotLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Kirim")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgotPasswordDialog = false
                    forgotResultMessage = null
                }) {
                    Text("Tutup")
                }
            }
        )
    }
}

// ================================================================
// 9. PROFILE SCREEN (USER EDIT)
// ================================================================

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    sub: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column {
                Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                if (sub != null) {
                    Text(sub, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 11.sp)
                }
            }
        }
        if (trailingText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trailingText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 12.5.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onNavigateToSecurity: () -> Unit = {}
) {
    val sess by viewModel.session.collectAsState()
    val isDark by viewModel.isDark.collectAsState()
    val isUploading by viewModel.isUploadingAvatar.collectAsState()
    val seasonXp by viewModel.seasonXp.collectAsState()
    val seasonLevel by viewModel.seasonLevel.collectAsState()
    val ownBannerUrl by viewModel.ownBannerUrl.collectAsState()
    val isUploadingBanner by viewModel.isUploadingBanner.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    // Aksen kedua khusus buat gradient (ring avatar, progress bar, role pill) — bukan warna baru buat elemen lain
    val goldAccent = Color(0xFFFFC24B)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadSeasonProgress()
    }

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

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadBanner(uri) { processing ->
                if (!processing) {
                    Toast.makeText(context, "Banner berhasil diunggah!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Progress level musim ini — dianimasikan dari 0 ke target setiap kali screen muncul / datanya berubah
    val currentLevelBaseXp = 20 * (seasonLevel - 1) * (seasonLevel - 1)
    val nextLevelXp = 20 * seasonLevel * seasonLevel
    val xpIntoLevel = (seasonXp - currentLevelBaseXp).coerceAtLeast(0)
    val xpNeededForLevel = (nextLevelXp - currentLevelBaseXp).coerceAtLeast(1)
    val targetProgress = (xpIntoLevel.toFloat() / xpNeededForLevel.toFloat()).coerceIn(0f, 1f)

    var progressAnimStarted by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (progressAnimStarted) targetProgress else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "level_progress_anim"
    )
    LaunchedEffect(targetProgress) {
        progressAnimStarted = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Toolbar header
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

        // ── Banner profil (tap buat ganti) dengan fade gradient ke background ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.6f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { bannerPickerLauncher.launch("image/*") }
        ) {
            if (!ownBannerUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ownBannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Tap untuk tambah banner",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }

            // Fade dari transparan (atas) ke warna background (bawah) supaya transisi ke konten halus
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            0.78f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            )

            if (isUploadingBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            Icon(
                Icons.Default.Edit,
                contentDescription = "Ganti banner",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                    .padding(6.dp)
            )
        }

        // ── Blok identitas: avatar overlap banner + username/role/email jadi satu blok ──
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // Avatar dengan ring gradient, ditarik naik nutupin bagian bawah banner
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .offset(y = (-48).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(accentColor, goldAccent, accentColor)))
                            .padding(2.5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sess.avatarUrl.isNullOrEmpty()) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(38.dp)
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(sess.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                        if (isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    // Badge kecil buat ganti avatar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .border(2.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Ganti avatar", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Username + UID + role pill jadi satu blok identitas
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = sess.username.orDefault("-"),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        sess.userNumber?.let { num ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "#$num",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (sess.isAdmin || sess.isModerator) {
                        Spacer(modifier = Modifier.height(7.dp))
                        val roleColor = if (sess.isAdmin) accentColor else Color(0xFF7C4DFF)
                        val roleText = if (sess.isAdmin) "ADMINISTRATOR" else "MODERATOR"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(roleColor.copy(alpha = 0.16f), goldAccent.copy(alpha = 0.10f))))
                                .border(1.dp, roleColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(text = roleText, color = roleColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = sess.email ?: "-",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // ── Konten: level card, edit profil, akun, logout ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Card level musim ini — progress bar gradient teranimasi
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Level Musim Ini",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lv.$seasonLevel", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(Brush.horizontalGradient(listOf(accentColor, goldAccent)))
                        )
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    Text(
                        "$xpIntoLevel / $xpNeededForLevel XP ke Level ${seasonLevel + 1}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            // Section: Edit Profil
            Text(
                "EDIT PROFIL",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "NAMA PENGGUNA",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                    TextField(
                        value = usernameEditor,
                        onValueChange = { usernameEditor = it },
                        modifier = Modifier.fillMaxWidth().testTag("profile_username_input"),
                        shape = RoundedCornerShape(13.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = accentColor,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Button(
                onClick = {
                    if (usernameEditor.isBlank()) {
                        Toast.makeText(context, "Nama pengguna tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateProfileUsername(usernameEditor) {
                            Toast.makeText(context, "Username berhasil diubah!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_save_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
            }

            // Section: Akun
            Text(
                "AKUN",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    if (sess.userNumber != null) {
                        ProfileInfoRow(
                            icon = Icons.Default.Badge,
                            iconTint = goldAccent,
                            iconBg = goldAccent.copy(alpha = 0.1f),
                            label = "ID Pengguna",
                            sub = "#${sess.userNumber}"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    }
                    ProfileInfoRow(
                        icon = Icons.Default.Security,
                        iconTint = Color(0xFF4ADE80),
                        iconBg = Color(0xFF4ADE80).copy(alpha = 0.1f),
                        label = "Keamanan Akun",
                        trailingText = "Kelola",
                        onClick = onNavigateToSecurity
                    )
                }
            }

            // Role notice lama tetap dipertahankan sebagai info tambahan (opsional, ringkas)
            Spacer(modifier = Modifier.height(2.dp))

            // Logout Button
            Button(
                onClick = {
                    viewModel.logout {
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0x1AFF4B3E) else Color(0xFFFFEBEE)
                ),
                border = BorderStroke(1.dp, Color(0xFFFF4B3E).copy(alpha = 0.25f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("profile_logout_btn")
            ) {
                Text("Keluar (Logout)", color = Color(0xFFFF6B5E), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ================================================================
// 10. ADMIN PANEL SCREEN
// ================================================================

@OptIn(ExperimentalLayoutApi::class)
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
    val sess by viewModel.session.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    LaunchedEffect(banStatusMessage) {
        banStatusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Users, 1: Announcements, 2: Slider, 3: Blacklist Anime, 4: Blacklist Genre, 5: Anime Request

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

    // Search query for user management tab
    var userSearchQuery by remember { mutableStateOf("") }

    // Dihitung di scope @Composable biasa (bukan di dalam item{}) karena dipakai
    // dua tempat: buat nampilin empty-state di header, DAN buat items() list kartu
    // user di bawahnya -- keduanya di luar satu sama lain di LazyListScope.
    val filteredUsers = remember(users, userSearchQuery) {
        if (userSearchQuery.isBlank()) {
            users
        } else {
            val q = userSearchQuery.trim().removePrefix("#")
            users.filter { u ->
                (u.username?.contains(q, ignoreCase = true) == true) ||
                    (u.user_number?.toString()?.contains(q, ignoreCase = true) == true)
            }
        }
    }

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
                "Blacklist Anime" to Icons.Default.Block,
                "Blacklist Genre" to Icons.Default.FilterAltOff,
                "Anime Request" to Icons.Default.VideoLibrary,
                "Feature Flags" to Icons.Default.Science
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Section A: Users List
                        item {
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

                        OutlinedTextField(
                            value = userSearchQuery,
                            onValueChange = { userSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari username atau #id...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (userSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { userSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus pencarian", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (filteredUsers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Pengguna tidak ditemukan",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        } // end header item

                        // Kartu tiap user dirender lewat items() (LazyColumn) supaya cuma
                        // yang keliatan di layar yang di-compose & avatar-nya di-load --
                        // sebelumnya pakai forEach di Column biasa, jadi SEMUA user (avatar,
                        // dialog state, dll) ke-compose sekaligus pas panel dibuka. Kalau
                        // usernya udah ratusan, ini yang bikin lag parah sampai crash/OOM.
                        items(filteredUsers, key = { u: com.example.network.ProfileDto -> u.id }) { usr: com.example.network.ProfileDto ->
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
                                                username = usr.username.orDefault("?"),
                                                size = 44.dp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = usr.username.orDefault("Tamu"),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                usr.user_number?.let { num ->
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "#$num",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                    )
                                                }
                                                if (usr.isAdmin()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    AdminBadge()
                                                } else if (usr.isModerator()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    ModeratorBadge()
                                                } else if (usr.isBeta()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    BetaBadge()
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
                                                    text = "  •  ${usr.roleLabel()}",
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

                                        // Swap ID button - hanya admin
                                        var showSwapDialog by remember { mutableStateOf(false) }
                                        if (sess.isAdmin) IconButton(
                                            onClick = { showSwapDialog = true },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = "Swap ID", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                        if (showSwapDialog) {
                                            var swapSearchQuery by remember { mutableStateOf("") }
                                            val swapTargets = remember(users, usr.id, swapSearchQuery) {
                                                val base = users.filter { it.id != usr.id && it.user_number != null }
                                                if (swapSearchQuery.isBlank()) base
                                                else {
                                                    val q = swapSearchQuery.trim().removePrefix("#")
                                                    base.filter { t ->
                                                        (t.username?.contains(q, ignoreCase = true) == true) ||
                                                            (t.user_number?.toString()?.contains(q, ignoreCase = true) == true)
                                                    }
                                                }
                                            }
                                            AlertDialog(
                                                onDismissRequest = { showSwapDialog = false },
                                                title = { Text("Tukar ID #${usr.user_number}") },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        Text("Pilih user untuk tukar ID dengan ${usr.username}:", fontSize = 13.sp)

                                                        OutlinedTextField(
                                                            value = swapSearchQuery,
                                                            onValueChange = { swapSearchQuery = it },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            placeholder = { Text("Cari username atau #id...", fontSize = 12.sp) },
                                                            leadingIcon = {
                                                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            },
                                                            trailingIcon = {
                                                                if (swapSearchQuery.isNotEmpty()) {
                                                                    IconButton(onClick = { swapSearchQuery = "" }) {
                                                                        Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(14.dp))
                                                                    }
                                                                }
                                                            },
                                                            singleLine = true,
                                                            shape = RoundedCornerShape(50),
                                                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                                            )
                                                        )

                                                        if (swapTargets.isEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 16.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    "User tidak ditemukan",
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                                )
                                                            }
                                                        } else {
                                                            LazyColumn(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .heightIn(max = 320.dp),
                                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                items(swapTargets, key = { t: com.example.network.ProfileDto -> t.id }) { target: com.example.network.ProfileDto ->
                                                                    Button(
                                                                        onClick = {
                                                                            viewModel.swapUserNumber(usr, target)
                                                                            showSwapDialog = false
                                                                        },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Text(
                                                                            "${target.username} (#${target.user_number})",
                                                                            color = MaterialTheme.colorScheme.onSurface
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                confirmButton = {},
                                                dismissButton = { TextButton(onClick = { showSwapDialog = false }) { Text("Batal") } }
                                            )
                                        }

                                        // +DM button (kredit Diamond manual) - hanya admin
                                        var showAddDmDialog by remember { mutableStateOf(false) }
                                        if (sess.isAdmin) IconButton(
                                            onClick = { showAddDmDialog = true },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF4FD8E8).copy(alpha = 0.15f))
                                        ) {
                                            Icon(Icons.Default.Diamond, contentDescription = "Tambah DM", tint = Color(0xFF4FD8E8), modifier = Modifier.size(18.dp))
                                        }
                                        if (showAddDmDialog) {
                                            var dmAmount by remember { mutableStateOf("") }
                                            AlertDialog(
                                                onDismissRequest = { showAddDmDialog = false },
                                                title = { Text("Tambah Diamond \u2014 ${usr.username.orDefault("User")}") },
                                                text = {
                                                    Column {
                                                        Text("Saldo saat ini: ${usr.diamond_balance ?: 0} DM", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        OutlinedTextField(
                                                            value = dmAmount,
                                                            onValueChange = { dmAmount = it.filter { c -> c.isDigit() } },
                                                            label = { Text("Jumlah DM ditambahkan") },
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        dmAmount.toIntOrNull()?.let { viewModel.adminAddDiamond(usr.id, it) }
                                                        showAddDmDialog = false
                                                        dmAmount = ""
                                                    }) { Text("Tambah") }
                                                },
                                                dismissButton = { TextButton(onClick = { showAddDmDialog = false }) { Text("Batal") } }
                                            )
                                        }

                                        // Set Role button - hanya admin
                                        var showRoleDialog by remember { mutableStateOf(false) }
                                        if (sess.isAdmin) IconButton(
                                            onClick = { showRoleDialog = true },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(Icons.Default.ManageAccounts, contentDescription = "Set Role", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                        if (showRoleDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showRoleDialog = false },
                                                title = { Text("Set Role: ${usr.username}") },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        listOf("user", "moderator", "admin", "beta").forEach { roleOption ->
                                                            val isSelected = usr.role == roleOption
                                                            Button(
                                                                onClick = {
                                                                    viewModel.updateUserRole(usr, roleOption)
                                                                    showRoleDialog = false
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                                ),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = when(roleOption) { "admin" -> "Admin"; "moderator" -> "Moderator"; "beta" -> "Beta"; else -> "Pengguna" },
                                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                confirmButton = {},
                                                dismissButton = {
                                                    TextButton(onClick = { showRoleDialog = false }) { Text("Batal") }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Section B: Announcements Create Form
                        item {
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
                        }
                        } // end form item

                        items(announcements, key = { a: com.example.network.AnnouncementDto -> a.id }) { ann: com.example.network.AnnouncementDto ->
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

                    2 -> {
                        // Section C: Featured Slider Overwrite Hero Manual
                        item {
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
                        }
                        } // end form item

                        items(featured, key = { f: com.example.network.FeaturedAnimeDto -> f.id }) { ft: com.example.network.FeaturedAnimeDto ->
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

                    3 -> {
                        // Section D: Blacklist Management
                        item {
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
                        }
                        } // end form item

                        items(blacklist, key = { b: com.example.network.BlacklistedAnimeDto -> b.id }) { bl: com.example.network.BlacklistedAnimeDto ->
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
                    4 -> {
                        item {
                        // Section E: Blacklist Genre — tinggal tap chip genre yang mau disembunyikan, gak perlu form
                        val genresListRaw by viewModel.genres.collectAsState()
                        val blacklistGenres by viewModel.adminBlacklistGenres.collectAsState()
                        val blacklistedGenreSlugSet = remember(blacklistGenres) { blacklistGenres.map { it.genre_slug }.toSet() }

                        Text("Blacklist Genre", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap genre buat sembunyikan dari daftar pilihan di Eksplor. Tap lagi buat nampilin.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (genresListRaw.isEmpty()) {
                            Text("Memuat daftar genre...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else {
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                genresListRaw.forEach { genre ->
                                    val isBlacklisted = blacklistedGenreSlugSet.contains(genre.slug)
                                    FilterChip(
                                        selected = isBlacklisted,
                                        onClick = { viewModel.toggleGenreBlacklist(genre.slug, genre.name) },
                                        label = {
                                            Text(
                                                genre.name,
                                                fontSize = 12.sp,
                                                textDecoration = if (isBlacklisted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                        },
                                        leadingIcon = if (isBlacklisted) {
                                            { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isBlacklisted,
                                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                                            selectedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        if (blacklistGenres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "${blacklistGenres.size} genre disembunyikan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        }
                    }
                    5 -> {
                        item { AdminRequestAnimeSection(viewModel = viewModel) }
                    }
                    6 -> {
                        item { FeatureFlagsAdminSection(viewModel = viewModel) }
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
fun AnimatedSettingsItem(
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
fun SettingsNavCard(
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = sess.username.nullIfBlank() ?: sess.email ?: "Pengguna",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            sess.userNumber?.let { num ->
                                                Text(
                                                    text = "#$num",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            when {
                                                sess.isAdmin -> AdminBadge()
                                                sess.isModerator -> ModeratorBadge()
                                                sess.isBeta -> BetaBadge()
                                                else -> Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(accentColor.copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Member",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = accentColor
                                                    )
                                                }
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

                                // Picker warna nama chat - cuma nongol buat role beta/moderator/
                                // admin (sesuai yang diizinin trigger enforce_custom_name_color
                                // di server). Klik warna yang sama lagi buat reset ke default.
                                if (sess.isBeta || sess.canModerate()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Warna Nama Chat",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val presetColors = listOf(
                                        "#22D3EE", "#F472B6", "#A78BFA", "#FACC15",
                                        "#4ADE80", "#FB923C", "#F87171", "#60A5FA"
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        presetColors.forEach { hex ->
                                            val parsed = Color(android.graphics.Color.parseColor(hex))
                                            val isSelected = sess.customNameColor.equals(hex, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(parsed)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.dp,
                                                        color = Color.White,
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        val newValue = if (isSelected) null else hex
                                                        viewModel.updateMyNameColor(newValue)
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Section B: Admin Panel ───────────────────────────
                if (!sess.token.isNullOrEmpty() && sess.canModerate()) {
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

                // ── Donation Card (Material3, konsisten sama card Preferensi di atas) ──
                AnimatedSettingsItem(index = 7) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Dukung Aniku",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Bantu kelangsungan aplikasi",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            // Sociabuzz row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sociabuzz.com/dayynime/tribe")))
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Sociabuzz", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("sociabuzz.com/dayynime/tribe", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                            // Trakteer row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://trakteer.id/Dayynimee")))
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Trakteer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("trakteer.id/Dayynimee", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // ── Section Label: Aplikasi ──────────────────────────
                // ── Section Label: Komunitas ─────────────────────────
                AnimatedSettingsItem(index = 8) {
                    Text(
                        text = "KOMUNITAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // ── Komunitas Card ───────────────────────────────────
                AnimatedSettingsItem(index = 9) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Telegram Channel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Dayynime")))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF229ED9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Telegram icon (paper plane)
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
                                        val w = size.width; val h = size.height
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(w * 0.1f, h * 0.5f)
                                            lineTo(w * 0.9f, h * 0.18f)
                                            lineTo(w * 0.62f, h * 0.82f)
                                            lineTo(w * 0.42f, h * 0.62f)
                                            close()
                                        }
                                        drawPath(path, androidx.compose.ui.graphics.Color.White)
                                        val path2 = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(w * 0.42f, h * 0.62f)
                                            lineTo(w * 0.4f, h * 0.82f)
                                            lineTo(w * 0.54f, h * 0.7f)
                                            close()
                                        }
                                        drawPath(path2, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Telegram Channel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("t.me/Dayynime", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(start = 70.dp))

                            // Telegram Bot
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Dayynime_bot")))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF229ED9).copy(alpha = 0.75f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Bot icon (simple robot head)
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Telegram Bot", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("t.me/Dayynime_bot", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(start = 70.dp))

                            // WhatsApp Komunitas
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.whatsapp.com/FPloru2UpCY4Os0rn5Rq03")))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF25D366)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // WhatsApp icon
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
                                        val w = size.width; val h = size.height
                                        // Bubble shape
                                        drawCircle(androidx.compose.ui.graphics.Color.White, radius = w * 0.42f, center = center)
                                        // Phone handset simplified
                                        val phonePath = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(w * 0.35f, h * 0.25f)
                                            cubicTo(w * 0.28f, h * 0.25f, w * 0.22f, h * 0.35f, w * 0.25f, h * 0.45f)
                                            cubicTo(w * 0.30f, h * 0.62f, w * 0.40f, h * 0.72f, w * 0.57f, h * 0.76f)
                                            cubicTo(w * 0.67f, h * 0.79f, w * 0.76f, h * 0.73f, w * 0.76f, h * 0.66f)
                                            lineTo(w * 0.76f, h * 0.58f)
                                            cubicTo(w * 0.76f, h * 0.55f, w * 0.74f, h * 0.53f, w * 0.71f, h * 0.52f)
                                            lineTo(w * 0.63f, h * 0.50f)
                                            cubicTo(w * 0.60f, h * 0.49f, w * 0.57f, h * 0.50f, w * 0.55f, h * 0.52f)
                                            lineTo(w * 0.53f, h * 0.55f)
                                            cubicTo(w * 0.47f, h * 0.52f, w * 0.48f, h * 0.53f, w * 0.45f, h * 0.47f)
                                            lineTo(w * 0.47f, h * 0.45f)
                                            cubicTo(w * 0.50f, h * 0.43f, w * 0.51f, h * 0.40f, w * 0.50f, h * 0.37f)
                                            lineTo(w * 0.48f, h * 0.29f)
                                            cubicTo(w * 0.47f, h * 0.26f, w * 0.45f, h * 0.24f, w * 0.42f, h * 0.24f)
                                            close()
                                        }
                                        drawPath(phonePath, Color(0xFF25D366))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("WhatsApp Komunitas", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Grup komunitas Aniku", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(start = 70.dp))

                            // Facebook
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/vppxbn3e8h")))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1877F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Facebook,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Facebook", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Dayynime", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // ── Section Label: Aplikasi ──────────────────────────
                AnimatedSettingsItem(index = 10) {
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
                AnimatedSettingsItem(index = 11) {
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
                AnimatedSettingsItem(index = 12) {
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
        Column {
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
        } // end Column wrapper for AnimatedVisibility
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
    val activePreset by viewModel.themePreset.collectAsState()
    val activeCardStyle by viewModel.cardStyle.collectAsState()
    val activeNavStyle by viewModel.navStyle.collectAsState()
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
            // ── Section: Card Style ──
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Style Card Anime", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    val cardOptions = listOf("Rounded" to "Sudut bulat", "Sharp" to "Sudut tajam", "Poster" to "Judul overlay", "Wide" to "Landscape 16:9")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cardOptions.forEach { (style, desc) ->
                            val isSelected = activeCardStyle == style
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else Color.White.copy(0.1f), RoundedCornerShape(10.dp))
                                    .background(if (isSelected) accentColor.copy(0.08f) else MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.changeCardStyle(style) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.size(16.dp).background(accentColor, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    }
                                } else {
                                    Box(modifier = Modifier.size(16.dp).background(Color.White.copy(0.08f), CircleShape))
                                }
                                Text(style, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Text(desc, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // ── Section: Nav Style ──
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Style Bottom Navigation", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    val navOptions = listOf(
                        "IconLabel" to "Icon + Label",
                        "IconOnly"  to "Icon + titik aktif",
                        "PillLabel" to "Pill label aktif",
                        "PillIcon"  to "Pill icon only"
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        navOptions.forEach { (style, desc) ->
                            val isSelected = activeNavStyle == style
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else Color.White.copy(0.1f), RoundedCornerShape(10.dp))
                                    .background(if (isSelected) accentColor.copy(0.08f) else MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.changeNavStyle(style) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.size(16.dp).background(accentColor, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    }
                                } else {
                                    Box(modifier = Modifier.size(16.dp).background(Color.White.copy(0.08f), CircleShape))
                                }
                                Text(desc, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // ── Section: Theme Preset ──
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Tema Preset",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.05.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val presets = listOf(
                        Triple("Default", Color(0xFF0A0A0A), Color(0xFFE53935)),
                        Triple("Netflix", Color(0xFF141414), Color(0xFFE50914)),
                        Triple("Midnight", Color(0xFF0B0C1A), Color(0xFF7C5AF6))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        presets.forEach { (name, bgColor, acColor) ->
                            val isSelected = activePreset == name
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) acColor else Color.White.copy(0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(bgColor)
                                    .clickable { viewModel.changeThemePreset(name) }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Mini preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bgColor.copy(alpha = 0.6f))
                                ) {
                                    // Fake nav bar
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .background(bgColor)
                                    )
                                    // Fake card
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(width = 28.dp, height = 20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(acColor.copy(0.8f))
                                    )
                                    // Fake accent dot
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(6.dp)
                                            .background(acColor, CircleShape)
                                    )
                                }
                                // Checkmark or name
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(acColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Text(
                                    name,
                                    color = if (isSelected) acColor else Color.White.copy(0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Pilihan Tema" + if (activePreset != "Default") "  (diatur oleh preset)" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
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
    val disabledSources by viewModel.disabledDataSources.collectAsState()
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
                "Dayynime-v2" to "Sumber alternatif (server 2)",
                "Dayynime-v3" to "Animekompi (server 3)",
                "Dayynime-v4" to "Donghua (server 4)"
            )

            sources.forEach { (sourceKey, sourceDesc) ->
                val isSelected = currentSource == sourceKey
                val isDisabled = sourceKey in disabledSources
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDisabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            isSelected -> accentColor.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = if (isSelected && !isDisabled) androidx.compose.foundation.BorderStroke(1.5.dp, accentColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isDisabled) Modifier
                            else Modifier.clickable {
                                viewModel.changeDataSource(sourceKey)
                                viewModel.loadHomeData()
                                viewModel.loadSearchPopular()
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sourceKey,
                                color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (isDisabled) "Sementara nonaktif" else sourceDesc,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDisabled) 0.5f else 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isSelected && !isDisabled,
                            enabled = !isDisabled,
                            onCheckedChange = {
                                viewModel.changeDataSource(sourceKey)
                                viewModel.loadHomeData()
                                viewModel.loadSearchPopular()
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

private data class SupporterRow(
    val name: String,
    val amount: Int,
    val matchedProfile: com.example.network.ProfileDto?
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TopSupporterScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val donations by viewModel.donations.collectAsState()
    val userDirectory by viewModel.userDirectory.collectAsState()
    val scope = rememberCoroutineScope()

    // Group by supporter_name dan jumlahkan total_amount, terus dicocokkan ke
    // akun Aniku (avatar/level/nomor ID) kalau username-nya sama persis --
    // pola sama kayak widget "Top Supporter" di Home.
    val leaderboard = donations
        .groupBy { it.supporter_name }
        .map { (name, list) ->
            val amount = list.sumOf { it.total_amount ?: 0 }
            val matched = userDirectory.firstOrNull {
                it.username?.trim()?.equals(name?.trim(), ignoreCase = true) == true
            }
            SupporterRow(name = name ?: "Anonim", amount = amount, matchedProfile = matched)
        }
        .sortedByDescending { it.amount }

    var isRefreshing by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadDonations()
        viewModel.loadUserDirectory()
        hasLoadedOnce = true
    }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            viewModel.loadDonations()
            delay(450)
            isRefreshing = false
        }
    }

    // Palet warna "perkamen emas" sesuai referensi redesign
    val bg0 = Color(0xFF120F16)
    val ivory = Color(0xFFF2EAD9)
    val ivoryDim = Color(0xFFB9AD97)
    val gold = Color(0xFFD8AD5F)
    val goldSoft = Color(0xFFE9CB8E)
    val brass = Color(0xFF8C724A)
    val pewter = Color(0xFFABA599)
    val copper = Color(0xFFA9633B)
    val lineGold = gold.copy(alpha = 0.22f)
    val lineDim = Color.White.copy(alpha = 0.06f)

    val goldGradient = Brush.linearGradient(listOf(goldSoft, gold))
    val silverGradient = Brush.linearGradient(listOf(Color(0xFFD8D2C2), pewter))
    val bronzeGradient = Brush.linearGradient(listOf(Color(0xFFC98A5E), copper))

    // Trigger buat staggered entrance animation begitu data pertama kali landing
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(leaderboard.isNotEmpty()) {
        if (leaderboard.isNotEmpty()) {
            contentVisible = false
            delay(40)
            contentVisible = true
        }
    }

    // Trophy melayang pelan + glow napas di hero
    val heroTransition = rememberInfiniteTransition(label = "hero")
    val trophyFloat by heroTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophyFloat"
    )
    val heroGlowAlpha by heroTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroGlow"
    )

    Scaffold(
        containerColor = bg0,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text("Top Supporter", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ivory)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = ivory)
                    }
                },
                actions = {
                    val refreshTransition = rememberInfiniteTransition(label = "refreshSpin")
                    val continuousRotation by refreshTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = LinearEasing)
                        ),
                        label = "continuousRotation"
                    )
                    IconButton(onClick = { refresh() }, enabled = !isRefreshing) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Muat ulang",
                            tint = if (isRefreshing) gold else ivoryDim,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (isRefreshing) continuousRotation else 0f
                            }
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = bg0
                )
            )
        }
    ) { padding ->
        if (leaderboard.isEmpty()) {
            // ===== Empty state =====
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg0)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (!hasLoadedOnce) {
                    CircularProgressIndicator(color = gold, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .graphicsLayer { translationY = trophyFloat }
                                .background(Brush.linearGradient(listOf(goldSoft, brass)), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏆", fontSize = 30.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Belum ada supporter",
                            color = ivory,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Jadilah yang pertama support Aniku!",
                            color = ivoryDim,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val top3 = leaderboard.take(3)
            val rest = leaderboard.drop(3)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg0),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // ===== Hero =====
                    item {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(500)) + slideInVertically(
                                initialOffsetY = { -it / 3 },
                                animationSpec = tween(500, easing = FastOutSlowInEasing)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 10.dp, bottom = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    // Glow napas di belakang trophy
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(gold.copy(alpha = heroGlowAlpha), Color.Transparent)
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .graphicsLayer { translationY = trophyFloat }
                                            .background(
                                                Brush.linearGradient(listOf(goldSoft, brass)),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🏆", fontSize = 32.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Hall of Fame",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = gold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Terima kasih sudah support Aniku!",
                                    fontSize = 12.5.sp,
                                    color = ivoryDim
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, lineGold, shape = RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "TOTAL DUKUNGAN TERBESAR",
                                        fontSize = 9.5.sp,
                                        letterSpacing = 0.06.em,
                                        color = brass
                                    )
                                }
                            }
                        }
                    }

                    // ===== Podium rank 1-3 =====
                    item {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(550, delayMillis = 120)) + scaleIn(
                                initialScale = 0.85f,
                                animationSpec = tween(550, delayMillis = 120, easing = FastOutSlowInEasing)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 26.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Silver (kiri)
                                if (top3.size > 1) {
                                    PodiumPlaque(
                                        modifier = Modifier.weight(1f),
                                        rank = 2,
                                        name = top3[1].name,
                                        amountValue = top3[1].amount,
                                        avatarUrl = top3[1].matchedProfile?.avatar_url,
                                        levelValue = top3[1].matchedProfile?.season_level,
                                        rankGradient = silverGradient,
                                        avatarGradient = silverGradient,
                                        borderColor = lineDim,
                                        nameColor = ivory,
                                        amountColor = ivoryDim,
                                        isGold = false,
                                        countDelay = 260
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                // Gold (tengah, lebih besar, dengan glow pulse)
                                PodiumPlaque(
                                    modifier = Modifier.weight(1.15f),
                                    rank = 1,
                                    name = top3[0].name,
                                    amountValue = top3[0].amount,
                                    avatarUrl = top3[0].matchedProfile?.avatar_url,
                                    levelValue = top3[0].matchedProfile?.season_level,
                                    rankGradient = goldGradient,
                                    avatarGradient = goldGradient,
                                    borderColor = gold.copy(alpha = 0.45f),
                                    nameColor = goldSoft,
                                    amountColor = gold,
                                    isGold = true,
                                    countDelay = 180
                                )

                                // Bronze (kanan)
                                if (top3.size > 2) {
                                    PodiumPlaque(
                                        modifier = Modifier.weight(1f),
                                        rank = 3,
                                        name = top3[2].name,
                                        amountValue = top3[2].amount,
                                        avatarUrl = top3[2].matchedProfile?.avatar_url,
                                        levelValue = top3[2].matchedProfile?.season_level,
                                        rankGradient = bronzeGradient,
                                        avatarGradient = bronzeGradient,
                                        borderColor = lineDim,
                                        nameColor = ivory,
                                        amountColor = ivoryDim,
                                        isGold = false,
                                        countDelay = 340
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Garis dasar podium
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                                .padding(top = 4.dp, bottom = 18.dp)
                                .height(2.dp)
                                .background(
                                    Brush.verticalGradient(listOf(lineGold, Color.Transparent))
                                )
                        )
                    }

                    // ===== Sisa daftar (rank 4+), muncul satu-satu (staggered) =====
                    itemsIndexed(rest, key = { i, item -> item.name.ifBlank { "anon-$i" } + "-$i" }) { idx, row ->
                        var rowVisible by remember(row.name, idx) { mutableStateOf(false) }
                        LaunchedEffect(contentVisible) {
                            if (contentVisible) {
                                delay(220L + idx * 55L)
                                rowVisible = true
                            }
                        }
                        AnimatedVisibility(
                            visible = rowVisible,
                            enter = fadeIn(tween(320)) + slideInHorizontally(
                                initialOffsetX = { it / 5 },
                                animationSpec = tween(320, easing = FastOutSlowInEasing)
                            )
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 3.5.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.025f)),
                                border = BorderStroke(1.dp, lineDim),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${idx + 4}",
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF736A82),
                                        modifier = Modifier.width(22.dp)
                                    )
                                    val avatarUrl = row.matchedProfile?.avatar_url
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color.White.copy(alpha = 0.06f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUrl != null) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = row.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                                            )
                                        } else {
                                            Text(
                                                row.name.trim().firstOrNull()?.uppercase() ?: "?",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFCFC7DA)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                row.name,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFCFC7DA),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (row.matchedProfile != null) {
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    Icons.Default.Verified,
                                                    contentDescription = "Akun Aniku terverifikasi",
                                                    tint = Color(0xFF4FC3F7),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                        row.matchedProfile?.let { p ->
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.MilitaryTech,
                                                    contentDescription = null,
                                                    tint = brass,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    "Lvl. ${p.season_level ?: 1}",
                                                    fontSize = 10.sp,
                                                    color = ivoryDim
                                                )
                                                p.user_number?.let { num ->
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "#$num",
                                                        fontSize = 10.sp,
                                                        color = ivoryDim.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AnimatedRupiahText(
                                        target = row.amount,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF9D93AB),
                                        delayMillis = 220 + idx * 55
                                    )
                                }
                            }
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(400, delayMillis = 260 + rest.size * 55))
                        ) {
                            Text(
                                "— akhir daftar —",
                                fontSize = 10.5.sp,
                                color = ivoryDim.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Text angka rupiah yang animasi "ngitung naik" dari 0 ke nilai aslinya. */
@Composable
private fun AnimatedRupiahText(
    target: Int,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(delayMillis.toLong())
        animated.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }
    Text(
        text = formatRupiah(animated.value.toInt()),
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun PodiumPlaque(
    modifier: Modifier = Modifier,
    rank: Int,
    name: String,
    amountValue: Int,
    avatarUrl: String? = null,
    levelValue: Int? = null,
    rankGradient: Brush,
    avatarGradient: Brush,
    borderColor: Color,
    nameColor: Color,
    amountColor: Color,
    isGold: Boolean,
    countDelay: Int = 0
) {
    val avatarSize = if (isGold) 58.dp else 48.dp
    val isVerified = avatarUrl != null

    // Glow pulse cuma buat si juara satu
    val glowTransition = rememberInfiniteTransition(label = "podiumGlow")
    val glowScale by glowTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    // Crown si juara 1 melayang pelan
    val crownTransition = rememberInfiniteTransition(label = "crownFloat")
    val crownOffset by crownTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crownOffset"
    )

    Column(
        modifier = modifier
            .shadow(
                elevation = if (isGold) 10.dp else 3.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = if (isGold) Color(0xFFD8AD5F).copy(alpha = 0.5f) else Color.Black,
                spotColor = if (isGold) Color(0xFFD8AD5F).copy(alpha = 0.5f) else Color.Black
            )
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.045f), Color.White.copy(alpha = 0.015f))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .border(1.dp, borderColor, shape = RoundedCornerShape(18.dp))
            .padding(
                top = if (isGold) 22.dp else 14.dp,
                bottom = if (isGold) 16.dp else 12.dp,
                start = 6.dp,
                end = 6.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Box(contentAlignment = Alignment.Center) {
                if (isGold) {
                    // Glow napas di belakang avatar juara 1
                    Box(
                        modifier = Modifier
                            .size(avatarSize * glowScale)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFE9CB8E).copy(alpha = glowAlpha), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                    // Mahkota di atas avatar juara 1
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-22 + crownOffset).dp)
                            .size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .background(avatarGradient, shape = CircleShape)
                        .border(2.dp, rankGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            name.trim().firstOrNull()?.uppercase() ?: "?",
                            fontSize = if (isGold) 20.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF120F16)
                        )
                    }
                }
                // Badge nomor rank nempel di pojok kanan bawah avatar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(if (isGold) 22.dp else 19.dp)
                        .background(rankGradient, shape = CircleShape)
                        .border(1.5.dp, Color(0xFF120F16), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$rank",
                        fontSize = if (isGold) 11.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF120F16)
                    )
                }
            }
            Spacer(modifier = Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = if (isGold) 13.5.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isVerified) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Akun Aniku terverifikasi",
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(if (isGold) 13.dp else 11.dp)
                    )
                }
            }
            if (levelValue != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Lvl. $levelValue",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = amountColor.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedRupiahText(
                target = amountValue,
                fontSize = if (isGold) 14.5.sp else 12.5.sp,
                fontWeight = if (isGold) FontWeight.Bold else FontWeight.Normal,
                color = amountColor,
                delayMillis = countDelay
            )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Mini Player (in-app floating PIP)
// ─────────────────────────────────────────────────────────────────────────
// Overlay Compose yang ditaruh di ROOT, di atas NavHost (lihat MainActivity).
// Beda sama PIP sistem Android: video kecil ini nempel DI DALAM Aniku, jadi
// NavHost di baliknya tetap jalan normal — user bisa buka home/search/dll
// sementara video tetap muter di kotak kecil draggable ini. ExoPlayer yang
// dipakai di sini instance BARU & terpisah dari yang di WatchScreen (yang
// sudah di-release begitu WatchScreen ditutup lewat tombol minimize).
@Composable
fun MiniPlayerOverlay(
    data: AnikuViewModel.MiniPlayerData,
    onExpand: (Long) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Ukuran resizable, jaga rasio 16:9. Batas biar gak kekecilan/kegedean sampe
    // gak muat di layar atau nutupin kontennya sendiri.
    val minWidthPx = with(density) { 110.dp.toPx() }
    val maxWidthPx = with(density) { (config.screenWidthDp.dp * 0.85f).toPx() }
    var widthPx by remember { mutableStateOf(with(density) { 168.dp.toPx() }) }
    val heightPx = widthPx * 9f / 16f

    // Posisi awal: pojok kanan bawah, di atas bottom nav.
    var offsetX by remember {
        mutableStateOf(with(density) { (config.screenWidthDp.dp.toPx() - widthPx - 12.dp.toPx()) })
    }
    var offsetY by remember {
        mutableStateOf(with(density) { (config.screenHeightDp.dp.toPx() - heightPx - 90.dp.toPx()) })
    }

    val exoPlayer = remember(data.streamUrl) {
        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            com.example.network.VideoExtractor.streamingHttpClient
        ).apply {
            if (data.headers.isNotEmpty()) setDefaultRequestProperties(data.headers)
        }
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(ctx, httpDataSourceFactory)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(
                if (data.streamUrl.contains(".m3u8"))
                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                else
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(data.streamUrl))
                prepare()
                playWhenReady = true
                seekTo(data.startPositionMs)
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Ambang gerak buat bedain "tap" (buka full player) dari "drag" (geser posisi) —
    // gerak di bawah ini dianggap tap, di atasnya dianggap mulai geser.
    val tapSlopPx = with(density) { 12.dp.toPx() }

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { heightPx.toDp() }
            )
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                // SATU handler tunggal buat geser (1 jari), pinch-resize (2 jari), dan
                // tap (buka full player). Sebelumnya ini 3 pointerInput terpisah yang
                // masing-masing baca event mentah yang SAMA tanpa koordinasi — pas pinch
                // 2 jari, detektor "geser" ikut nganggep gerakan itu sebagai drag JUGA,
                // jadi offset ke-update dobel (dari drag + dari resize pinch) sampai
                // videonya kelontang ke luar layar. Sekarang cuma satu loop yang jalan,
                // jadi gak mungkin dobel proses buat gesture yang sama.
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    // Sengaja gak konsumsi down di sini kalau area ini bakal ditangani
                    // handle resize (pojok kanan-bawah) — biar pointerInput handle-nya
                    // (composable terpisah) yang megang gesture ini, gak direbut di sini.
                    val handleZonePx = with(density) { 30.dp.toPx() }
                    val inHandleZone = firstDown.position.x >= size.width - handleZonePx &&
                        firstDown.position.y >= size.height - handleZonePx
                    if (inHandleZone) return@awaitEachGesture

                    firstDown.consume()
                    var totalMovement = 0f
                    var wasPinch = false
                    var prevPinchDistance = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            wasPinch = true
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val dx = p1.x - p2.x
                            val dy = p1.y - p2.y
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (prevPinchDistance > 0f && distance > 0f) {
                                val zoom = distance / prevPinchDistance
                                if (zoom != 1f) {
                                    // Nempel di pojok kiri-atas — cuma widthPx/heightPx yang
                                    // berubah, offsetX/Y TIDAK disentuh. Sebelumnya resize
                                    // "centered" (ngegeser pojok kiri-atas juga biar box
                                    // tetap di tengah), itu penyebab box kelihatan jalan
                                    // sendiri ke atas tiap kali di-resize berkali-kali.
                                    widthPx = (widthPx * zoom).coerceIn(minWidthPx, maxWidthPx)
                                }
                            }
                            prevPinchDistance = distance
                            pressed.forEach { it.consume() }
                        } else {
                            val change = pressed[0]
                            val drag = change.positionChange()
                            offsetX += drag.x
                            offsetY += drag.y
                            totalMovement += kotlin.math.abs(drag.x) + kotlin.math.abs(drag.y)
                            change.consume()
                            prevPinchDistance = 0f
                        }
                    }

                    if (!wasPinch && totalMovement < tapSlopPx) {
                        onExpand(exoPlayer.currentPosition)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Judul kecil biar user tau lagi nonton apa
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = data.animeTitle,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Tombol close
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Tutup mini player",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }

        // Handle resize pojok kanan-bawah — drag buat gedein/kecilin tanpa perlu pinch dua jari.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newWidth = (widthPx + dragAmount.x).coerceIn(minWidthPx, maxWidthPx)
                            widthPx = newWidth
                        }
                    )
                }
        ) {
            Icon(
                Icons.Default.OpenInFull,
                contentDescription = "Ubah ukuran mini player",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 3.dp, end = 3.dp)
            )
        }
    }
}
