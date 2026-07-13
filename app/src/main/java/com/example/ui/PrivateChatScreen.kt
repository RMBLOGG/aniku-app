package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.PrivateMessage
import com.example.util.orDefault
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrivateChatScreen(
    viewModel: AnikuViewModel,
    otherUserId: String,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val userDirectory by viewModel.userDirectory.collectAsState()
    val messages by viewModel.privateChatMessages.collectAsState()
    val replyingTo by viewModel.replyingToMessage.collectAsState()
    val myId = session.userId
    val otherProfile = remember(userDirectory, otherUserId) {
        userDirectory.firstOrNull { it.id == otherUserId }
    }
    val otherUsername = otherProfile?.username.orDefault("User")

    var input by remember { mutableStateOf("") }
    var menuForMessage by remember { mutableStateOf<PrivateMessage?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(otherUserId) {
        viewModel.openPrivateChat(otherUserId)
        viewModel.loadUserDirectory()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.closePrivateChat() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                username = otherUsername,
                avatarUrl = otherProfile?.avatar_url,
                onBack = onBack
            )
        },
        bottomBar = {
            ChatInputBar(
                input = input,
                onInputChange = { input = it },
                replyingTo = replyingTo,
                otherUsername = otherUsername,
                myId = myId,
                onCancelReply = { viewModel.clearReplyTarget() },
                onSend = {
                    if (input.isNotBlank()) {
                        viewModel.sendPrivateMessage(input)
                        input = ""
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        radius = 1000f
                    )
                )
        ) {
            if (messages.isEmpty()) {
                EmptyChatState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == myId
                        var appeared by remember(msg.id) { mutableStateOf(false) }
                        LaunchedEffect(msg.id) { appeared = true }
                        AnimatedVisibility(
                            visible = appeared,
                            modifier = Modifier.animateItem(placementSpec = tween(260)),
                            enter = fadeIn(tween(260)) + slideInVertically(
                                initialOffsetY = { full -> full / 4 },
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            )
                        ) {
                            Box {
                                PrivateMessageBubble(
                                    message = msg,
                                    isMine = isMine,
                                    otherUsername = otherUsername,
                                    onLongPress = { if (!msg.deleted) menuForMessage = msg }
                                )
                                ChatContextMenu(
                                    expanded = menuForMessage?.id == msg.id,
                                    isMine = isMine,
                                    onDismiss = { menuForMessage = null },
                                    onReply = {
                                        viewModel.setReplyTarget(msg)
                                        menuForMessage = null
                                    },
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(msg.text))
                                        menuForMessage = null
                                        scope.launch { snackbarHostState.showSnackbar("Pesan disalin") }
                                    },
                                    onDelete = {
                                        viewModel.deletePrivateMessage(msg.id)
                                        menuForMessage = null
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
private fun ChatTopBar(
    username: String,
    avatarUrl: String?,
    onBack: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ringRotate")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(7000, easing = LinearEasing)),
        label = "ringRotation"
    )

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = ringRotation }
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            username.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(username, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, maxLines = 1)
                Text(
                    "Obrolan pribadi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

@Composable
private fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    replyingTo: PrivateMessage?,
    otherUsername: String,
    myId: String?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(
            visible = replyingTo != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
        ) {
            val target = replyingTo
            if (target != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                if (target.senderId == myId) "Membalas pesanmu" else "Membalas $otherUsername",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (target.deleted) "Pesan telah dihapus" else target.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontStyle = if (target.deleted) FontStyle.Italic else FontStyle.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Filled.Close, contentDescription = "Batal balas")
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tulis pesan...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            val sendInteraction = remember { MutableInteractionSource() }
            val sendPressed by sendInteraction.collectIsPressedAsState()
            val sendScale by animateFloatAsState(if (sendPressed) 0.88f else 1f, label = "sendScale")
            val sendBg by animateColorAsState(
                if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "sendBg"
            )
            IconButton(
                onClick = onSend,
                interactionSource = sendInteraction,
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                    .clip(CircleShape)
                    .background(sendBg)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim",
                    tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatContextMenu(
    expanded: Boolean,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp,
        shadowElevation = 10.dp
    ) {
        ChatMenuItem(icon = Icons.AutoMirrored.Filled.Reply, label = "Balas", tint = MaterialTheme.colorScheme.primary, onClick = onReply)
        ChatMenuItem(icon = Icons.Filled.ContentCopy, label = "Salin", tint = MaterialTheme.colorScheme.onSurface, onClick = onCopy)
        if (isMine) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            ChatMenuItem(icon = Icons.Filled.Delete, label = "Hapus", tint = MaterialTheme.colorScheme.error, onClick = onDelete)
        }
    }
}

@Composable
private fun ChatMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Medium, color = tint) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
        },
        onClick = onClick
    )
}

@Composable
private fun EmptyChatState() {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyScale"
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Belum ada pesan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Mulai obrolan sekarang!",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrivateMessageBubble(
    message: PrivateMessage,
    isMine: Boolean,
    otherUsername: String,
    onLongPress: () -> Unit
) {
    val timeStr = remember(message.timestamp) {
        try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        } catch (e: Exception) {
            ""
        }
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (message.deleted) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Pesan ini telah dihapus",
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(timeStr, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .shadow(elevation = if (isMine) 3.dp else 1.dp, shape = bubbleShape, clip = false)
                    .clip(bubbleShape)
                    .background(
                        if (isMine) Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        ) else Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                if (message.replyToId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isMine) Color.White.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary)
                        )
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(
                                if (message.replyToSenderId == message.senderId) "Diri sendiri"
                                else if (isMine) otherUsername else "Kamu",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                message.replyToText.orDefault("Pesan"),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                color = if (isMine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }
                Text(
                    message.text,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    timeStr,
                    color = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
