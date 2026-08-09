package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.network.AnikuViewModel
import com.example.util.orDefault
import com.example.network.ClanDto
import com.example.network.ClanMemberDto
import kotlinx.coroutines.delay

private val AnimeGradient = Brush.linearGradient(listOf(Color(0xFF7B2FBF), Color(0xFF2FA8BF)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onTopUpClick: () -> Unit,
    onQuizClick: () -> Unit = {}
) {
    val diamondBalance by viewModel.diamondBalance.collectAsState()
    val topClans by viewModel.topClans.collectAsState()
    val myClan by viewModel.myClanDetail.collectAsState()
    val myMembership by viewModel.myClanMembership.collectAsState()
    val myClanMembers by viewModel.myClanMembers.collectAsState()
    val selectedClanMembers by viewModel.selectedClanMembers.collectAsState()
    val clanActionError by viewModel.clanActionError.collectAsState()
    val isClanLoading by viewModel.isClanLoading.collectAsState()
    val isUploadingIcon by viewModel.isUploadingClanIcon.collectAsState()
    val session by viewModel.session.collectAsState()

    var showCreateForm by remember { mutableStateOf(false) }
    var clanName by remember { mutableStateOf("") }
    var clanTag by remember { mutableStateOf("") }
    var selectedClan by remember { mutableStateOf<ClanDto?>(null) }
    var contributeAmount by remember { mutableStateOf("") }
    var showContributeDialog by remember { mutableStateOf(false) }
    var showContributeBurst by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    val pendingRequests by viewModel.pendingJoinRequests.collectAsState()
    var clanTabIndex by remember { mutableStateOf(0) }

    val isLeader = myMembership?.role == "leader" && myMembership?.user_id == session.userId
    val isCoLeader = myMembership?.role == "co_leader" && myMembership?.user_id == session.userId
    val canManageMembers = isLeader || isCoLeader

    LaunchedEffect(myClan, canManageMembers) {
        if (canManageMembers) myClan?.let { viewModel.loadPendingJoinRequests(it.id) }
    }

    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) myClan?.let { viewModel.uploadClanIcon(it.id, uri) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
        viewModel.loadClans()
        viewModel.loadMyClanMembership()
    }

    LaunchedEffect(myClan) {
        myClan?.let { viewModel.loadMyClanMembers(it.id) }
    }

    LaunchedEffect(showContributeBurst) {
        if (showContributeBurst) {
            delay(1400)
            showContributeBurst = false
        }
    }

    if (selectedClan != null) {
        ViewClanScreen(
            clan = selectedClan!!,
            members = selectedClanMembers,
            alreadyInClan = myClan != null,
            onBack = { selectedClan = null },
            onJoin = { clan ->
                if (clan.is_private == true) viewModel.requestJoinClan(clan.id) else viewModel.joinClan(clan.id)
                selectedClan = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Clan", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { QuizEntryCard(onClick = onQuizClick) }

            clanActionError?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            // 3 tab: Clan Saya, Daftar Clan, Leaderboard
            item {
                ClanTabRow(selectedIndex = clanTabIndex, onSelect = { clanTabIndex = it })
            }

            when (clanTabIndex) {
                0 -> {
                    if (myClan != null) {
                        item {
                            MyClanCard(
                                clan = myClan!!,
                                members = myClanMembers,
                                isLeader = isLeader,
                                isCoLeader = isCoLeader,
                                isUploadingIcon = isUploadingIcon,
                                currentUserId = session.userId,
                                onContribute = { showContributeDialog = true },
                                onManage = { showManageDialog = true },
                                onKick = { userId -> viewModel.kickMember(myClan!!.id, userId) },
                                onPromote = { userId -> viewModel.promoteCoLeader(myClan!!.id, userId) },
                                onDemote = { userId -> viewModel.demoteCoLeader(myClan!!.id, userId) },
                                onTransferLeader = { userId -> viewModel.transferLeaderClan(myClan!!.id, userId) },
                                onLeave = { viewModel.leaveClan() },
                                onIconClick = {
                                    if (isLeader) iconPickerLauncher.launch("image/*")
                                }
                            )
                        }
                    } else {
                        item {
                            ClanHeroInvite(totalClans = topClans.size, totalXp = topClans.sumOf { it.total_xp ?: 0 })
                        }

                        item {
                            CreateClanSection(
                                showCreateForm = showCreateForm,
                                onShowForm = { showCreateForm = true },
                                clanName = clanName,
                                onNameChange = { clanName = it },
                                clanTag = clanTag,
                                onTagChange = { if (it.length <= 6) clanTag = it.uppercase() },
                                isLoading = isClanLoading,
                                onCreate = { viewModel.createClan(clanName, clanTag) }
                            )
                        }
                    }
                }
                1 -> {
                    if (topClans.isEmpty()) {
                        item { EmptyLeaderboardState() }
                    } else {
                        val displayedClans = topClans.sortedBy { it.name.lowercase() }
                        itemsIndexed(displayedClans, key = { _, c -> "daftar_${c.id}" }) { index, clan ->
                            ClanLeaderboardRow(
                                clan = clan,
                                rank = index + 1,
                                index = index,
                                showRank = false,
                                onClick = {
                                    selectedClan = clan
                                    viewModel.loadClanMembers(clan.id)
                                }
                            )
                        }
                    }
                }
                2 -> {
                    if (topClans.isEmpty()) {
                        item { EmptyLeaderboardState() }
                    } else {
                        val displayedClans = topClans.sortedByDescending { it.total_xp ?: 0 }
                        itemsIndexed(displayedClans, key = { _, c -> "lb_${c.id}" }) { index, clan ->
                            ClanLeaderboardRow(
                                clan = clan,
                                rank = index + 1,
                                index = index,
                                showRank = true,
                                onClick = {
                                    selectedClan = clan
                                    viewModel.loadClanMembers(clan.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showContributeBurst) {
            val burstComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti_burst))
            val burstProgress by animateLottieCompositionAsState(burstComposition, iterations = 1)
            LottieAnimation(
                composition = burstComposition,
                progress = { burstProgress },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
            )
        }
        }
    }

    if (showContributeDialog) {
        AnimeDialog(
            onDismissRequest = { showContributeDialog = false },
            title = "Kontribusi ke Clan",
            icon = Icons.Default.VolunteerActivism,
            content = {
                Text("Saldo kamu: $diamondBalance DM", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = contributeAmount,
                    onValueChange = { contributeAmount = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah DM") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2FA8BF), unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFF2FA8BF), unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            buttons = {
                TextButton(onClick = { showContributeDialog = false }) { Text("Batal", color = Color.White.copy(alpha = 0.6f)) }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        contributeAmount.toIntOrNull()?.let { viewModel.contributeToClan(it) }
                        showContributeDialog = false
                        contributeAmount = ""
                        showContributeBurst = true
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA8BF))
                ) { Text("Kontribusi", color = Color(0xFF1B2A2E), fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showManageDialog && myClan != null) {
        ManageClanDialog(
            clan = myClan!!,
            isLeader = isLeader,
            pendingRequests = pendingRequests,
            diamondBalance = diamondBalance,
            onDismiss = { showManageDialog = false },
            onRename = { newName -> viewModel.renameClan(myClan!!.id, newName) },
            onRenameTag = { newTag -> viewModel.renameClanTag(myClan!!.id, newTag) },
            onTogglePrivacy = { isPrivate -> viewModel.setClanPrivacy(myClan!!.id, isPrivate) },
            onApproveRequest = { reqId -> viewModel.approveJoinRequest(reqId, myClan!!.id) },
            onRejectRequest = { reqId -> viewModel.rejectJoinRequest(reqId, myClan!!.id) },
            onDeleteClan = {
                viewModel.deleteClan(myClan!!.id) { showManageDialog = false }
            }
        )
    }

}

// ============================================================================
//  VIEW CLAN — halaman penuh (bukan popup) buat liat detail clan orang lain,
//  gaya kartu-nya sama kayak MyClanCard biar konsisten: header + tag pill +
//  stat box + tombol gabung gede + list member dengan role/XP.
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewClanScreen(
    clan: ClanDto,
    members: List<ClanMemberDto>,
    alreadyInClan: Boolean,
    onBack: () -> Unit,
    onJoin: (ClanDto) -> Unit
) {
    val leaderMember = remember(members) { members.firstOrNull { it.role == "leader" } }
    val sortedMembers = remember(members) { members.sortedByDescending { it.contributed_xp ?: 0 } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("View Clan", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3A1560), Color(0xFF241530))))
                        .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(AnimeGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!clan.icon_url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = clan.icon_url, contentDescription = "Icon Clan",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text("${clan.level ?: 1}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        clan.name,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 19.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ClanTagPill(clan.tag)
                                    if (clan.is_private == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Lock, contentDescription = "Private", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                leaderMember?.let { leader ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!leader.avatar_url.isNullOrBlank()) {
                                            AsyncImage(
                                                model = leader.avatar_url, contentDescription = "Leader",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(18.dp).clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFBA68C8).copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) { Text(leader.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 9.sp) }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(leader.username.orDefault("?"), fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.75f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ClanStatBox(value = "${clan.level ?: 1}", label = "Level", modifier = Modifier.weight(1f))
                            ClanStatBox(value = formatXpAbbrev(clan.total_xp ?: 0), label = "Total XP", modifier = Modifier.weight(1f))
                            ClanStatBox(value = "${members.size}", label = "Member", modifier = Modifier.weight(1f))
                        }

                        if (!alreadyInClan) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onJoin(clan) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC24B)),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text(
                                    if (clan.is_private == true) "Minta Gabung" else "Gabung Clan Ini",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF3A2A00)
                                )
                            }
                            if (clan.is_private == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clan private \u2014 butuh persetujuan leader", fontSize = 11.5.sp, color = Color(0xFFB388FF))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "MEMBER (${members.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }

            items(sortedMembers, key = { it.id }) { member ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!member.avatar_url.isNullOrBlank()) {
                            AsyncImage(
                                model = member.avatar_url, contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFBA68C8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text(member.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                member.username.orDefault("?"),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ClanTagPill(clan.tag)
                                Spacer(modifier = Modifier.width(6.dp))
                                ClanRolePill(role = member.role)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                formatXpAbbrev(member.contributed_xp ?: 0),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun QuizEntryCard(onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "quiz_banner")
    // Border gradient yang geser terus-menerus, kesan "energi" yang mengalir
    val borderShift by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "borderShift"
    )
    // Icon badge berdenyut
    val iconPulse by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconPulse"
    )
    val iconGlow by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconGlow"
    )
    // Kilau (shimmer) yang menyapu dari kiri ke kanan
    val shimmer by infinite.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2400, delayMillis = 500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    // Panah yang "nudge" ke kanan, ngasih sinyal buat di-tap
    val arrowNudge by infinite.animateFloat(
        initialValue = 0f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrowNudge"
    )

    val neonBorder = Brush.linearGradient(
        colors = listOf(Color(0xFFFF3D81), Color(0xFF7B2FBF), Color(0xFF2FE0FF), Color(0xFFFF3D81)),
        start = Offset(borderShift * 500f, 0f),
        end = Offset(borderShift * 500f + 400f, 260f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF14101F), Color(0xFF0A0812))))
            .border(BorderStroke(1.4.dp, neonBorder), RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        // Sapuan cahaya diagonal
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val x = shimmer * w
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset(x - 120f, 0f),
                            end = Offset(x + 120f, size.height)
                        )
                    )
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { scaleX = iconPulse; scaleY = iconPulse }
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF2FE0FF).copy(alpha = iconGlow), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1430))
                        .border(1.dp, Color(0xFF2FE0FF).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Theaters,
                        contentDescription = null,
                        tint = Color(0xFF2FE0FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TEBAK ANIME DARI POSTER",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Jawab bener nambah XP kamu & clan",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2FE0FF).copy(alpha = 0.14f))
                    .border(1.dp, Color(0xFF2FE0FF).copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("+XP", color = Color(0xFF2FE0FF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF2FE0FF),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { translationX = arrowNudge }
            )
        }
    }
}

@Composable
private fun DiamondBalanceCard(balance: Int, onTopUpClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "diamond_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotate"
    )
    val sparkleOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sparkle"
    )

    var animatedBalance by remember { mutableStateOf(0) }
    LaunchedEffect(balance) {
        val start = animatedBalance
        val steps = 16
        for (i in 1..steps) {
            animatedBalance = start + ((balance - start) * i / steps)
            delay(12)
        }
        animatedBalance = balance
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF241530), Color(0xFF16414D))))
    ) {
        Box(
            modifier = Modifier.size(140.dp).align(Alignment.CenterEnd).offset(x = 30.dp)
                .background(Color(0xFF7B2FBF).copy(alpha = glowAlpha), CircleShape)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF5FC9DE),
                modifier = Modifier.size(34.dp).rotate(rotation)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Diamond (DM)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), letterSpacing = 0.5.sp)
                Text("$animatedBalance", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Button(
                onClick = onTopUpClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Top-up", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier = this.then(
    Modifier.graphicsLayer(alpha = alpha.coerceIn(0f, 1f))
)

// Format angka gede ala kartu clan referensi (1_356_830 -> "1.3M", 24_000 -> "24K")
private fun formatXpAbbrev(n: Int): String {
    val abs = kotlin.math.abs(n)
    return when {
        abs >= 1_000_000 -> {
            val v = n / 1_000_000f
            if (v == v.toInt().toFloat()) "${v.toInt()}M" else "%.1fM".format(v)
        }
        abs >= 1_000 -> {
            val v = n / 1_000f
            if (v == v.toInt().toFloat()) "${v.toInt()}K" else "%.1fK".format(v)
        }
        else -> "$n"
    }
}

@Composable
private fun ClanTagPill(tag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFE53950), Color(0xFFFF7A59))))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp, color = Color.White)
    }
}

@Composable
private fun ClanRolePill(role: String?, modifier: Modifier = Modifier) {
    val bg = when (role) {
        "leader" -> Color(0xFFFFC24B)
        "co_leader" -> Color(0xFF64D9E5)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val fg = when (role) {
        "leader" -> Color(0xFF3A2A00)
        "co_leader" -> Color(0xFF073136)
        else -> Color.White.copy(alpha = 0.6f)
    }
    val label = when (role) {
        "leader" -> "LEADER"
        "co_leader" -> "CO-LEADER"
        else -> "MEMBER"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
            color = fg
        )
    }
}

@Composable
private fun ClanStatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun MyClanCard(
    clan: ClanDto,
    members: List<ClanMemberDto>,
    isLeader: Boolean,
    isCoLeader: Boolean,
    isUploadingIcon: Boolean,
    currentUserId: String?,
    onContribute: () -> Unit,
    onManage: () -> Unit,
    onKick: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDemote: (String) -> Unit,
    onTransferLeader: (String) -> Unit,
    onLeave: () -> Unit,
    onIconClick: () -> Unit
) {
    val canManageMembers = isLeader || isCoLeader
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var transferTargetId by remember { mutableStateOf<String?>(null) }
    val xpIntoLevel = (clan.total_xp ?: 0) % 1000
    val progress = xpIntoLevel / 1000f
    val animatedProgress by animateFloatAsState(progress, tween(700, easing = FastOutSlowInEasing), label = "xp_progress")
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Deteksi level naik buat mainin confetti sekali pas clan naik level
    var previousLevel by remember { mutableStateOf(clan.level) }
    var showLevelUpBurst by remember { mutableStateOf(false) }
    LaunchedEffect(clan.level) {
        val prev = previousLevel
        if (prev != null && (clan.level ?: 0) > prev) {
            showLevelUpBurst = true
            delay(2200)
            showLevelUpBurst = false
        }
        previousLevel = clan.level
    }

    val leaderMember = remember(members) { members.firstOrNull { it.role == "leader" } }
    val sortedMembers = remember(members) { members.sortedByDescending { it.contributed_xp ?: 0 } }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A1560), Color(0xFF241530))))
                .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
        ) {
            val sparkleComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sparkle_shine))
            val sparkleProgress by animateLottieCompositionAsState(sparkleComposition, iterations = LottieConstants.IterateForever)
            LottieAnimation(
                composition = sparkleComposition,
                progress = { sparkleProgress },
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = (-26).dp)
            )

            if (showLevelUpBurst) {
                val confettiComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti_burst))
                val confettiProgress by animateLottieCompositionAsState(confettiComposition, iterations = 1)
                LottieAnimation(
                    composition = confettiComposition,
                    progress = { confettiProgress },
                    modifier = Modifier.matchParentSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .animateContentSize()
            ) {
                // ── Header: icon gede + nama/tag/lock + tombol kelola ──
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AnimeGradient)
                            .clickable(enabled = isLeader, onClick = onIconClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingIcon) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                        } else if (!clan.icon_url.isNullOrBlank()) {
                            AsyncImage(
                                model = clan.icon_url, contentDescription = "Icon Clan",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text("${clan.level}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        if (isLeader) {
                            Box(
                                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                                    .background(Color(0xFF1F1530)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.CameraAlt, contentDescription = "Ganti icon", tint = Color.White, modifier = Modifier.size(11.dp)) }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                clan.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 19.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ClanTagPill(clan.tag)
                            if (clan.is_private == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Lock, contentDescription = "Private", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        leaderMember?.let { leader ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!leader.avatar_url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = leader.avatar_url, contentDescription = "Leader",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(18.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFBA68C8).copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(leader.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 9.sp) }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(leader.username.orDefault("?"), fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.75f))
                                Spacer(modifier = Modifier.width(4.dp))
                                ClanCrownIcon(modifier = Modifier.size(11.dp), tint = Color(0xFFFFD700))
                            }
                        }
                    }
                    if (canManageMembers) {
                        IconButton(onClick = onManage, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Kelola Clan", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Stat row: Level / Total XP / Member ──
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClanStatBox(value = "${clan.level ?: 1}", label = "Level", modifier = Modifier.weight(1f))
                    ClanStatBox(value = formatXpAbbrev(clan.total_xp ?: 0), label = "Total XP", modifier = Modifier.weight(1f))
                    ClanStatBox(value = "${members.size}", label = "Member", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onContribute,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA8BF)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Kontribusi", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B2A2E)) }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress ke Lv.${(clan.level ?: 1) + 1}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Text("$xpIntoLevel / 1000 XP", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                val shimmerTransition = rememberInfiniteTransition(label = "xp_shimmer")
                val shimmerX by shimmerTransition.animateFloat(
                    initialValue = -80f, targetValue = 260f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                    label = "shimmer_x"
                )
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(50)).background(AnimeGradient)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(36.dp)
                                .graphicsLayer { translationX = shimmerX }
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "ANGGOTA (${members.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                sortedMembers.forEachIndexed { i, member ->
                    if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!member.avatar_url.isNullOrBlank()) {
                            AsyncImage(
                                model = member.avatar_url, contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFBA68C8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text(member.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                member.username.orDefault("?"),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ClanTagPill(clan.tag)
                                Spacer(modifier = Modifier.width(6.dp))
                                ClanRolePill(role = member.role)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(formatXpAbbrev(member.contributed_xp ?: 0), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        }
                        if (isLeader && member.user_id != currentUserId && member.role == "member") {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { onPromote(member.user_id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.MilitaryTech, contentDescription = "Jadikan Co-Leader", tint = Color(0xFF64D9E5), modifier = Modifier.size(14.dp))
                            }
                        }
                        if (isLeader && member.user_id != currentUserId && member.role == "co_leader") {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { onDemote(member.user_id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.RemoveModerator, contentDescription = "Copot Co-Leader", tint = Color(0xFF64D9E5), modifier = Modifier.size(14.dp))
                            }
                        }
                        if (isLeader && member.user_id != currentUserId && member.role != "leader") {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { transferTargetId = member.user_id }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer Leader", tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                            }
                        }
                        if (canManageMembers && member.user_id != currentUserId && member.role != "leader" &&
                            !(isCoLeader && member.role == "co_leader")
                        ) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { onKick(member.user_id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "Kick", tint = Color(0xFFE57373), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (transferTargetId != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Yakin transfer leader ke member ini? Kamu bakal turun jadi Co-Leader.",
                        fontSize = 12.sp,
                        color = Color(0xFFFFD700)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Button(
                            onClick = {
                                transferTargetId?.let { onTransferLeader(it) }
                                transferTargetId = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                        ) {
                            Text("Ya, Transfer")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { transferTargetId = null }) { Text("Batal", color = Color.White.copy(alpha = 0.6f)) }
                    }
                }

                if (!isLeader) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!showLeaveConfirm) {
                        TextButton(onClick = { showLeaveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keluar Clan", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Yakin mau keluar dari clan ini?", fontSize = 12.sp, color = Color(0xFFE57373))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) {
                                Text("Ya, Keluar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showLeaveConfirm = false }) { Text("Batal", color = Color.White.copy(alpha = 0.6f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateClanSection(
    showCreateForm: Boolean, onShowForm: () -> Unit,
    clanName: String, onNameChange: (String) -> Unit,
    clanTag: String, onTagChange: (String) -> Unit,
    isLoading: Boolean, onCreate: () -> Unit
) {
    Column(modifier = Modifier.animateContentSize()) {
        if (!showCreateForm) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A1560), Color(0xFF241530))))
                    .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onShowForm).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buat Clan Baru \u2014 2.000 DM", color = Color(0xFFB388FF), fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).padding(18.dp)
            ) {
                Text("Buat Clan Baru", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Biaya 2.000 DM, kamu jadi leader otomatis", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = clanName, onValueChange = onNameChange, label = { Text("Nama Lengkap Clan") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = clanTag, onValueChange = onTagChange, label = { Text("Singkatan (2-6 huruf)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onCreate, enabled = !isLoading && clanName.isNotBlank() && clanTag.length in 2..6,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF))
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Buat Clan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ClanTabRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "Clan Saya" to Icons.Default.Shield,
        "Daftar Clan" to Icons.Default.Groups,
        "Leaderboard" to Icons.Default.EmojiEvents
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val selected = selectedIndex == index
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Brush.linearGradient(listOf(Color(0xFF7B2FBF), Color(0xFF2FA8BF))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ClanLeaderboardRow(clan: ClanDto, rank: Int, index: Int, showRank: Boolean = true, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 60L); visible = true }

    val effectiveRank = if (showRank) rank else 0
    val rankColor = when (effectiveRank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(
                        if (effectiveRank in 1..3) Brush.horizontalGradient(listOf(rankColor.copy(alpha = 0.15f), Color(0xFF7B2FBF).copy(alpha = 0.06f)))
                        else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)))
                    )
                    .border(1.dp, if (effectiveRank in 1..3) rankColor.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box {
                if (!clan.icon_url.isNullOrBlank()) {
                    AsyncImage(
                        model = clan.icon_url, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(CircleShape).border(1.dp, rankColor.copy(alpha = 0.5f), CircleShape)
                    )
                } else if (effectiveRank in 1..3) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(rankColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (effectiveRank == 1) {
                            ClanCrownIcon(modifier = Modifier.size(17.dp), tint = rankColor)
                        } else {
                            RankMedalIcon(rank = effectiveRank, tint = rankColor, modifier = Modifier.size(22.dp))
                        }
                    }
                } else if (showRank) {
                    Text("#$rank", fontWeight = FontWeight.Bold, color = rankColor, modifier = Modifier.width(34.dp), fontSize = 13.sp)
                } else {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF7B2FBF).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text(clan.name.take(1).uppercase(), color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }

                // Badge kecil di pojok buat rank 1-3, tetep muncul walaupun clan udah punya icon custom
                if (effectiveRank in 1..3 && !clan.icon_url.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(rankColor)
                            .border(1.dp, Color(0xFF1F1530), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (effectiveRank == 1) {
                            ClanCrownIcon(modifier = Modifier.size(9.dp), tint = Color(0xFF1F1530))
                        } else {
                            Text("$effectiveRank", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1F1530))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${clan.name} [${clan.tag}]", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (clan.is_private == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Private", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(11.dp))
                    }
                }
                Text("Lv.${clan.level} \u2022 ${clan.total_xp} XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }

            if (effectiveRank == 1) {
                val confettiComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti_burst))
                val confettiProgress by animateLottieCompositionAsState(confettiComposition, iterations = 1)
                LottieAnimation(
                    composition = confettiComposition,
                    progress = { confettiProgress },
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
private fun ManageClanDialog(
    clan: ClanDto,
    isLeader: Boolean,
    pendingRequests: List<com.example.network.ClanJoinRequestDto>,
    diamondBalance: Int,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRenameTag: (String) -> Unit,
    onTogglePrivacy: (Boolean) -> Unit,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onDeleteClan: () -> Unit
) {
    var newName by remember { mutableStateOf(clan.name) }
    var newTag by remember { mutableStateOf(clan.tag) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AnimeDialog(
        onDismissRequest = onDismiss,
        title = "Kelola Clan",
        icon = Icons.Default.Settings,
        content = {
            Column(modifier = Modifier.animateContentSize()) {
                if (isLeader) {
                    Text("Ganti Nama Clan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Biaya 1.000 DM \u2014 saldo kamu: $diamondBalance DM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName, onValueChange = { newName = it }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF2FA8BF), unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { if (newName.isNotBlank() && newName != clan.name) onRename(newName) },
                            enabled = newName.isNotBlank() && newName != clan.name
                        ) { Text("Simpan", color = Color(0xFF2FA8BF)) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Ganti Singkatan Clan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Maks 6 karakter, otomatis jadi huruf kapital", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { if (it.length <= 6) newTag = it.uppercase() },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF2FA8BF), unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { if (newTag.isNotBlank() && newTag != clan.tag) onRenameTag(newTag) },
                            enabled = newTag.isNotBlank() && newTag != clan.tag
                        ) { Text("Simpan", color = Color(0xFF2FA8BF)) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clan Private", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            Text("Anggota baru butuh persetujuan kamu", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        Switch(
                            checked = clan.is_private == true, onCheckedChange = onTogglePrivacy,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7B2FBF))
                        )
                    }
                } else {
                    // Co-leader: cuma bisa liat & kelola permintaan gabung, gak ada
                    // akses rename/ganti tag/privacy/hapus clan (tetap leader-only).
                    Text("Kelola Permintaan Gabung", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Sebagai co-leader kamu bisa terima/tolak member baru", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }

                if (clan.is_private == true) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Permintaan Gabung (${pendingRequests.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    if (pendingRequests.isEmpty()) {
                        Text("Belum ada permintaan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
                    }
                    pendingRequests.forEach { req ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(req.username.orDefault("?"), fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onApproveRequest(req.id) }) { Text("Terima", color = Color(0xFF2FA8BF)) }
                            TextButton(onClick = { onRejectRequest(req.id) }) { Text("Tolak", color = Color(0xFFE57373)) }
                        }
                    }
                }

                if (isLeader) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!showDeleteConfirm) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hapus Clan", color = Color(0xFFE57373))
                        }
                    } else {
                        Text("Yakin mau hapus clan? Semua member bakal ikut keluar. Aksi ini gak bisa dibatalkan.", fontSize = 12.sp, color = Color(0xFFE57373))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = onDeleteClan, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))) {
                                Text("Ya, Hapus")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal", color = Color.White.copy(alpha = 0.6f)) }
                        }
                    }
                }
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Tutup", color = Color.White.copy(alpha = 0.6f)) }
        }
    )
}

@Composable
private fun AnimeDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2A1B3D), Color(0xFF16414D))))
                .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-30).dp)
                    .background(Color(0xFF2FA8BF).copy(alpha = 0.15f), CircleShape)
            )
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(AnimeGradient),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(content = content)
                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = buttons)
            }
        }
    }
}

@Composable
private fun ClanHeroInvite(totalClans: Int, totalXp: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_invite")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1B3D), Color(0xFF16414D))))
            .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .background(Color(0xFF2FA8BF).copy(alpha = glowAlpha), CircleShape)
        )
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Belum punya clan sendiri?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Kumpulin temenmu, jadi leader, dan naikin level bareng-bareng lewat kontribusi Diamond. Clan aktif bakal nampil di puncak leaderboard di bawah.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 18.sp
            )
            if (totalClans > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Row {
                    HeroStat(label = "Clan Aktif", value = "$totalClans")
                    Spacer(modifier = Modifier.width(20.dp))
                    HeroStat(label = "Total XP Terkumpul", value = "$totalXp")
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2FA8BF))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun EmptyLeaderboardState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            val sparkleComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sparkle_shine))
            val sparkleProgress by animateLottieCompositionAsState(sparkleComposition, iterations = LottieConstants.IterateForever)
            LottieAnimation(
                composition = sparkleComposition,
                progress = { sparkleProgress },
                modifier = Modifier.size(70.dp)
            )
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Leaderboard masih kosong \u2014 jadilah clan pertama yang duduk di puncak",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// Icon medali buat rank #2 dan #3 di leaderboard, digambar manual pakai Canvas (bukan emoji)
@Composable
private fun RankMedalIcon(rank: Int, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val ribbonPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, 0f)
                lineTo(w * 0.46f, h * 0.5f)
                lineTo(w * 0.30f, h * 0.5f)
                close()
                moveTo(w * 0.70f, 0f)
                lineTo(w * 0.54f, h * 0.5f)
                lineTo(w * 0.70f, h * 0.5f)
                close()
            }
            drawPath(ribbonPath, color = tint.copy(alpha = 0.55f))
            drawCircle(color = tint, radius = w * 0.32f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.64f))
        }
        Text("$rank", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black.copy(alpha = 0.7f))
    }
}

// Icon mahkota digambar manual pakai Canvas (bukan emoji), buat nandain leader clan
@Composable
private fun ClanCrownIcon(modifier: Modifier = Modifier, tint: Color) {
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
