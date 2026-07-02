package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.network.AnikuViewModel
import com.example.network.JikanAnimeData
import com.example.network.RequestedAnimeDto

// ─────────────────────────────────────────────────────────────────────────
// 0. HELPERS — grouping banyak video jadi 1 anime dengan banyak episode.
//    Key grouping pakai mal_id kalau ada (paling akurat), fallback ke title.
// ─────────────────────────────────────────────────────────────────────────
private fun RequestedAnimeDto.groupKey(): String =
    mal_id?.toString() ?: title.trim().lowercase()

private fun parseEpisodeNumber(raw: String?): Int? =
    raw?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

private fun episodeLabel(anime: RequestedAnimeDto): String {
    val num = parseEpisodeNumber(anime.episode)
    return if (num != null) "E$num" else anime.episode?.takeIf { it.isNotBlank() } ?: "E1"
}

/** Urutkan episode: yang punya nomor duluan (ascending), sisanya di belakang berdasar created_at. */
private fun List<RequestedAnimeDto>.sortedByEpisode(): List<RequestedAnimeDto> =
    sortedWith(compareBy({ parseEpisodeNumber(it.episode) == null }, { parseEpisodeNumber(it.episode) ?: 0 }, { it.created_at ?: "" }))

/** Representative card per grup: episode dengan nomor terkecil (biasanya E1). */
private fun List<RequestedAnimeDto>.groupedForList(): List<Pair<RequestedAnimeDto, Int>> =
    groupBy { it.groupKey() }
        .map { (_, group) -> group.sortedByEpisode().first() to group.size }
        .sortedByDescending { (anime, _) -> anime.created_at ?: "" }

// ─────────────────────────────────────────────────────────────────────────
// 1. LIST — daftar semua anime hasil request, diakses dari menu "Lainnya"
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun RequestedAnimeListScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit
) {
    val list by viewModel.requestedAnimeList.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.fetchRequestedAnimeList() }

    // User biasa cuma lihat yang udah di-approve admin, dikelompokkan per judul
    // (1 anime bisa punya banyak episode/video, jangan sampai tiap video jadi card sendiri)
    val approved = list.filter { it.status == "approved" }.groupedForList()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("Anime Request", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onBackground)
                Text("Anime hasil request user", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

        if (approved.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Belum ada anime request", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(approved, key = { (anime, _) -> anime.groupKey() }) { (anime, episodeCount) ->
                    RequestedAnimeCard(anime = anime, episodeCount = episodeCount, onClick = { onItemClick(anime.id) })
                }
            }
        }
    }
}

@Composable
private fun RequestedAnimeCard(anime: RequestedAnimeDto, episodeCount: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(anime.poster_url).crossfade(300).build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            anime.rating?.let {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(it, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (episodeCount > 1) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("$episodeCount Eps", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            anime.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 2. DETAIL — poster besar, sinopsis, genre, tombol "Mulai Tonton"
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun RequestedAnimeDetailScreen(
    id: String,
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onWatch: (String) -> Unit
) {
    val list by viewModel.requestedAnimeList.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(id) {
        if (list.none { it.id == id }) viewModel.fetchRequestedAnimeList()
    }

    val anime = list.find { it.id == id }

    if (anime == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Semua video dengan judul/mal_id yang sama dianggap 1 anime dengan banyak episode
    val episodes = remember(list, anime.groupKey()) {
        list.filter { it.status == "approved" && it.groupKey() == anime.groupKey() }.sortedByEpisode()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(anime.poster_url).crossfade(300).build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 0.3f
                    )
                )
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(12.dp).size(40.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(anime.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                anime.rating?.let {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                anime.anime_status?.let {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onWatch((episodes.firstOrNull() ?: anime).id) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mulai Tonton", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (!anime.synopsis.isNullOrBlank()) {
                Text("Sinopsis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(6.dp))
                Text(anime.synopsis, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (!anime.genres.isNullOrBlank()) {
                Text("Genre", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    anime.genres.split(",").filter { it.isNotBlank() }.forEach { g ->
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(g.trim(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (episodes.size > 1) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Daftar Episode", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                    Text("${episodes.size} Episode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(10.dp))
                episodes.chunked(5).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { ep ->
                            EpisodeChip(
                                label = episodeLabel(ep),
                                selected = ep.id == anime.id,
                                modifier = Modifier.weight(1f),
                                onClick = { onWatch(ep.id) }
                            )
                        }
                        repeat(5 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            anime.studio?.let {
                Text("Studio: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 3. WATCH — custom player controls (bukan default ExoPlayer UI) biar matching
//    tema app: play/pause bulat warna primary, seekbar custom, fullscreen landscape,
//    double-tap ±10s. Strip episode di bawah player buat ganti episode tanpa balik.
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun RequestedAnimeWatchScreen(
    id: String,
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val list by viewModel.requestedAnimeList.collectAsState()

    LaunchedEffect(id) {
        if (list.none { it.id == id }) viewModel.fetchRequestedAnimeList()
    }

    // activeId dipisah dari `id` (argumen nav) supaya tap episode lain gak perlu re-navigate
    var activeId by remember(id) { mutableStateOf(id) }
    val anime = list.find { it.id == activeId } ?: list.find { it.id == id }

    if (anime == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val episodes = remember(list, anime.groupKey()) {
        list.filter { it.status == "approved" && it.groupKey() == anime.groupKey() }.sortedByEpisode()
    }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val exoPlayer = remember(anime.video_url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(anime.video_url))
            prepare()
            playWhenReady = true
        }
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekTarget by remember { mutableStateOf<Float?>(null) }
    var doubleTapSide by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Poll posisi/durasi player tiap 500ms — ExoPlayer gak punya Flow bawaan buat ini
    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0)
            durationMs = exoPlayer.duration.coerceAtLeast(0)
            isPlaying = exoPlayer.isPlaying
            isBuffering = exoPlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            kotlinx.coroutines.delay(500)
        }
    }

    // Auto-hide kontrol setelah 3 detik kalau lagi main
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }
    BackHandler(enabled = !isFullscreen) { onBack() }

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

    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            exoPlayer.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = if (isFullscreen) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center).size(36.dp)
                )
            }

            // Efek kilat pas double-tap seek
            if (doubleTapSide != null) {
                LaunchedEffect(doubleTapSide) {
                    kotlinx.coroutines.delay(500)
                    doubleTapSide = null
                }
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.4f)
                        .align(if (doubleTapSide == "left") Alignment.CenterStart else Alignment.CenterEnd)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (doubleTapSide == "left") "« 10s" else "10s »", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Area tap: single tap toggle kontrol, double tap seek ±10s
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val side = if (offset.x < size.width / 2f) "left" else "right"
                            val newPos = if (side == "left") exoPlayer.currentPosition - 10_000 else exoPlayer.currentPosition + 10_000
                            exoPlayer.seekTo(newPos.coerceIn(0, exoPlayer.duration.coerceAtLeast(0)))
                            doubleTapSide = side
                            showControls = false
                        }
                    )
                }
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.35f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                            .statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (isFullscreen) isFullscreen = false else onBack() },
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        if (!isFullscreen) {
                            Text(
                                anime.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                            )
                        } else {
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(anime.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(episodeLabel(anime), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    // Center play/pause + skip
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Mundur 10 detik", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                                showControls = true
                            },
                            modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(exoPlayer.duration.coerceAtLeast(0))) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Maju 10 detik", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }

                    // Bottom bar: progress + waktu + fullscreen
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val sliderPos = seekTarget ?: if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                        Slider(
                            value = sliderPos.coerceIn(0f, 1f),
                            onValueChange = { seekTarget = it },
                            onValueChangeFinished = {
                                val target = seekTarget
                                if (target != null && durationMs > 0) {
                                    exoPlayer.seekTo((target * durationMs).toLong())
                                }
                                seekTarget = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(28.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${formatTime((sliderPos * durationMs).toLong())} / ${formatTime(durationMs)}",
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { isFullscreen = !isFullscreen }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Layar penuh", tint = Color.White, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isFullscreen) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(anime.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(episodeLabel(anime), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            if (episodes.size > 1) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Semua Episode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(episodes, key = { it.id }) { ep ->
                            EpisodeChip(
                                label = episodeLabel(ep),
                                selected = ep.id == anime.id,
                                modifier = Modifier.width(64.dp),
                                onClick = { activeId = ep.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 4. ADMIN SECTION — dipanggil dari tab baru di AdminPanelScreen (Screens.kt)
//    Alur: cari judul di Jikan -> pilih hasil -> pilih file video -> upload.
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun AdminRequestAnimeSection(viewModel: AnikuViewModel) {
    val context = LocalContext.current
    val searchResults by viewModel.jikanSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingJikan.collectAsState()
    val isUploading by viewModel.isUploadingRequestedAnime.collectAsState()
    val uploadProgress by viewModel.uploadRequestedAnimeProgress.collectAsState()
    val requestedList by viewModel.requestedAnimeList.collectAsState()
    val uploadError by viewModel.requestedAnimeError.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedAnime by remember { mutableStateOf<JikanAnimeData?>(null) }
    var episode by remember { mutableStateOf("") }
    var selectedVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.fetchRequestedAnimeList() }

    LaunchedEffect(uploadError) {
        uploadError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) selectedVideoUri = uri }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Tambah Anime Request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (selectedAnime?.title != it) selectedAnime = null
                viewModel.searchJikanAnime(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ketik judul anime, mis. Oshi no Ko", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true
        )

        if (isSearching) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (searchResults.isNotEmpty() && selectedAnime == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                searchResults.forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                selectedAnime = result
                                query = result.title
                                viewModel.clearJikanSearch()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(result.images?.jpg?.image_url).crossfade(true).build(),
                            contentDescription = result.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(result.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(result.status ?: "-", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        selectedAnime?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dipilih: ${it.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = episode,
            onValueChange = { episode = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Episode (opsional), mis. Episode 1", fontSize = 13.sp) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { videoPickerLauncher.launch("video/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (selectedVideoUri == null) "Pilih File Video" else "Video dipilih ✓")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val uri = selectedVideoUri
                val anime = selectedAnime
                if (uri == null || anime == null) {
                    Toast.makeText(context, "Pilih anime dari hasil pencarian & file video dulu", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.uploadRequestedAnimeVideo(uri, anime, episode.ifBlank { null }) { uploading ->
                    if (!uploading) {
                        Toast.makeText(context, "Upload selesai!", Toast.LENGTH_SHORT).show()
                        selectedAnime = null
                        query = ""
                        episode = ""
                        selectedVideoUri = null
                    }
                }
            },
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isUploading) {
                Text("Mengunggah... $uploadProgress%")
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Anime Request")
            }
        }

        if (isUploading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        Spacer(modifier = Modifier.height(12.dp))

        Text("Daftar Request (${requestedList.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(10.dp))

        // Kelompokkan per anime (mal_id/title) biar 1 anime dengan banyak episode gak jadi
        // banyak baris identik — baris grup bisa di-expand buat kelola tiap episode satuan.
        val groupedRequests = remember(requestedList) {
            requestedList.groupBy { it.groupKey() }
                .toList()
                .map { (key, group) -> key to group.sortedByEpisode() }
                .sortedByDescending { (_, group) -> group.maxOf { it.created_at ?: "" } }
        }
        var expandedGroups by remember { mutableStateOf(setOf<String>()) }

        groupedRequests.forEach { (groupKey, items) ->
            val cover = items.first()
            val isMulti = items.size > 1
            val isExpanded = groupKey in expandedGroups

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = isMulti) {
                            expandedGroups = if (isExpanded) expandedGroups - groupKey else expandedGroups + groupKey
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(cover.poster_url).crossfade(true).build(),
                        contentDescription = cover.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cover.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isMulti) {
                            Text("${items.size} Episode", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(
                                cover.status ?: "pending",
                                fontSize = 11.sp,
                                color = when (cover.status) {
                                    "approved" -> Color(0xFF4CAF50)
                                    "rejected" -> Color(0xFFE53935)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    if (isMulti) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Tutup" else "Buka",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        if (cover.status != "approved") {
                            IconButton(onClick = { viewModel.setRequestedAnimeStatus(cover.id, "approved") }) {
                                Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { viewModel.deleteRequestedAnime(cover.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (isMulti && isExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 54.dp, bottom = 6.dp)
                    ) {
                        items.forEach { ep ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(episodeLabel(ep), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                    Text(
                                        ep.status ?: "pending",
                                        fontSize = 10.sp,
                                        color = when (ep.status) {
                                            "approved" -> Color(0xFF4CAF50)
                                            "rejected" -> Color(0xFFE53935)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (ep.status != "approved") {
                                    IconButton(onClick = { viewModel.setRequestedAnimeStatus(ep.id, "approved") }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteRequestedAnime(ep.id) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
