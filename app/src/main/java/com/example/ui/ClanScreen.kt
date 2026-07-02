package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AnikuViewModel
import com.example.network.ClanDto
import kotlinx.coroutines.delay

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
    val selectedClanMembers by viewModel.selectedClanMembers.collectAsState()
    val clanActionError by viewModel.clanActionError.collectAsState()
    val isClanLoading by viewModel.isClanLoading.collectAsState()

    var showCreateForm by remember { mutableStateOf(false) }
    var clanName by remember { mutableStateOf("") }
    var clanTag by remember { mutableStateOf("") }
    var selectedClan by remember { mutableStateOf<ClanDto?>(null) }
    var contributeAmount by remember { mutableStateOf("") }
    var showContributeDialog by remember { mutableStateOf(false) }

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
                    title = { Text("Clan", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
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
                        onContribute = { showContributeDialog = true }
                    )
                }
            } else {
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

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TOP CLANS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFD700), letterSpacing = 1.sp)
                    }
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
    }

    if (showContributeDialog) {
        AlertDialog(
            onDismissRequest = { showContributeDialog = false },
            title = { Text("Kontribusi ke Clan") },
            text = {
                Column {
                    Text("Saldo kamu: $diamondBalance DM", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contributeAmount,
                        onValueChange = { contributeAmount = it.filter { c -> c.isDigit() } },
                        label = { Text("Jumlah DM") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    contributeAmount.toIntOrNull()?.let { viewModel.contributeToClan(it) }
                    showContributeDialog = false
                    contributeAmount = ""
                }) { Text("Kontribusi") }
            },
            dismissButton = {
                TextButton(onClick = { showContributeDialog = false }) { Text("Batal") }
            }
        )
    }

    selectedClan?.let { clan ->
        AlertDialog(
            onDismissRequest = { selectedClan = null },
            title = { Text("${clan.name} [${clan.tag}]") },
            text = {
                Column {
                    Text("Level ${clan.level} \u2022 ${clan.total_xp} XP \u2022 ${selectedClanMembers.size} member", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    selectedClanMembers.forEach { member ->
                        Text(
                            "\u2022 " + (member.username ?: "?") + if (member.role == "leader") " (Leader)" else "",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.joinClan(clan.id)
                    selectedClan = null
                }) { Text("Gabung Clan Ini") }
            },
            dismissButton = {
                TextButton(onClick = { selectedClan = null }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun DiamondBalanceCard(balance: Int, onTopUpClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "diamond_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotate"
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
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF0B2F3F), Color(0xFF135C73), Color(0xFF1B7A8C)))
            )
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 30.dp)
                .background(Color(0xFF4FD8E8).copy(alpha = glowAlpha), CircleShape)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Diamond,
                contentDescription = null,
                tint = Color(0xFF6EEAFA),
                modifier = Modifier
                    .size(34.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Diamond (DM)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), letterSpacing = 0.5.sp)
                Text("$animatedBalance", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Button(
                onClick = onTopUpClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA68C8)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Top-up", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MyClanCard(
    clan: ClanDto,
    members: List<com.example.network.ClanMemberDto>,
    onContribute: () -> Unit
) {
    val xpIntoLevel = (clan.total_xp ?: 0) % 1000
    val progress = xpIntoLevel / 1000f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "xp_progress"
    )
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2A1B3D), Color(0xFF1F1530))))
                .border(1.dp, Color(0xFFBA68C8).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(18.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFBA68C8), Color(0xFF7E57C2)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${clan.level}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${clan.name} [${clan.tag}]", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(
                        "${clan.total_xp ?: 0} XP total \u2022 ${members.size} member",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
                Button(
                    onClick = onContribute,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5b9cf6))
                ) { Text("Kontribusi", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progress ke Lv.${(clan.level ?: 1) + 1}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                Text("$xpIntoLevel / 1000 XP", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFBA68C8), Color(0xFF6EEAFA))))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            members.forEachIndexed { i, member ->
                if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFBA68C8).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.username?.take(1)?.uppercase() ?: "?", color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        (member.username ?: "?") + if (member.role == "leader") " \uD83D\uDC51" else "",
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${member.contributed_xp ?: 0} XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
            }
        }
    }
}

@Composable
private fun CreateClanSection(
    showCreateForm: Boolean,
    onShowForm: () -> Unit,
    clanName: String,
    onNameChange: (String) -> Unit,
    clanTag: String,
    onTagChange: (String) -> Unit,
    isLoading: Boolean,
    onCreate: () -> Unit
) {
    Column(modifier = Modifier.animateContentSize()) {
        if (!showCreateForm) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2A1B3D), Color(0xFF1F1530))))
                    .border(1.dp, Color(0xFFE53935).copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onShowForm)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buat Clan Baru \u2014 2.000 DM", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(18.dp)
            ) {
                Text("Buat Clan Baru", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Biaya 2.000 DM, kamu jadi leader otomatis", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = clanName,
                    onValueChange = onNameChange,
                    label = { Text("Nama Lengkap Clan") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = clanTag,
                    onValueChange = onTagChange,
                    label = { Text("Singkatan (2-6 huruf)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onCreate,
                    enabled = !isLoading && clanName.isNotBlank() && clanTag.length in 2..6,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
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
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }

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
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (rank <= 3) Brush.horizontalGradient(listOf(rankColor.copy(alpha = 0.12f), Color.Transparent))
                    else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)))
                )
                .border(1.dp, if (rank <= 3) rankColor.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank <= 3) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(rankColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = rankColor, modifier = Modifier.size(16.dp))
                }
            } else {
                Text("#$rank", fontWeight = FontWeight.Bold, color = rankColor, modifier = Modifier.width(30.dp), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${clan.name} [${clan.tag}]", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Lv.${clan.level} \u2022 ${clan.total_xp} XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}
