package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AnikuViewModel
import com.example.network.ClanDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onTopUpClick: () -> Unit
) {
    val diamondBalance by viewModel.diamondBalance.collectAsState()
    val topClans by viewModel.topClans.collectAsState()
    val myMembership by viewModel.myClanMembership.collectAsState()
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

    val myClan = remember(topClans, myMembership) {
        topClans.find { it.id == myMembership?.clan_id }
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
            // --- Saldo Diamond ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0D3B4F), Color(0xFF135C73))))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF4FD8E8), modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Diamond (DM)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text("$diamondBalance", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(
                        onClick = onTopUpClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA68C8))
                    ) { Text("Top-up") }
                }
            }

            clanActionError?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            // --- Clan saya ---
            if (myClan != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF241a2e))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${myClan.name} [${myClan.tag}]", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "Lv.${myClan.level} \u2022 ${myClan.total_xp} XP \u2022 ${selectedClanMembers.size} member",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Button(
                                onClick = { showContributeDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5b9cf6))
                            ) { Text("Kontribusi") }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        selectedClanMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (member.username ?: "?") + if (member.role == "leader") " \uD83D\uDC51" else "",
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${member.contributed_xp ?: 0} XP", fontSize = 12.sp, color = Color(0xFFFFD700))
                            }
                        }
                    }
                }
            } else {
                // --- Belum join clan: opsi buat / pilih dari leaderboard ---
                item {
                    if (!showCreateForm) {
                        OutlinedButton(
                            onClick = { showCreateForm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buat Clan Baru \u2014 2.000 DM")
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                .padding(16.dp)
                        ) {
                            Text("Buat Clan Baru \u2014 biaya 2.000 DM", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = clanName,
                                onValueChange = { clanName = it },
                                label = { Text("Nama Lengkap Clan") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = clanTag,
                                onValueChange = { if (it.length <= 6) clanTag = it.uppercase() },
                                label = { Text("Singkatan (2-6 huruf)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.createClan(clanName, clanTag) },
                                enabled = !isClanLoading && clanName.isNotBlank() && clanTag.length in 2..6,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isClanLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Buat Clan")
                            }
                        }
                    }
                }

                item {
                    Text(
                        "\uD83D\uDC51 TOP CLANS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                itemsIndexed(topClans) { index, clan ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                            .clickable {
                                selectedClan = clan
                                viewModel.loadClanMembers(clan.id)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${index + 1}", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), modifier = Modifier.width(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${clan.name} [${clan.tag}]", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Lv.${clan.level} \u2022 ${clan.total_xp} XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }

    // --- Dialog kontribusi DM ke clan sendiri ---
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

    // --- Dialog detail clan (dari leaderboard) buat gabung ---
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
