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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.ClanDto
import com.example.network.ClanMemberDto
import kotlinx.coroutines.delay

private val AnimeGradient = Brush.linearGradient(listOf(Color(0xFF7B2FBF), Color(0xFF2FA8BF)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onTopUpClick: () -> Unit
) {
    val diamondBalance by viewModel.diamondBalance.collectAsState()
    val topClans by viewModel.topClans.collectAsState()
    val myClan by viewModel.myClanDetail.collectAsState()
    val myMembership by viewModel.myClanMembership.collectAsState()
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
    var showManageDialog by remember { mutableStateOf(false) }
    val pendingRequests by viewModel.pendingJoinRequests.collectAsState()

    val isLeader = myMembership?.role == "leader" && myMembership?.user_id == session.userId

    LaunchedEffect(myClan, isLeader) {
        if (isLeader) myClan?.let { viewModel.loadPendingJoinRequests(it.id) }
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
        myClan?.let { viewModel.loadClanMembers(it.id) }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { DiamondBalanceCard(diamondBalance, onTopUpClick) }

            clanActionError?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            if (myClan != null) {
                item {
                    MyClanCard(
                        clan = myClan!!,
                        members = selectedClanMembers,
                        isLeader = isLeader,
                        isUploadingIcon = isUploadingIcon,
                        currentUserId = session.userId,
                        onContribute = { showContributeDialog = true },
                        onManage = { showManageDialog = true },
                        onKick = { userId -> viewModel.kickMember(myClan!!.id, userId) },
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

            // Leaderboard clan lain, tetep muncul walaupun user udah punya clan sendiri
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("TOP CLANS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFD700), letterSpacing = 1.sp)
                }
            }

            if (topClans.isEmpty()) {
                item { EmptyLeaderboardState() }
            }

            itemsIndexed(topClans, key = { _, c -> c.id }) { index, clan ->
                ClanLeaderboardRow(
                    clan = clan,
                    rank = index + 1,
                    index = index,
                    onClick = {
                        selectedClan = clan
                        viewModel.loadClanMembers(clan.id)
                    }
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
            pendingRequests = pendingRequests,
            diamondBalance = diamondBalance,
            onDismiss = { showManageDialog = false },
            onRename = { newName -> viewModel.renameClan(myClan!!.id, newName) },
            onTogglePrivacy = { isPrivate -> viewModel.setClanPrivacy(myClan!!.id, isPrivate) },
            onApproveRequest = { reqId -> viewModel.approveJoinRequest(reqId, myClan!!.id) },
            onRejectRequest = { reqId -> viewModel.rejectJoinRequest(reqId, myClan!!.id) },
            onDeleteClan = {
                viewModel.deleteClan(myClan!!.id) { showManageDialog = false }
            }
        )
    }

    selectedClan?.let { clan ->
        AnimeDialog(
            onDismissRequest = { selectedClan = null },
            title = "${clan.name} [${clan.tag}]",
            icon = Icons.Default.Groups,
            content = {
                Text("Level ${clan.level} \u2022 ${clan.total_xp} XP \u2022 ${selectedClanMembers.size} member", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                if (clan.is_private == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clan private \u2014 butuh persetujuan leader", fontSize = 12.sp, color = Color(0xFFB388FF))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                selectedClanMembers.forEach { member ->
                    Text(
                        "\u2022 " + (member.username ?: "?") + if (member.role == "leader") " (Leader)" else "",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            },
            buttons = {
                TextButton(onClick = { selectedClan = null }) { Text("Tutup", color = Color.White.copy(alpha = 0.6f)) }
                if (myClan == null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            if (clan.is_private == true) viewModel.requestJoinClan(clan.id) else viewModel.joinClan(clan.id)
                            selectedClan = null
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF))
                    ) { Text(if (clan.is_private == true) "Minta Gabung" else "Gabung Clan Ini", fontWeight = FontWeight.Bold) }
                }
            }
        )
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

@Composable
private fun MyClanCard(
    clan: ClanDto,
    members: List<ClanMemberDto>,
    isLeader: Boolean,
    isUploadingIcon: Boolean,
    currentUserId: String?,
    onContribute: () -> Unit,
    onManage: () -> Unit,
    onKick: (String) -> Unit,
    onLeave: () -> Unit,
    onIconClick: () -> Unit
) {
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val xpIntoLevel = (clan.total_xp ?: 0) % 1000
    val progress = xpIntoLevel / 1000f
    val animatedProgress by animateFloatAsState(progress, tween(700, easing = FastOutSlowInEasing), label = "xp_progress")
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A1560), Color(0xFF241530))))
                .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AnimeGradient)
                        .clickable(enabled = isLeader, onClick = onIconClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingIcon) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else if (!clan.icon_url.isNullOrBlank()) {
                        AsyncImage(
                            model = clan.icon_url, contentDescription = "Icon Clan",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text("${clan.level}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    if (isLeader) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape)
                                .background(Color(0xFF1F1530)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.CameraAlt, contentDescription = "Ganti icon", tint = Color.White, modifier = Modifier.size(10.dp)) }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${clan.name} [${clan.tag}]", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        if (clan.is_private == true) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Lock, contentDescription = "Private", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                        }
                    }
                    Text("${clan.total_xp ?: 0} XP total \u2022 ${members.size} member", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
                }
                if (isLeader) {
                    IconButton(onClick = onManage, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Kelola Clan", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Button(
                    onClick = onContribute, shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2FA8BF))
                ) { Text("Kontribusi", fontWeight = FontWeight.Bold, color = Color(0xFF1B2A2E)) }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progress ke Lv.${(clan.level ?: 1) + 1}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                Text("$xpIntoLevel / 1000 XP", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50)).background(AnimeGradient)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            members.forEachIndexed { i, member ->
                if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!member.avatar_url.isNullOrBlank()) {
                        AsyncImage(
                            model = member.avatar_url, contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.5f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFBA68C8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) { Text(member.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        member.username ?: "?",
                        fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (member.role == "leader") {
                        Spacer(modifier = Modifier.width(4.dp))
                        ClanCrownIcon(modifier = Modifier.size(12.dp), tint = Color(0xFFFFD700))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${member.contributed_xp ?: 0} XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    if (isLeader && member.user_id != currentUserId) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { onKick(member.user_id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.PersonRemove, contentDescription = "Kick", tint = Color(0xFFE57373), modifier = Modifier.size(14.dp))
                        }
                    }
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
private fun ClanLeaderboardRow(clan: ClanDto, rank: Int, index: Int, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 60L); visible = true }

    val rankColor = when (rank) {
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
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(
                    if (rank <= 3) Brush.horizontalGradient(listOf(rankColor.copy(alpha = 0.15f), Color(0xFF7B2FBF).copy(alpha = 0.06f)))
                    else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)))
                )
                .border(1.dp, if (rank <= 3) rankColor.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!clan.icon_url.isNullOrBlank()) {
                AsyncImage(
                    model = clan.icon_url, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape).border(1.dp, rankColor.copy(alpha = 0.5f), CircleShape)
                )
            } else if (rank <= 3) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(rankColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = rankColor, modifier = Modifier.size(16.dp)) }
            } else {
                Text("#$rank", fontWeight = FontWeight.Bold, color = rankColor, modifier = Modifier.width(34.dp), fontSize = 13.sp)
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
    }
}

@Composable
private fun ManageClanDialog(
    clan: ClanDto,
    pendingRequests: List<com.example.network.ClanJoinRequestDto>,
    diamondBalance: Int,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onTogglePrivacy: (Boolean) -> Unit,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onDeleteClan: () -> Unit
) {
    var newName by remember { mutableStateOf(clan.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AnimeDialog(
        onDismissRequest = onDismiss,
        title = "Kelola Clan",
        icon = Icons.Default.Settings,
        content = {
            Column(modifier = Modifier.animateContentSize()) {
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
                            Text(req.username ?: "?", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onApproveRequest(req.id) }) { Text("Terima", color = Color(0xFF2FA8BF)) }
                            TextButton(onClick = { onRejectRequest(req.id) }) { Text("Tolak", color = Color(0xFFE57373)) }
                        }
                    }
                }

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
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Leaderboard masih kosong \u2014 jadilah clan pertama yang duduk di puncak",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
