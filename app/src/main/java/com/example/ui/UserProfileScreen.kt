package com.example.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.network.UserRanksDto
import com.example.network.PremiumClaimDto
import com.example.network.PremiumPackageDto
import com.example.network.SakurupiahInvoiceResponse
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
    onNavigateToAnime: (String) -> Unit = {},
    onOpenPrivateChat: (String) -> Unit = {}
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
    val friendshipStatus by viewModel.friendships.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val goldAccent = Color(0xFFFFC24B)
    val context = LocalContext.current

    val isOwnProfile = session.userId == userId

    LaunchedEffect(userId) {
        viewModel.loadFriendships()
    }
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

    val showcaseCharacters by viewModel.showcaseCharacters.collectAsState()
    LaunchedEffect(profile?.showcase_character_ids) {
        viewModel.loadShowcaseCharacters(profile?.showcase_character_ids ?: emptyList())
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
            p.isPremiumActive() -> RoleBadgeStyle("PREMIUM", Color(0xFFFFA000), premium = true)
            else -> RoleBadgeStyle("USER", MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), premium = false)
        }
        val isVerifiedRole = p.isAdmin() || p.role == "moderator" || p.isBeta() || p.isPremiumActive()

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
                    var userRanks by remember(p.id) { mutableStateOf<UserRanksDto?>(null) }
                    LaunchedEffect(p.id) {
                        viewModel.loadUserRanks(p.id) { ranks -> userRanks = ranks }
                    }
                    // Badge "Top Support" ini sengaja dihitung dari donasi Trakteer
                    // (donations, sama persis sumbernya kayak widget "TOP SUPPORTER"
                    // di Home), BUKAN dari support_points fitur Gift Premium.
                    val donationsForRank by viewModel.donations.collectAsState()
                    val directoryForRank by viewModel.userDirectory.collectAsState()
                    LaunchedEffect(Unit) {
                        if (donationsForRank.isEmpty()) viewModel.loadDonations()
                        if (directoryForRank.isEmpty()) viewModel.loadUserDirectory()
                    }
                    val supportRank = remember(donationsForRank, directoryForRank, p.username) {
                        viewModel.getSupporterRank(p.username)
                    }
                    supportRank?.let { rank ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFFFD54F).copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("🏆", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Top Support #$rank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                        }
                    }
                    userRanks?.xp_rank?.let { rank ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF64B5F6).copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("⭐", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Top XP #$rank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                        }
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
                    Spacer(modifier = Modifier.height(10.dp))
                    var showBuyPremiumSheet by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showBuyPremiumSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFA000).copy(alpha = 0.08f),
                            contentColor = Color(0xFFFFA000)
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Beli Premium", fontWeight = FontWeight.Bold)
                    }
                    if (showBuyPremiumSheet) {
                        GiftPremiumSheet(
                            targetUserId = p.id,
                            targetUsername = p.username ?: "Kamu",
                            viewModel = viewModel,
                            selfMode = true,
                            onDismiss = { showBuyPremiumSheet = false }
                        )
                    }
                } else {
                    val showBanButton = canModerate
                    val showDiamondButton = session.isBeta || session.isModerator || session.isAdmin || session.isPremiumActive()
                    var showGiftPremiumSheet by remember { mutableStateOf(false) }

                    var showGiveDialog by remember { mutableStateOf(false) }
                    var giveAmountText by remember { mutableStateOf("") }
                    var giveResultMsg by remember { mutableStateOf<String?>(null) }
                    var giveInProgress by remember { mutableStateOf(false) }

                    val showGiftPremiumButton = session.userId != null
                    if (showBanButton || showDiamondButton || showGiftPremiumButton) {
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

                            if (showGiftPremiumButton) {
                                OutlinedButton(
                                    onClick = { showGiftPremiumSheet = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFFFA000).copy(alpha = 0.08f),
                                        contentColor = Color(0xFFFFA000)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gift Premium", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (showGiftPremiumSheet) {
                        GiftPremiumSheet(
                            targetUserId = p.id,
                            targetUsername = p.username ?: "Pengguna",
                            viewModel = viewModel,
                            onDismiss = { showGiftPremiumSheet = false }
                        )
                    }
                    if (showGiveDialog) {
                        AlertDialog(
                            onDismissRequest = { if (!giveInProgress) showGiveDialog = false },
                            title = { Text("Beri Diamond ke ${p.username}") },
                            text = {
                                Column {
                                    Text(
                                        "Maksimal 1000 DM per hari (gabungan semua penerima).",
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

                    // ── Add Teman / Kirim Pesan ──
                    Spacer(modifier = Modifier.height(10.dp))
                    val myFriendStatus = remember(friendshipStatus, userId) { viewModel.friendshipStatusWith(userId) }
                    when (myFriendStatus) {
                        "accepted" -> {
                            Button(
                                onClick = { onOpenPrivateChat(userId) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kirim Pesan", fontWeight = FontWeight.Bold)
                            }
                        }
                        "pending_sent" -> {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Text("Menunggu Konfirmasi", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        "pending_received" -> {
                            Button(
                                onClick = {
                                    val fs = viewModel.friendshipWith(userId)
                                    fs?.id?.let { viewModel.respondToFriendRequest(it, accept = true) }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Terima Permintaan Teman", fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { viewModel.sendFriendRequest(userId) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Teman", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (showcaseCharacters.isNotEmpty()) {
                    ShowcaseSection(characters = showcaseCharacters)
                }

                // Tab swipeable ala referensi: Komentar / Favorit / Histori
                // (dipindah ke sini, tepat di bawah tombol aksi, sesuai posisi di referensi)
                ProfileActivityTabs(
                    comments = comments,
                    bookmarks = bookmarks,
                    watchHistory = watchHistory,
                    isLoading = isActivityLoading,
                    accentColor = accentColor,
                    goldAccent = goldAccent,
                    monthsJoined = monthsJoined,
                    joinedDate = joinedDate,
                    seasonLevel = seasonLevel,
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
    goldAccent: Color,
    monthsJoined: Int,
    joinedDate: String,
    seasonLevel: Int,
    onNavigateToAnime: (String) -> Unit
) {
    val tabTitles = listOf("Statistik", "Favorit", "Histori")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()
    // Statistik jauh lebih pendek dibanding Favorit/Histori (yang perlu ruang scroll
    // buat list panjang) -- kalau dipaksa sama-sama 440dp, tab Statistik jadi ada
    // jarak kosong gede di bawahnya. Jadi tinggi area tab dibikin ngikutin tab aktif.
    val tabsAreaHeight by animateDpAsState(
        targetValue = if (pagerState.currentPage == 0) 300.dp else 440.dp,
        label = "tabsAreaHeight"
    )

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
                0 -> StatsTabContent(
                    commentCount = comments.size,
                    favoriteCount = bookmarks.size,
                    historyCount = watchHistory.size,
                    monthsJoined = monthsJoined,
                    joinedDate = joinedDate,
                    seasonLevel = seasonLevel,
                    accentColor = accentColor,
                    goldAccent = goldAccent
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

// ============================================================================
//  TAB STATISTIK — ringkasan aktivitas user, ganti feed komentar mentah ala
//  dashboard "gamer": tile besar per kategori + info bergabung.
// ============================================================================
@Composable
private fun StatsTabContent(
    commentCount: Int,
    favoriteCount: Int,
    historyCount: Int,
    monthsJoined: Int,
    joinedDate: String,
    seasonLevel: Int,
    accentColor: Color,
    goldAccent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                icon = Icons.Default.ChatBubbleOutline,
                value = "$commentCount",
                label = "Komentar",
                color = accentColor,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                icon = Icons.Default.Favorite,
                value = "$favoriteCount",
                label = "Favorit",
                color = Color(0xFFFF5C8A),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                icon = Icons.Default.History,
                value = "$historyCount",
                label = "Riwayat",
                color = Color(0xFF4FC3F7),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Kartu ringkasan keanggotaan - level saat ini + tanggal & lama bergabung
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    )
                )
                .border(1.dp, goldAccent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = goldAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Level $seasonLevel musim ini",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Bergabung sejak $joinedDate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (monthsJoined <= 0) "Baru bergabung bulan ini" else "Sudah $monthsJoined bulan jadi bagian dari Aniku",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.05f)))
            )
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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

// ============================================================================
//  KARTU DIPAJANG — karakter gacha pilihan user, tampil di profil publik.
//  Desain ala-gamer: pakai efek glossy border, holo sweep, dan glow yang sama
//  kayak di GachaScreen biar konsisten dan gak keliatan flat.
// ============================================================================
@Composable
private fun ShowcaseSection(characters: List<com.example.network.CharacterInfoDto>) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFFFFC24B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "KARTU DIPAJANG",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            characters.forEachIndexed { index, char ->
                ShowcaseCard(char, index)
            }
        }
    }
}

@Composable
private fun ShowcaseCard(char: com.example.network.CharacterInfoDto, index: Int = 0) {
    val color = rarityColor(char.rarity)
    val premium = isPremiumRarity(char.rarity)
    val shine = if (premium) rememberGlossyShine(2400) else 0f

    Box(
        modifier = Modifier
            .width(108.dp)
            .aspectRatio(0.7f)
    ) {
        // Glow radial di belakang kartu, warnanya ngikutin rarity - kesan kartu "hidup"
        if (premium) {
            GlowBehind(color = color, sizeFraction = 0.95f, baseAlpha = 0.45f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (premium) 14.dp else 6.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = color.copy(alpha = 0.7f),
                    spotColor = color.copy(alpha = 0.7f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF17141F), Color(0xFF0B0A12))))
                .then(
                    if (premium) {
                        Modifier.glossyBorder(
                            colors = rarityShimmerColors(char.rarity),
                            shine = shine,
                            cornerRadius = 16.dp,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    }
                )
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(13.dp))
            ) {
                AsyncImage(
                    model = char.image_url,
                    contentDescription = char.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .holoSweep(active = premium, delayMillis = index * 120)
                )

                // Scrim gelap dari bawah biar nama tetap kontras di atas foto
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
                // Kilau tipis di atas biar foto gak kelihatan flat kena badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )

                // Badge rarity asli ala-Gacha: gradient + ikon + glow berdenyut buat rarity tinggi
                RarityBadge(
                    rarity = char.rarity,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )

                // Nama karakter, nempel di bawah di atas scrim
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Text(
                        char.name,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    if (!char.anime_title.isNullOrBlank()) {
                        Text(
                            char.anime_title,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

// Bottom sheet buat kirim Gift Premium ke user lain, dengan 2 mode:
// - "direct": langsung ke target_user_id (dari profil orang lain)
// - "giveaway": "War di Chat Global" - siapa cepat dia dapat
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GiftPremiumSheet(
    targetUserId: String,
    targetUsername: String,
    viewModel: AnikuViewModel,
    selfMode: Boolean = false,
    giveawayOnly: Boolean = false,
    onDismiss: () -> Unit
) {
    val packages by viewModel.premiumPackages.collectAsState()
    var selectedPackageId by remember { mutableStateOf<String?>(null) }
    // "direct" atau "giveaway" - kalau selfMode, mode ini ga ditampilin (selalu "direct").
    // Kalau giveawayOnly (dipanggil dari chat, tanpa target spesifik), dipaksa "giveaway".
    var mode by remember { mutableStateOf(if (giveawayOnly) "giveaway" else "direct") }
    var slotCount by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var resultClaim by remember { mutableStateOf<PremiumClaimDto?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (packages.isEmpty()) viewModel.loadPremiumPackages()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            val claim = resultClaim
            if (claim != null) {
                var invoice by remember(claim.id) { mutableStateOf<SakurupiahInvoiceResponse?>(null) }
                var invoiceError by remember(claim.id) { mutableStateOf<String?>(null) }
                var isCreatingInvoice by remember(claim.id) { mutableStateOf(true) }
                val context = LocalContext.current

                LaunchedEffect(claim.id) {
                    isCreatingInvoice = true
                    invoiceError = null
                    viewModel.createSakurupiahInvoice(claim.id) { result, error ->
                        isCreatingInvoice = false
                        if (result != null) invoice = result else invoiceError = error
                    }
                }

                Text(
                    if (selfMode) "Pembelian Premium Dibuat!" else "Gift Premium Berhasil Dibuat!",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isCreatingInvoice -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    invoiceError != null -> {
                        Text(
                            invoiceError ?: "Gagal membuat invoice pembayaran",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                    invoice != null -> {
                        Text(
                            "Selesaikan pembayaran QRIS buat aktifin Premium-nya. Otomatis aktif setelah pembayaran terverifikasi.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val url = invoice?.checkout_url
                                if (!url.isNullOrBlank()) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (e: Exception) {
                                        Log.e("GiftPremiumSheet", "Failed to open checkout url", e)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bayar Sekarang")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                return@Column
            }

            Text(
                when {
                    selfMode -> "Beli Premium"
                    giveawayOnly -> "Gift Premium - War di Chat"
                    else -> "Gift Premium"
                },
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when {
                    selfMode -> "Aktifkan Premium buat akun kamu sendiri"
                    giveawayOnly -> "Bagikan Premium ke chat global, siapa cepat dia dapat"
                    else -> "Kirim Premium ke $targetUsername"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Pilih Paket", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            packages.forEach { pkg ->
                val isSelected = selectedPackageId == pkg.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedPackageId = pkg.id }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(pkg.label, fontWeight = FontWeight.SemiBold)
                    val priceText = if (mode == "giveaway" && !selfMode) "Rp${pkg.price * slotCount}" else "Rp${pkg.price}"
                    Text(priceText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!selfMode) {
                if (!giveawayOnly) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Mode Pengiriman", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == "direct",
                            onClick = { mode = "direct" },
                            label = { Text("Langsung ke $targetUsername") }
                        )
                        FilterChip(
                            selected = mode == "giveaway",
                            onClick = { mode = "giveaway" },
                            label = { Text("War di Chat Global") }
                        )
                    }
                }

                if (mode == "giveaway") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Jumlah Pemenang", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (slotCount > 1) slotCount-- },
                            enabled = slotCount > 1,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "$slotCount orang",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 64.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = { if (slotCount < 50) slotCount++ },
                            enabled = slotCount < 50,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Giveaway berlaku buat $slotCount orang tercepat yang klaim di chat",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val pkgId = selectedPackageId ?: return@Button
                    isLoading = true
                    errorMsg = null
                    when {
                        selfMode -> {
                            viewModel.createSelfPremiumClaim(pkgId) { claim, error ->
                                isLoading = false
                                if (claim != null) resultClaim = claim else errorMsg = error
                            }
                        }
                        mode == "direct" -> {
                            viewModel.createPremiumClaim(targetUserId, pkgId) { claim, error ->
                                isLoading = false
                                if (claim != null) resultClaim = claim else errorMsg = error
                            }
                        }
                        else -> {
                            viewModel.createGiveawayClaim(pkgId, slotCount) { claim, error ->
                                isLoading = false
                                if (claim != null) resultClaim = claim else errorMsg = error
                            }
                        }
                    }
                },
                enabled = selectedPackageId != null && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (selfMode) "Beli Sekarang" else "Buat Gift Premium")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
