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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.ChatPreview
import com.example.network.FriendshipDto
import com.example.network.ProfileDto
import com.example.util.orDefault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onOpenChat: (otherUserId: String) -> Unit
) {
    val session by viewModel.session.collectAsState()
    val friendships by viewModel.friendships.collectAsState()
    val friendsList by viewModel.friendsList.collectAsState()
    val incomingRequests by viewModel.incomingFriendRequests.collectAsState()
    val isLoading by viewModel.isFriendshipsLoading.collectAsState()
    val userDirectory by viewModel.userDirectory.collectAsState()
    val userChats by viewModel.userChats.collectAsState()
    val myId = session.userId

    LaunchedEffect(Unit) {
        viewModel.loadFriendships()
        viewModel.loadUserDirectory()
    }

    fun profileFor(userId: String): ProfileDto? = userDirectory.firstOrNull { it.id == userId }
    fun chatFor(otherId: String): ChatPreview? = userChats.firstOrNull { it.otherUserId == otherId }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Teman", "Permintaan")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pertemanan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title)
                                if (index == 1 && incomingRequests.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                            .padding(horizontal = 7.dp, vertical = 1.dp)
                                    ) {
                                        Text("${incomingRequests.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (isLoading && friendships.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            when (selectedTab) {
                0 -> {
                    if (friendsList.isEmpty()) {
                        EmptyState(Icons.Default.People, "Belum ada teman.\nCari user lain terus kirim permintaan pertemanan!")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(friendsList, key = { it.id ?: "" }) { fs ->
                                val otherId = if (fs.requester_id == myId) fs.addressee_id else fs.requester_id
                                val profile = profileFor(otherId)
                                val chat = chatFor(otherId)
                                val isUnread = chat != null && chat.lastSenderId.isNotBlank() &&
                                    chat.lastSenderId != myId && chat.lastMessageAt > chat.lastReadAt
                                FriendRow(
                                    profile = profile,
                                    fallbackId = otherId,
                                    chat = chat,
                                    myId = myId,
                                    isUnread = isUnread,
                                    onClick = { onOpenChat(otherId) },
                                    trailing = {
                                        TextButton(onClick = { fs.id?.let { viewModel.removeFriendOrCancelRequest(it) } }) {
                                            Text("Hapus", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (incomingRequests.isEmpty()) {
                        EmptyState(Icons.Default.PersonAdd, "Gak ada permintaan pertemanan baru.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(incomingRequests, key = { it.id ?: "" }) { fs ->
                                val profile = profileFor(fs.requester_id)
                                FriendRow(
                                    profile = profile,
                                    fallbackId = fs.requester_id,
                                    chat = null,
                                    myId = myId,
                                    isUnread = false,
                                    onClick = {},
                                    trailing = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = { fs.id?.let { viewModel.respondToFriendRequest(it, accept = true) } },
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1B7A3D).copy(alpha = 0.2f))
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Terima", tint = Color(0xFF4CAF50))
                                            }
                                            IconButton(
                                                onClick = { fs.id?.let { viewModel.respondToFriendRequest(it, accept = false) } },
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Tolak", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    profile: ProfileDto?,
    fallbackId: String,
    chat: ChatPreview?,
    myId: String?,
    isUnread: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val (roleColor, roleLabel) = when {
        profile?.isAdmin() == true -> Color(0xFFFFD200) to "ADMIN"
        profile?.isModerator() == true -> Color(0xFFB388FF) to "MODERATOR"
        profile?.isBeta() == true -> Color(0xFF22D3EE) to "BETA"
        else -> null to null
    }
    val ringColor = roleColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(ringColor)
                    .padding(1.8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (!profile?.avatar_url.isNullOrEmpty()) {
                    AsyncImage(
                        model = profile?.avatar_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = (profile?.username?.take(1)?.uppercase()) ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (roleLabel != null && roleColor != null) {
                Text(
                    text = roleLabel,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = roleColor,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
            }
            Text(
                text = profile?.username.orDefault("User"),
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.5.sp,
                maxLines = 1
            )
            if (chat != null && chat.lastMessage.isNotBlank()) {
                val prefix = if (chat.lastSenderId == myId) "Kamu: " else ""
                Text(
                    text = "$prefix${chat.lastMessage}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                profile?.user_number?.let {
                    Text("#$it", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (chat != null && chat.lastMessageAt > 0L) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastMessageAt)),
                fontSize = 10.5.sp,
                color = if (isUnread) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        trailing()
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
