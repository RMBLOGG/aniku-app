package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.network.AnikuViewModel
import com.example.network.EpisodeComment
import com.example.network.UserBookmarkDto
import com.example.network.UserWatchHistoryDto
import com.example.util.orDefault
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private data class RoleBadgeStyle(
    val label: String,
    val color: Color,
    val premium: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: AnikuViewModel,
    userId: String,
    onBack: () -> Unit,
    onEditOwnProfile: () -> Unit,
    onNavigateToAnime: (String) -> Unit = {}
) {
    val session by viewModel.session.collectAsState()
    val profile by viewModel.viewedProfile.collectAsState()
    val donationTotal by viewModel.viewedProfileDonationTotal.collectAsState()
    val chatCount by viewModel.viewedProfileChatCount.collectAsState()
    val isLoading by viewModel.isViewedProfileLoading.collectAsState()
    val comments by viewModel.viewedProfileComments.collectAsState()
    val bookmarks by viewModel.viewedProfileBookmarks.collectAsState()
    val watchHistory by viewModel.viewedProfileWatchHistory.collectAsState()
    val isActivityLoading by viewModel.isViewedProfileActivityLoading.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val goldAccent = Color(0xFFFFC24B)
    val context = LocalContext.current

    val isOwnProfile = session.userId == userId
    val canModerate = session.canModerate()

    val banStatusMessage by viewModel.banStatusMessage.collectAsState()
    var hasMounted by remember { mutableStateOf(false) }
    var showClanMembers by remember { mutableStateOf(false) }
    val clanMembersForDialog by viewModel.selectedClanMembers.collectAsState()
    val clanForDialog by viewModel.viewedProfileClan.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadPublicUserProfile(userId)
        viewModel.loadPublicUserActivity(userId)
        hasMounted = true
    }

    LaunchedEffect(clanForDialog) {
        clanForDialog?.let { viewModel.loadClanMembers(it.id) }
    }

    LaunchedEffect(banStatusMessage) {
        if (hasMounted && banStatusMessage != null) {
            viewModel.loadPublicUserProfile(userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil Pengguna") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading || profile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = accentColor)
                } else {
                    Text("Pengguna tidak ditemukan", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
            return@Scaffold
        }

        val p = profile!!
        val seasonLevel = p.season_level ?: 1
        val seasonXp = p.season_xp ?: 0
        val currentLevelBaseXp = 20 * (seasonLevel - 1) * (seasonLevel - 1)
        val nextLevelXp = 20 * seasonLevel * seasonLevel
        val xpIntoLevel = (seasonXp - currentLevelBaseXp).coerceAtLeast(0)
        val xpNeededForLevel = (nextLevelXp - currentLevelBaseXp).coerceAtLeast(1)
        val targetProgress = (xpIntoLevel.toFloat() / xpNeededForLevel.toFloat()).coerceIn(0f, 1f)

        var progressAnimStarted by remember(userId) { mutableStateOf(false) }
        val animatedProgress by animateFloatAsState(
            targetValue = if (progressAnimStarted) targetProgress else 0f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            label = "public_level_progress_anim"
        )
        LaunchedEffect(targetProgress) {
            progressAnimStarted = true
        }

        val joinedDate = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(p.created_at?.take(19) ?: "")
            SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(parsed ?: Any())
        } catch (e: Exception) {
            "-"
        }

        // Berapa bulan sejak gabung - dipakai di stat row ala referensi ("bulan bergabung")
        val monthsJoined = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(p.created_at?.take(19) ?: "")
            val diffMs = System.currentTimeMillis() - (parsed?.time ?: System.currentTimeMillis())
            (diffMs / (1000L * 60 * 60 * 24 * 30)).toInt().coerceAtLeast(0)
        } catch (e: Exception) { 0 }

        // Warna & gaya badge role - tiap role beda warna, admin/moderator dapet gaya "premium" (gradient + border)
        val roleBadge = when {
            p.isAdmin() -> RoleBadgeStyle("ADMINISTRATOR", goldAccent, premium = true)
            p.role == "moderator" -> RoleBadgeStyle("MODERATOR", Color(0xFFB388FF), premium = true)
            p.isBeta() -> RoleBadgeStyle("BETA", Color(0xFF22D3EE), premium = true)
            else -> RoleBadgeStyle("USER", MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), premium = false)
        }
        val isVerifiedRole = p.isAdmin() || p.role == "moderator" || p.isBeta()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Banner (read-only) dengan fade gradient + pill role mengambang ala referensi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.6f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!p.banner_url.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(p.banner_url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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

                // Pill role mengambang di banner (pengganti "title" kayak di referensi,
                // tapi isinya data role asli: ADMINISTRATOR/MODERATOR/BETA/USER)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, roleBadge.color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (roleBadge.premium) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(roleBadge.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                }
            }

            // Identitas: avatar ring overlap banner, username, UID - semua center
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .offset(y = (-58).dp)
                ) {
                    val ringColors = if (p.isBeta()) {
                        listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF22D3EE))
                    } else {
                        listOf(accentColor, goldAccent, accentColor)
                    }
                    if (!p.avatar_url.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(ringColors))
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(3.dp)
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(p.avatar_url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(ringColors))
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                p.username?.take(1)?.uppercase() ?: "?",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.username.orDefault("Pengguna"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (isVerifiedRole) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = "Terverifikasi",
                            tint = roleBadge.color,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val viewedClanForPill by viewModel.viewedProfileClan.collectAsState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    p.user_number?.let {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            GlossyGradientText(text = "#$it", colors = idGradientColors, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accentColor.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        GlossyGradientText(text = "Lv.$seasonLevel", colors = levelGradientColors, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    viewedClanForPill?.let { clan ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF7B2FBF).copy(alpha = 0.16f))
                                .clickable {
                                    viewModel.loadClanMembers(clan.id)
                                    showClanMembers = true
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFBA68C8), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(clan.tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA68C8))
                        }
                    }
                }
            }

            // Konten
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Row(modifier = Modifier.fillMaxWidth()) {
                    ProfileStatCell(value = "$monthsJoined", label = "bulan\nbergabung", modifier = Modifier.weight(1f))
                    ProfileStatCell(value = "${comments.size}", label = "jumlah\nkomentar", modifier = Modifier.weight(1f))
                    ProfileStatCell(value = "${bookmarks.size}", label = "jumlah\nfavorit", modifier = Modifier.weight(1f))
                    ProfileStatCell(value = "${watchHistory.size}", label = "jumlah\nriwayat", modifier = Modifier.weight(1f))
                }

                if (isOwnProfile) {
                    Button(
                        onClick = onEditOwnProfile,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Edit Profil Saya", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val showBanButton = canModerate
                    val showDiamondButton = session.isBeta || session.isModerator || session.isAdmin

                    var showGiveDialog by remember { mutableStateOf(false) }
                    var giveAmountText by remember { mutableStateOf("") }
                    var giveResultMsg by remember { mutableStateOf<String?>(null) }
                    var giveInProgress by remember { mutableStateOf(false) }

                    if (showBanButton || showDiamondButton) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (showBanButton) {
                                val isBanned = p.is_banned == true
                                Button(
                                    onClick = { viewModel.toggleUserBanStatus(p) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(if (showDiamondButton) 1.4f else 1f)
                                        .height(52.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    if (isBanned) listOf(Color(0xFF1B7A3D), Color(0xFF145C2D))
                                                    else listOf(Color(0xFFB71C1C), Color(0xFF7F1414))
                                                ),
                                                RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (isBanned) Icons.Default.CheckCircle else Icons.Default.Block,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (isBanned) "Aktifkan Kembali" else "Banned User",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            if (showDiamondButton) {
                                OutlinedButton(
                                    onClick = { showGiveDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF4FD8E8).copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFF4FD8E8).copy(alpha = 0.08f),
                                        contentColor = Color(0xFF4FD8E8)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF4FD8E8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Diamond", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (showGiveDialog) {
                        AlertDialog(
                            onDismissRequest = { if (!giveInProgress) showGiveDialog = false },
                            title = { Text("Beri Diamond ke ${p.username}") },
                            text = {
                                Column {
                                    Text(
                                        "Maksimal 200 DM per hari (gabungan semua penerima).",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = giveAmountText,
                                        onValueChange = { giveAmountText = it.filter { c -> c.isDigit() } },
                                        label = { Text("Jumlah DM") },
                                        singleLine = true,
                                        enabled = !giveInProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (giveResultMsg != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(giveResultMsg ?: "", color = Color(0xFFE57373), fontSize = 12.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = !giveInProgress && giveAmountText.toIntOrNull()?.let { it > 0 } == true,
                                    onClick = {
                                        val amount = giveAmountText.toIntOrNull() ?: return@TextButton
                                        val targetUsername = p.username ?: return@TextButton
                                        giveInProgress = true
                                        giveResultMsg = null
                                        viewModel.giveDiamond(targetUsername, amount) { success, error ->
                                            giveInProgress = false
                                            if (success) {
                                                showGiveDialog = false
                                                giveAmountText = ""
                                            } else {
                                                giveResultMsg = error
                                            }
                                        }
                                    }
                                ) { Text(if (giveInProgress) "Mengirim..." else "Kirim") }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !giveInProgress,
                                    onClick = { showGiveDialog = false }
                                ) { Text("Batal") }
                            }
                        )
                    }
                }

                // Tab swipeable ala referensi: Komentar / Favorit / Histori
                // (dipindah ke sini, tepat di bawah tombol aksi, sesuai posisi di referensi)
                ProfileActivityTabs(
                    comments = comments,
                    bookmarks = bookmarks,
                    watchHistory = watchHistory,
                    isLoading = isActivityLoading,
                    accentColor = accentColor,
                    profileUsername = p.username.orDefault("Pengguna"),
                    profileAvatarUrl = p.avatar_url,
                    profileIsAdmin = p.isAdmin(),
                    profileIsModerator = p.role == "moderator",
                    onNavigateToAnime = onNavigateToAnime
                )

                // Level card - progress bar gradient teranimasi
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

                val viewedClan by viewModel.viewedProfileClan.collectAsState()
                viewedClan?.let { clan ->
                    val memberRank = remember(clanMembersForDialog, userId) {
                        val sorted = clanMembersForDialog.sortedByDescending { it.contributed_xp ?: 0 }
                        val idx = sorted.indexOfFirst { it.user_id == userId }
                        if (idx >= 0) idx + 1 else null
                    }
                    val rankColor = when (memberRank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC9D6E3)
                        3 -> Color(0xFFCD7F32)
                        else -> Color(0xFF2FA8BF)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF241530), Color(0xFF102830))))
                            .border(1.dp, Brush.linearGradient(listOf(rankColor.copy(alpha = 0.5f), Color(0xFF7B2FBF).copy(alpha = 0.25f))), RoundedCornerShape(18.dp))
                            .clickable {
                                viewModel.loadClanMembers(clan.id)
                                showClanMembers = true
                            }
                    ) {
                        val sparkleComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sparkle_shine))
                        val sparkleProgress by animateLottieCompositionAsState(
                            sparkleComposition, iterations = LottieConstants.IterateForever
                        )
                        LottieAnimation(
                            composition = sparkleComposition,
                            progress = { sparkleProgress },
                            modifier = Modifier
                                .size(90.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 20.dp, y = (-20).dp)
                        )

                        if (memberRank == 1) {
                            val confettiComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti_burst))
                            val confettiProgress by animateLottieCompositionAsState(
                                confettiComposition, iterations = 1
                            )
                            LottieAnimation(
                                composition = confettiComposition,
                                progress = { confettiProgress },
                                modifier = Modifier
                                    .matchParentSize()
                            )
                        }

                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(0xFF7B2FBF), Color(0xFF2FA8BF))))
                                        .border(1.5.dp, rankColor.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!clan.icon_url.isNullOrBlank()) {
                                        AsyncImage(model = clan.icon_url, contentDescription = "Icon Clan", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                    } else {
                                        Text(clan.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ANGGOTA CLAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Color.White.copy(alpha = 0.45f))
                                    Text("${clan.name} [${clan.tag}]", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(rankColor.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = rankColor, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        memberRank?.let { "Peringkat #$it Kontributor" } ?: "Kontributor Clan",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lv.${clan.level}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2FA8BF))
                                }
                            }
                        }
                    }
                }

                Text(
                    "STATISTIK LAINNYA",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(goldAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = goldAccent, modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("TOTAL DUKUNGAN", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                if (donationTotal > 0) "Rp${"%,d".format(donationTotal).replace(",", ".")}" else "Belum ada",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = if (donationTotal > 0) goldAccent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Forum, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("PESAN CHAT", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("$chatCount pesan", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                Text(
                    "INFO AKUN",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(11.dp))
                            Text(
                                "Bergabung Sejak",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(joinedDate, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showClanMembers) {
        val clan = clanForDialog
        Dialog(onDismissRequest = { showClanMembers = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2A1B3D), Color(0xFF16414D))))
                    .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            clan?.let { "${it.name} [${it.tag}]" } ?: "Member Clan",
                            fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${clanMembersForDialog.size} member", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))
                    if (clanMembersForDialog.isEmpty()) {
                        Text("Memuat member...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    } else {
                        clanMembersForDialog.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!member.avatar_url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = member.avatar_url, contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(30.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFBA68C8).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(member.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(member.username.orDefault("?"), fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                                if (member.role == "leader") {
                                    Icon(Icons.Default.Star, contentDescription = "Leader", tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showClanMembers = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Tutup", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

/**
 * Tab swipeable Komentar / Favorit / Histori ala referensi.
 * Tingginya FIXED (gak ikut memanjang ke bawah) - tiap tab pakai LazyColumn/LazyVerticalGrid
 * sendiri buat scroll vertikal internal kalau isinya lebih panjang dari area yang disediakan.
 */
@Composable
private fun ProfileActivityTabs(
    comments: List<EpisodeComment>,
    bookmarks: List<UserBookmarkDto>,
    watchHistory: List<UserWatchHistoryDto>,
    isLoading: Boolean,
    accentColor: Color,
    profileUsername: String,
    profileAvatarUrl: String?,
    profileIsAdmin: Boolean,
    profileIsModerator: Boolean,
    onNavigateToAnime: (String) -> Unit
) {
    val tabTitles = listOf("Komentar", "Favorit", "Histori")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()
    val tabsAreaHeight = 440.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(2.5.dp)
                            .width(if (isSelected) 28.dp else 0.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) accentColor else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(tabsAreaHeight)
        ) { page ->
            when (page) {
                0 -> CommentsTabContent(
                    comments, isLoading, accentColor,
                    profileUsername, profileAvatarUrl, profileIsAdmin, profileIsModerator,
                    onNavigateToAnime
                )
                1 -> FavoritesTabContent(bookmarks, isLoading, onNavigateToAnime)
                else -> HistoryTabContent(watchHistory, isLoading, accentColor, onNavigateToAnime)
            }
        }
    }
}

@Composable
private fun EmptyTabState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f), modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// Kartu komentar ala feed asli Aniku - borderless + GlossyGradientText, senada sama
// gaya komentar episode (EpisodeCommentRow) & chat, bukan kotak flat abu-abu generik.
@Composable
private fun CommentsTabContent(
    comments: List<EpisodeComment>,
    isLoading: Boolean,
    accentColor: Color,
    profileUsername: String,
    profileAvatarUrl: String?,
    profileIsAdmin: Boolean,
    profileIsModerator: Boolean,
    onNavigateToAnime: (String) -> Unit
) {
    if (isLoading && comments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp))
        }
        return
    }
    if (comments.isEmpty()) {
        EmptyTabState(Icons.Default.ChatBubbleOutline, "Belum ada komentar dari user ini.")
        return
    }
    val nameGradient = when {
        profileIsAdmin -> adminGradientColors
        profileIsModerator -> moderatorGradientColors
        else -> defaultNameGradientColors
    }
    val ringColor = when {
        profileIsAdmin -> Color(0xFFFFC107)
        profileIsModerator -> Color(0xFFB388FF)
        else -> Color.Transparent
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(comments, key = { _, c -> c.id }) { index, comment ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = comment.anime_slug != null) {
                        comment.anime_slug?.let { onNavigateToAnime(it) }
                    }
            ) {
                // Baris identitas: avatar + username gradient + timestamp (mirip EpisodeCommentRow)
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profileAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profileAvatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(profileUsername.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    GlossyGradientText(text = profileUsername, colors = nameGradient, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "\u00b7 ${relativeTimeShort(comment.created_at)} lalu",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Baris anime: poster + judul, mirip kartu "sedang ditonton" di referensi
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (!comment.anime_poster.isNullOrBlank()) {
                            AsyncImage(
                                model = comment.anime_poster,
                                contentDescription = comment.anime_title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Center).size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            comment.anime_title ?: "Anime",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            comment.message,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (index != comments.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}

@Composable
private fun FavoritesTabContent(
    bookmarks: List<UserBookmarkDto>,
    isLoading: Boolean,
    onNavigateToAnime: (String) -> Unit
) {
    if (isLoading && bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        return
    }
    if (bookmarks.isEmpty()) {
        EmptyTabState(Icons.Default.FavoriteBorder, "Belum ada anime favorit.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bookmarks, key = { it.anime_slug }) { bm ->
            Column(
                modifier = Modifier.clickable { onNavigateToAnime(bm.anime_slug) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = bm.poster,
                        contentDescription = bm.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    bm.title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(4.dp)) }
    }
}

@Composable
private fun HistoryTabContent(
    watchHistory: List<UserWatchHistoryDto>,
    isLoading: Boolean,
    accentColor: Color,
    onNavigateToAnime: (String) -> Unit
) {
    if (isLoading && watchHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp))
        }
        return
    }
    if (watchHistory.isEmpty()) {
        EmptyTabState(Icons.Default.History, "Belum ada riwayat tontonan.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(watchHistory, key = { "${it.anime_slug}_${it.episode_slug}" }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAnime(item.anime_slug) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = item.anime_poster,
                        contentDescription = item.anime_title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.anime_title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                item.episode_title ?: item.episode_slug,
                                color = accentColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${relativeTimeShort(item.watched_at ?: "")} lalu",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}
