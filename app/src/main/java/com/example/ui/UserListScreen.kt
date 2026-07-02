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
        if (query.isBlank()) users
        else users.filter { it.username?.contains(query, ignoreCase = true) == true }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Daftar Pengguna", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }
        }
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
                placeholder = { Text("Cari username...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        items(filteredUsers, key = { it.id }) { user ->
                            UserDirectoryRow(
                                user = user,
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
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val (roleColor, roleLabel) = when {
        user.isAdmin() -> Color(0xFFFFD200) to "ADMIN"
        user.isModerator() -> Color(0xFFB388FF) to "MODERATOR"
        else -> null to null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!user.avatar_url.isNullOrBlank()) {
            AsyncImage(
                model = user.avatar_url,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.username?.take(1)?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (roleLabel != null && roleColor != null) {
                    Text(
                        roleLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = roleColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    user.username ?: "Tanpa nama",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lv.${user.season_level ?: 1}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                user.user_number?.let {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "#$it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
