package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
    val seenFriendIds = remember { mutableSetOf<String>() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text("Pertemanan", fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        )
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            FriendTabSwitcher(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
                requestCount = incomingRequests.size
            )

            if (isLoading && friendships.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                val rowKey = fs.id ?: otherId
                                val alreadySeen = remember(rowKey) { rowKey in seenFriendIds }
                                val anim = remember(rowKey) { Animatable(if (alreadySeen) 1f else 0f) }
                                LaunchedEffect(rowKey) {
                                    seenFriendIds.add(rowKey)
                                    if (!alreadySeen) {
                                        anim.animateTo(1f, animationSpec = tween(240))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .animateItem(placementSpec = tween(240))
                                        .graphicsLayer {
                                            alpha = anim.value
                                            translationY = (1f - anim.value) * 30f
                                        }
                                ) {
                                    FriendRow(
                                        profile = profile,
                                        fallbackId = otherId,
                                        chat = chat,
                                        myId = myId,
                                        isUnread = isUnread,
                                        onClick = { onOpenChat(otherId) },
                                        trailing = {
                                            PillActionButton(
                                                label = "Hapus",
                                                tint = MaterialTheme.colorScheme.error,
                                                onClick = { fs.id?.let { viewModel.removeFriendOrCancelRequest(it) } }
                                            )
                                        }
                                    )
                                }
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
                                val rowKey = fs.id ?: fs.requester_id
                                val alreadySeen = remember(rowKey) { rowKey in seenFriendIds }
                                val anim = remember(rowKey) { Animatable(if (alreadySeen) 1f else 0f) }
                                LaunchedEffect(rowKey) {
                                    seenFriendIds.add(rowKey)
                                    if (!alreadySeen) {
                                        anim.animateTo(1f, animationSpec = tween(240))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .animateItem(placementSpec = tween(240))
                                        .graphicsLayer {
                                            alpha = anim.value
                                            translationY = (1f - anim.value) * 30f
                                        }
                                ) {
                                    FriendRow(
                                        profile = profile,
                                        fallbackId = fs.requester_id,
                                        chat = null,
                                        myId = myId,
                                        isUnread = false,
                                        onClick = {},
                                        trailing = {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                AnimatedIconButton(
                                                    icon = Icons.Filled.Check,
                                                    background = Color(0xFF1B7A3D).copy(alpha = 0.18f),
                                                    tint = Color(0xFF4CAF50),
                                                    contentDescription = "Terima",
                                                    onClick = { fs.id?.let { viewModel.respondToFriendRequest(it, accept = true) } }
                                                )
                                                AnimatedIconButton(
                                                    icon = Icons.Filled.Close,
                                                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    contentDescription = "Tolak",
                                                    onClick = { fs.id?.let { viewModel.respondToFriendRequest(it, accept = false) } }
                                                )
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
}

@Composable
private fun FriendTabSwitcher(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    requestCount: Int
) {
    val tabs = listOf("Teman", "Permintaan")
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedTab,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "tabIndicator"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                val textColor by animateColorAsState(
                    if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabText"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    if (index == 1 && requestCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.error)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("$requestCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedIconButton(
    icon: ImageVector,
    background: Color,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, label = "iconBtnScale")
    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
private fun PillActionButton(
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "pillScale")
    Text(
        label,
        color = tint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.1f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
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
    val ringColor = roleColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "rowScale")
    val rowShape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = if (isUnread) 3.dp else 1.dp, shape = rowShape, clip = false)
            .clip(rowShape)
            .background(
                if (isUnread) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(listOf(ringColor, ringColor.copy(alpha = 0.2f), ringColor))
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(1.6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
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
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
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
private fun EmptyState(icon: ImageVector, message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyScale"
    )
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
