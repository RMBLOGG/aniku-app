package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
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
import com.example.util.orDefault
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
    onEditOwnProfile: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val profile by viewModel.viewedProfile.collectAsState()
    val donationTotal by viewModel.viewedProfileDonationTotal.collectAsState()
    val chatCount by viewModel.viewedProfileChatCount.collectAsState()
    val isLoading by viewModel.isViewedProfileLoading.collectAsState()
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

        // Warna & gaya badge role — tiap role beda warna, admin/moderator dapet gaya "premium" (gradient + border)
        val roleBadge = when {
            p.isAdmin() -> RoleBadgeStyle("ADMINISTRATOR", goldAccent, premium = true)
            p.role == "moderator" -> RoleBadgeStyle("MODERATOR", Color(0xFFB388FF), premium = true)
            p.role == "beta" -> RoleBadgeStyle("BETA", Color(0xFF22D3EE), premium = true)
            else -> RoleBadgeStyle("USER", MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), premium = false)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Banner (read-only) dengan fade gradient ──
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
            }

            // ── Identitas: avatar ring overlap banner, username, UID, role pill — semua center ──
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .offset(y = (-58).dp)
                ) {
                    // Ring avatar user Beta pakai warna cyan/biru khusus (beda dari
                    // accent/gold user biasa) - identitas visual eksklusif kosmetik.
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

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        p.username.orDefault("Pengguna"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    p.user_number?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "#$it",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (roleBadge.premium) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(roleBadge.color.copy(alpha = 0.16f), goldAccent.copy(alpha = 0.10f))))
                            .border(1.dp, roleBadge.color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = goldAccent, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(roleBadge.label, color = roleBadge.color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(roleBadge.color.copy(alpha = 0.12f))
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(roleBadge.label, color = roleBadge.color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    }
                }
            }

            // ── Konten ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Level card — progress bar gradient teranimasi
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

                // Section: Clan (cuma tampil kalau user ini tergabung di clan)
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

                    Spacer(modifier = Modifier.height(4.dp))
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Section: Statistik
                Text(
                    "STATISTIK",
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

                // Section: Info Akun
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

                if (isOwnProfile) {
                    OutlinedButton(
                        onClick = onEditOwnProfile,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Edit Profil Saya", fontWeight = FontWeight.Bold)
                    }
                } else if (canModerate) {
                    val isBanned = p.is_banned == true
                    Button(
                        onClick = { viewModel.toggleUserBanStatus(p) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        if (isBanned) listOf(Color(0xFF1B7A3D), Color(0xFF145C2D))
                                        else listOf(Color(0xFFB71C1C), Color(0xFF7F1414))
                                    ),
                                    RoundedCornerShape(14.dp)
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
                                    if (isBanned) "Aktifkan Kembali User" else "Banned User",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Tombol "Beri Diamond" - eksklusif buat role beta/moderator/admin,
                // cuma nongol pas liat profil ORANG LAIN (bukan profil sendiri).
                // Validasi asli (limit harian, saldo) tetap dicek ulang di server.
                if (!isOwnProfile && (session.isBeta || session.isModerator || session.isAdmin)) {
                    var showGiveDialog by remember { mutableStateOf(false) }
                    var giveAmountText by remember { mutableStateOf("") }
                    var giveResultMsg by remember { mutableStateOf<String?>(null) }
                    var giveInProgress by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showGiveDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF4FD8E8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Beri Diamond", fontWeight = FontWeight.Bold)
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
            }
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
