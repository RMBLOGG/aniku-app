package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.network.AnikuViewModel
import com.example.network.ProfileDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val users by viewModel.userDirectory.collectAsState()
    val isLoading by viewModel.isUserDirectoryLoading.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadUserDirectory()
    }

    val filteredUsers = remember(users, query) {
        val sorted = users.sortedByDescending { it.season_xp ?: 0 }
        if (query.isBlank()) sorted
        else sorted.filter { it.username?.contains(query, ignoreCase = true) == true }
    }
    // Peringkat XP dihitung dari daftar lengkap (belum difilter pencarian) biar #rank-nya tetap konsisten
    val rankByUserId = remember(users) {
        users.sortedByDescending { it.season_xp ?: 0 }
            .mapIndexed { index, user -> user.id to (index + 1) }
            .toMap()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Daftar Pengguna", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Cari username...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            if (users.isNotEmpty()) {
                Text(
                    "${filteredUsers.size} pengguna" + if (query.isNotBlank()) " ditemukan" else " terdaftar",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            when {
                isLoading && users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                filteredUsers.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "Belum ada pengguna" else "Gak ketemu \"$query\"",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredUsers, key = { it.id }) { user ->
                            UserDirectoryRow(
                                user = user,
                                rank = rankByUserId[user.id] ?: 0,
                                onClick = { onUserClick(user.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDirectoryRow(
    user: ProfileDto,
    rank: Int,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val (roleColor, roleLabel) = when {
        user.isAdmin() -> Color(0xFFFFD200) to "ADMIN"
        user.isModerator() -> Color(0xFFB388FF) to "MODERATOR"
        else -> null to null
    }
    val ringColor = roleColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    // Tiga besar XP dikasih warna medali biar langsung kebaca podium-nya
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(ringColor)
                .padding(1.8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!user.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = user.avatar_url,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.username?.take(1)?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (roleLabel != null && roleColor != null) {
                Text(
                    roleLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = roleColor,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
            }
            Text(
                user.username ?: "Tanpa nama",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Level ${user.season_level ?: 1}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (rank > 0) {
                    Text(
                        "  \u2022  #$rank",
                        fontSize = 12.sp,
                        fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
                        color = rankColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Chip XP
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Text(
                "${user.season_xp ?: 0} XP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(20.dp)
        )
    }
}
