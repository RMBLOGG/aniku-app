package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Warna custom biar lepas dari Material3 defaults ──────────────────────────
private val AksenMerah     = Color(0xFFE5282A)
private val AksenMerahGelap = Color(0xFF9B1B1C)
private val BubbleOwn      = Color(0xFFCC2020)
private val BubbleOther    = Color(0xFF1A1A1A)
private val BorderOther    = Color(0xFF2E2E2E)
private val TextDim        = Color(0xFF666666)
private val TextMain       = Color(0xFFEEEEEE)
private val BgSurface      = Color(0xFF0D0D0D)
private val InputBg        = Color(0xFF151515)
private val DividerColor   = Color(0xFF222222)

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

    LaunchedEffect(Unit) {
        viewModel.loadChatMessages()
        while (true) {
            delay(5000)
            viewModel.loadChatMessages()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(chatError) {
        chatError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearChatError()
        }
    }

    Scaffold(
        containerColor = BgSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // ── Header custom — bukan Material TopAppBar generik ─────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgSurface)
            ) {
                // garis aksen merah tipis di bawah header
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Kembali",
                                tint = TextMain
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // aksen slash merah sebelum judul
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(18.dp)
                                        .background(AksenMerah, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "CHAT ROOM",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.5.sp,
                                    color = TextMain
                                )
                            }
                            Text(
                                if (isLoggedIn) "● ${session.username}" else "○ tamu — hanya lihat",
                                fontSize = 11.sp,
                                color = if (isLoggedIn) AksenMerah.copy(alpha = 0.85f) else TextDim,
                                modifier = Modifier.padding(start = 11.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.loadChatMessages() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextDim
                            )
                        }
                    }
                    // garis tipis merah-ke-transparan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AksenMerah, AksenMerahGelap, Color.Transparent)
                                )
                            )
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(BgSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerColor)
                )
                if (isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Input flat, bukan outlined bulat ─────────────────
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputBg)
                                .border(
                                    width = 1.dp,
                                    color = DividerColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { if (it.length <= 300) inputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                textStyle = TextStyle(
                                    color = TextMain,
                                    fontSize = 14.sp
                                ),
                                maxLines = 3,
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            "Ketik pesan...",
                                            color = TextDim,
                                            fontSize = 14.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // ── Send button — kotak kecil, bukan circle besar ────
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (inputText.trim().isNotEmpty()) AksenMerah
                                    else Color(0xFF1E1E1E)
                                )
                                .clickable(enabled = inputText.trim().isNotEmpty()) {
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
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = if (inputText.trim().isNotEmpty()) Color.White
                                       else TextDim,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // ── Guest bar ────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "//",
                            color = AksenMerah,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Login untuk ikut chat",
                            color = TextDim,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AksenMerah)
                                .clickable { navController.navigate("auth") }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "LOGIN",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgSurface)
                .padding(innerPadding)
        ) {
            when {
                isChatLoading && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AksenMerah, strokeWidth = 2.dp)
                    }
                }
                messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "[ ]",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Thin,
                                color = TextDim,
                                letterSpacing = 4.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "BELUM ADA PESAN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = TextDim
                            )
                            Text(
                                if (isLoggedIn) "jadilah yang pertama" else "login untuk mulai",
                                fontSize = 11.sp,
                                color = TextDim.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = parser.parse(message.created_at.take(19)) ?: Date()
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
            formatter.format(date)
        } catch (e: Exception) { "--:--" }
    }

    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        // ── Avatar orang lain — kotak kecil bukan circle besar ───────────────
        if (!isOwnMessage) {
            if (!message.avatar_url.isNullOrEmpty()) {
                AsyncImage(
                    model = message.avatar_url,
                    contentDescription = message.username,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AksenMerahGelap),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.username.take(1).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            // ── Username + badge admin ────────────────────────────────────────
            if (!isOwnMessage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Text(
                        text = message.username,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AksenMerah,
                        letterSpacing = 0.5.sp
                    )
                    if (message.is_admin == true) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(AksenMerah)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "ADMIN",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // ── Bubble message ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isOwnMessage) 12.dp else 2.dp,
                            topEnd = if (isOwnMessage) 2.dp else 12.dp,
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp
                        )
                    )
                    .background(
                        if (isOwnMessage) BubbleOwn
                        else BubbleOther
                    )
                    .then(
                        // border tipis untuk bubble orang lain
                        if (!isOwnMessage)
                            Modifier.border(
                                1.dp,
                                BorderOther,
                                RoundedCornerShape(
                                    topStart = 2.dp,
                                    topEnd = 12.dp,
                                    bottomStart = 12.dp,
                                    bottomEnd = 12.dp
                                )
                            )
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (onDelete != null) showDelete = true }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.message,
                    fontSize = 13.sp,
                    color = if (isOwnMessage) Color.White else TextMain,
                    lineHeight = 18.sp
                )
            }

            // ── Timestamp ─────────────────────────────────────────────────────
            Text(
                text = timeStr,
                fontSize = 9.sp,
                color = TextDim,
                modifier = Modifier.padding(horizontal = 2.dp, top = 2.dp)
            )
        }
    }

    // ── Dialog hapus ─────────────────────────────────────────────────────────
    if (showDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Color(0xFF141414),
            title = {
                Text(
                    "HAPUS PESAN",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = TextMain,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    "Pesan ini akan dihapus permanen.",
                    color = TextDim,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AksenMerah)
                        .clickable {
                            onDelete()
                            showDelete = false
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "HAPUS",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, DividerColor, RoundedCornerShape(4.dp))
                        .clickable { showDelete = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "BATAL",
                        fontWeight = FontWeight.Bold,
                        color = TextDim,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        )
    }
}
