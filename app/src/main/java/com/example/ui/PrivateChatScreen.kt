package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.PrivateMessage
import com.example.util.orDefault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    var input by remember { mutableStateOf("") }
    var menuForMessage by remember { mutableStateOf<PrivateMessage?>(null) }
    val listState = rememberLazyListState()

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
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!otherProfile?.avatar_url.isNullOrEmpty()) {
                                AsyncImage(
                                    model = otherProfile?.avatar_url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    (otherProfile?.username?.take(1)?.uppercase()) ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(otherProfile?.username.orDefault("User"), fontWeight = FontWeight.Bold, fontSize = 15.5.sp, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (replyingTo != null) {
                    val target = replyingTo!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (target.senderId == myId) "Membalas pesanmu" else "Membalas ${otherProfile?.username.orDefault("pesan")}",
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
                        IconButton(onClick = { viewModel.clearReplyTarget() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Batal balas")
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tulis pesan...") },
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendPrivateMessage(input)
                                input = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada pesan.\nMulai obrolan sekarang!",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == myId
                    Box {
                        PrivateMessageBubble(
                            message = msg,
                            isMine = isMine,
                            otherUsername = otherProfile?.username.orDefault("User"),
                            onLongPress = { if (!msg.deleted) menuForMessage = msg }
                        )
                        DropdownMenu(
                            expanded = menuForMessage?.id == msg.id,
                            onDismissRequest = { menuForMessage = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Balas") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                                onClick = {
                                    viewModel.setReplyTarget(msg)
                                    menuForMessage = null
                                }
                            )
                            if (isMine) {
                                DropdownMenuItem(
                                    text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isMine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onLongPress
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            if (!message.deleted && message.replyToId != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isMine) Color.White.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
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
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                if (message.deleted) "Pesan ini telah dihapus" else message.text,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontStyle = if (message.deleted) FontStyle.Italic else FontStyle.Normal
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
