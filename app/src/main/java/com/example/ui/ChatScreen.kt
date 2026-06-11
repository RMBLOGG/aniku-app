package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.example.network.AnikuViewModel
import com.example.network.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AnikuViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val chatError by viewModel.chatError.collectAsState()

    val isLoggedIn = !session.token.isNullOrEmpty()
    val currentUserId = session.userId

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-poll setiap 5 detik (Supabase REST polling)
    LaunchedEffect(Unit) {
        viewModel.loadChatMessages()
        while (true) {
            delay(5000)
            viewModel.loadChatMessages()
        }
    }

    // Auto-scroll ke bawah saat ada pesan baru
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Tampilkan error via snackbar
    LaunchedEffect(chatError) {
        chatError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearChatError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "💬 Chat Room",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            if (isLoggedIn) "Online sebagai ${session.username}" else "Mode tamu — hanya lihat",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadChatMessages() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Input bar
            Column {
                Divider()
                if (isLoggedIn) {
                    // User login — bisa kirim pesan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { if (it.length <= 300) inputText = it },
                            placeholder = { Text("Ketik pesan...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                val msg = inputText.trim()
                                if (msg.isNotEmpty()) {
                                    viewModel.sendChatMessage(msg)
                                    inputText = ""
                                    coroutineScope.launch {
                                        delay(300)
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                    }
                                }
                            },
                            enabled = inputText.trim().isNotEmpty(),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Kirim",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Guest — tampilkan CTA login
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Login untuk ikut chat",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            onClick = { navController.navigate("auth") },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("Login", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isChatLoading && messages.isEmpty() -> {
                    // Loading pertama kali
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Memuat pesan...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
                messages.isEmpty() -> {
                    // Kosong
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💬", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Belum ada pesan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                if (isLoggedIn) "Jadilah yang pertama chat!" else "Login untuk mulai chat",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            val isOwn = message.user_id == currentUserId
                            ChatBubble(
                                message = message,
                                isOwnMessage = isOwn,
                                onDelete = if (isOwn || session.isAdmin) {
                                    { viewModel.deleteChatMessage(message.id) }
                                } else null
                            )
                        }
                        // Spacer di bawah
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    onDelete: (() -> Unit)?
) {
    val timeStr = remember(message.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(message.created_at.take(19)) ?: Date()
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            "--:--"
        }
    }

    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwnMessage) {
            // Avatar kiri untuk pesan orang lain
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.username.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isOwnMessage) {
                Text(
                    text = message.username,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isOwnMessage) 16.dp else 4.dp,
                            topEnd = if (isOwnMessage) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isOwnMessage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (onDelete != null) showDelete = true
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.message,
                    fontSize = 14.sp,
                    color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = timeStr,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 0.dp)
            )
        }
    }

    // Dialog konfirmasi hapus
    if (showDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Hapus pesan?") },
            text = { Text("Pesan ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDelete = false
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
