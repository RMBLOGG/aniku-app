package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.regex.Pattern

// Regex sederhana buat deteksi URL (http/https/www) di dalam teks pesan chat.
// Trailing punctuation umum (.,!?)]}>"') tidak ikut dianggap bagian dari URL,
// supaya kalimat seperti "cek aniku.id, makasih" tidak ikut nge-link tanda komanya.
private val urlPattern: Pattern = Pattern.compile(
    "(https?://[^\\s]+|www\\.[^\\s]+?)(?=[.,!?)\\]}>\"']*(?:\\s|$))",
    Pattern.CASE_INSENSITIVE
)

/**
 * Pecah teks pesan jadi AnnotatedString dengan bagian URL dijadikan link
 * yang bisa di-tap (warna beda + underline), sisanya tetap teks biasa.
 */
private fun linkifyMessage(
    text: String,
    linkColor: Color
): androidx.compose.ui.text.AnnotatedString {
    val matcher = urlPattern.matcher(text)
    return buildAnnotatedString {
        var lastEnd = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            // Teks biasa sebelum URL
            append(text.substring(lastEnd, start))

            val rawUrl = text.substring(start, end)
            val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl
            } else {
                "https://$rawUrl"
            }

            withLink(
                LinkAnnotation.Url(
                    url = fullUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                )
            ) {
                append(rawUrl)
            }
            lastEnd = end
        }
        // Sisa teks setelah URL terakhir (atau semua teks kalau ga ada URL)
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

/**
 * Animasi posisi kilau (shimmer) yang bergerak loop dari kiri ke kanan terus-menerus.
 * Dipakai untuk efek glossy di teks nama/id/role chat.
 */
@Composable
internal fun rememberGlossyShimmer(durationMillis: Int = 2200): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "glossyShimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    return progress
}

/**
 * Brush gradient dasar [baseColors] yang dikasih highlight putih bergerak
 * sesuai [progress], jadi keliatan kayak kilau kaca/glossy yang lewat terus.
 */
internal fun glossyBrush(baseColors: List<Color>, progress: Float): Brush {
    val c0 = baseColors.first()
    val c1 = baseColors.last()
    val band = 0.22f
    val p0 = (progress - band).coerceIn(0f, 1f)
    val p1 = progress.coerceIn(0f, 1f)
    val p2 = (progress + band).coerceIn(0f, 1f)
    val highlight = Color.White.copy(alpha = 0.95f)
    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to c0,
            p0 to c0,
            p1 to highlight,
            p2 to c1,
            1f to c1
        )
    )
}

/**
 * Teks dengan warna gradient + animasi glossy/kilau bergerak.
 * Dipakai buat nama pengirim, id (#angka), dan badge role di chat.
 */
@Composable
internal fun GlossyGradientText(
    text: String,
    colors: List<Color>,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier
) {
    val progress = rememberGlossyShimmer()
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        modifier = modifier,
        style = LocalTextStyle.current.copy(brush = glossyBrush(colors, progress))
    )
}

// Set warna gradient khusus per role, dipakai bareng GlossyGradientText
internal val adminGradientColors = listOf(Color(0xFFFFD200), Color(0xFFFF6B6B), Color(0xFFFF8E53))
internal val moderatorGradientColors = listOf(Color(0xFFB388FF), Color(0xFF7C4DFF))
internal val defaultNameGradientColors = listOf(Color(0xFF64B5F6), Color(0xFFBA68C8))
internal val idGradientColors = listOf(Color(0xFFCFD8DC), Color(0xFF90A4AE))
internal val levelGradientColors = listOf(Color(0xFF4FD1C5), Color(0xFF38B2AC))
internal val clanTagGradientColors = listOf(Color(0xFF7B2FBF), Color(0xFF2FA8BF))

// Badge tag clan di chat, misal [COC], pakai icon perisai bukan emoji
@Composable
internal fun ClanTagBadge(tag: String) {
    val progress = rememberGlossyShimmer()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Shield,
            contentDescription = "Clan",
            modifier = Modifier.size(11.dp),
            tint = Color(0xFF2FA8BF)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "[$tag]",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            style = LocalTextStyle.current.copy(brush = glossyBrush(clanTagGradientColors, progress))
        )
    }
}

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
    val chatNotifEnabled by viewModel.chatNotifEnabled.collectAsState()
    val chatRoomEnabled by viewModel.remoteConfigManager.chatRoomEnabled.collectAsState()
    val chatImageUploadEnabled by viewModel.remoteConfigManager.chatImageUploadEnabled.collectAsState()
    val clanTagMap by viewModel.clanTagMap.collectAsState()

    val isLoggedIn = !session.token.isNullOrEmpty()
    val currentUserId = session.userId
    val isSendingImage by viewModel.isSendingImage.collectAsState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isInitialLoad by remember { mutableStateOf(true) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingImageUri = it }
    }

    // Auto-poll setiap 5 detik
    LaunchedEffect(Unit) {
        viewModel.loadChatMessages()
        viewModel.markChatRead()
        viewModel.loadClanTagMap()
        while (true) {
            delay(5000)
            viewModel.loadChatMessages()
            viewModel.markChatRead()
        }
    }

    // Auto-scroll ke bawah saat ada pesan baru
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialLoad) {
                // Load pertama: langsung snap ke bawah, tanpa animasi
                listState.scrollToItem(messages.size - 1)
                isInitialLoad = false
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
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
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    "Chat Room",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                color = if (isLoggedIn) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        if (isLoggedIn) "Online sebagai ${session.username}" else "Mode tamu \u2014 hanya lihat",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleChatNotif() }) {
                            Icon(
                                if (chatNotifEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = if (chatNotifEnabled) "Matikan notifikasi chat" else "Nyalakan notifikasi chat",
                                tint = if (chatNotifEnabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = { viewModel.loadChatMessages() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
        bottomBar = {
            if (chatRoomEnabled) {
            Column {
                Divider()
                // Image preview bar
                AnimatedVisibility(
                    visible = pendingImageUri != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    pendingImageUri?.let { uri ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Foto siap dikirim",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            IconButton(onClick = { pendingImageUri = null }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Batalkan foto",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
                // Reply preview bar
                AnimatedVisibility(
                    visible = replyTarget != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    replyTarget?.let { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.username,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = target.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyTarget = null }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Batal reply",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                if (isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol pilih foto
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            enabled = !isSendingImage && chatImageUploadEnabled
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Pilih foto",
                                tint = if (chatImageUploadEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { if (it.length <= 300) inputText = it },
                            placeholder = {
                                Text(if (replyTarget != null) "Balas ${replyTarget!!.username}..." else "Ketik pesan...")
                            },
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
                                val imgUri = pendingImageUri
                                if (msg.isNotEmpty() || imgUri != null) {
                                    if (imgUri != null) {
                                        // Upload foto dulu, lalu kirim pesan
                                        viewModel.uploadChatImage(context, imgUri) { imageUrl ->
                                            viewModel.sendChatMessage(
                                                message = msg,
                                                replyToId = replyTarget?.id,
                                                replyToUsername = replyTarget?.username,
                                                replyToMessage = replyTarget?.message,
                                                imageUrl = imageUrl
                                            )
                                        }
                                        pendingImageUri = null
                                    } else {
                                        viewModel.sendChatMessage(
                                            message = msg,
                                            replyToId = replyTarget?.id,
                                            replyToUsername = replyTarget?.username,
                                            replyToMessage = replyTarget?.message
                                        )
                                    }
                                    inputText = ""
                                    replyTarget = null
                                    coroutineScope.launch {
                                        delay(300)
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                    }
                                }
                            },
                            enabled = (inputText.trim().isNotEmpty() || pendingImageUri != null) && !isSendingImage,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (isSendingImage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Kirim",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !chatRoomEnabled -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Chat Room sedang maintenance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Fitur ini lagi dinonaktifkan sementara. Coba lagi nanti ya!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                isChatLoading && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDCAC", fontSize = 48.sp)
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
                                navController = navController,
                                clanTagMap = clanTagMap,
                                onReply = if (isLoggedIn) { { replyTarget = message } } else null,
                                onDelete = if (isOwn || session.canModerate()) {
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    navController: NavController,
    clanTagMap: Map<String, Pair<String, String?>>,
    onReply: (() -> Unit)?,
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
        } catch (e: Exception) {
            "--:--"
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var swipeOffset by remember { mutableStateOf(0f) }
    val swipeThreshold = 80f

    // Warna nama berdasarkan role: admin = merah, moderator = ungu, lainnya = putih/abu
    val nameColor = when {
        message.role == "admin" || message.is_admin == true -> Color(0xFFFF6B6B)
        message.role == "moderator" -> Color(0xFFB388FF)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val nameGradient = when {
        message.role == "admin" || message.is_admin == true ->
            Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)))
        message.role == "moderator" ->
            Brush.linearGradient(listOf(Color(0xFFB388FF), Color(0xFF7C4DFF)))
        else -> null
    }

    val dragModifier = Modifier
        .fillMaxWidth()
        .pointerInput(onReply) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (swipeOffset > swipeThreshold && onReply != null) {
                        onReply()
                    }
                    swipeOffset = 0f
                },
                onDragCancel = { swipeOffset = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    val delta = if (isOwnMessage) -dragAmount else dragAmount
                    if (delta > 0) {
                        swipeOffset = (swipeOffset + delta).coerceAtMost(swipeThreshold * 1.2f)
                    }
                }
            )
        }
        .combinedClickable(
            onClick = {},
            onLongClick = { if (onDelete != null) showDeleteDialog = true }
        )
        .padding(horizontal = 12.dp, vertical = 2.dp)

    if (isOwnMessage) {
        OwnChatBubble(
            message = message,
            timeStr = timeStr,
            navController = navController,
            clanTagMap = clanTagMap,
            modifier = dragModifier
        )
    } else {
    Row(
        modifier = dragModifier,
        horizontalArrangement = Arrangement.Start
    ) {
        // Avatar selalu di kiri (flat layout, gaya AniKme)
        if (!message.avatar_url.isNullOrEmpty()) {
            AsyncImage(
                model = message.avatar_url,
                contentDescription = message.username,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { navController.navigate("user_profile/${message.user_id}") },
                contentScale = ContentScale.Crop,
                error = null,
                fallback = null,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .clickable { navController.navigate("user_profile/${message.user_id}") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.username.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                GlossyGradientText(
                    text = message.username,
                    colors = when {
                        message.role == "admin" || message.is_admin == true -> adminGradientColors
                        message.role == "moderator" -> moderatorGradientColors
                        else -> defaultNameGradientColors
                    },
                    fontSize = 13.sp
                )
                message.season_level?.let { lvl ->
                    GlossyGradientText(
                        text = "Lv.$lvl",
                        colors = levelGradientColors,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                message.user_number?.let { num ->
                    GlossyGradientText(
                        text = "#$num",
                        colors = idGradientColors,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                clanTagMap[message.user_id]?.let { (tag, _) -> ClanTagBadge(tag) }
                if (message.role == "admin" || message.is_admin == true) {
                    GlossyGradientText(
                        text = "ADMIN",
                        colors = adminGradientColors,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp
                    )
                } else if (message.role == "moderator") {
                    GlossyGradientText(
                        text = "MODERATOR",
                        colors = moderatorGradientColors,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bubble bulat buat isi pesan (reply preview, gambar, teks) - gaya kayak own bubble tapi neutral
            val otherBubbleShape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
            Column(
                modifier = Modifier
                    .clip(otherBubbleShape)
                    .background(Color(0xFF2A2A2E).copy(alpha = 0.75f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {

            // Quoted reply preview
            if (!message.reply_to_message.isNullOrEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(22.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Text(
                            text = message.reply_to_username ?: "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message.reply_to_message ?: "",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Gambar jika ada
            var showFullImage by remember { mutableStateOf(false) }
            if (!message.image_url.isNullOrEmpty()) {
                AsyncImage(
                    model = message.image_url,
                    contentDescription = "Foto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFullImage = true },
                    contentScale = ContentScale.FillWidth
                )
                if (showFullImage) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showFullImage = false }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.95f))
                                .clickable { showFullImage = false },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = message.image_url,
                                contentDescription = "Foto penuh",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentScale = ContentScale.FillWidth
                            )
                            IconButton(
                                onClick = { showFullImage = false },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
                if (message.message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            if (message.message.isNotEmpty()) {
                Text(
                    text = linkifyMessage(message.message, MaterialTheme.colorScheme.primary),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )
            }
            } // tutup Column bubble bulat

            Text(
                text = timeStr,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
    }

    // Dialog konfirmasi hapus (long press)
    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus pesan?") },
            text = { Text("Pesan ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun OwnChatBubble(
    message: ChatMessage,
    timeStr: String,
    navController: NavController,
    clanTagMap: Map<String, Pair<String, String?>>,
    modifier: Modifier = Modifier
) {
    var showFullImage by remember { mutableStateOf(false) }

    // Shape khas futuristik: sudut tajam di tiga sisi, satu sudut "ekor" kecil
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 4.dp
    )

    // Latar bubble: glass transparan netral (bukan warna merah/primary)
    val bubbleBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.03f)
        )
    )

    // Outline tipis yang gerak, pakai netral (putih/abu) bukan warna merah
    val borderShimmer = rememberGlossyShimmer(durationMillis = 3000)
    val borderBrush = glossyBrush(
        baseColors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f)),
        progress = borderShimmer
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Nama, id, role - rata kanan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (message.role == "admin" || message.is_admin == true) {
                    GlossyGradientText(
                        text = "ADMIN",
                        colors = adminGradientColors,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp
                    )
                } else if (message.role == "moderator") {
                    GlossyGradientText(
                        text = "MODERATOR",
                        colors = moderatorGradientColors,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp
                    )
                }
                message.season_level?.let { lvl ->
                    GlossyGradientText(
                        text = "Lv.$lvl",
                        colors = levelGradientColors,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                message.user_number?.let { num ->
                    GlossyGradientText(
                        text = "#$num",
                        colors = idGradientColors,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                clanTagMap[message.user_id]?.let { (tag, _) -> ClanTagBadge(tag) }
                GlossyGradientText(
                    text = message.username,
                    colors = when {
                        message.role == "admin" || message.is_admin == true -> adminGradientColors
                        message.role == "moderator" -> moderatorGradientColors
                        else -> defaultNameGradientColors
                    },
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(1.dp))

            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .shadow(
                        elevation = 8.dp,
                        shape = bubbleShape,
                        ambientColor = Color.Black.copy(alpha = 0.4f),
                        spotColor = Color.White.copy(alpha = 0.18f)
                    )
                    .clip(bubbleShape)
                    .background(bubbleBrush)
                    .border(1.2.dp, borderBrush, bubbleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Strip kilau halus di atas bubble (kesan kaca)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )
                Spacer(modifier = Modifier.height(5.dp))

                // Quoted reply preview
                if (!message.reply_to_message.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column {
                            Text(
                                text = message.reply_to_username ?: "",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message.reply_to_message ?: "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Gambar jika ada
                if (!message.image_url.isNullOrEmpty()) {
                    AsyncImage(
                        model = message.image_url,
                        contentDescription = "Foto",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFullImage = true },
                        contentScale = ContentScale.FillWidth
                    )
                    if (message.message.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (message.message.isNotEmpty()) {
                    Text(
                        text = linkifyMessage(message.message, MaterialTheme.colorScheme.primary),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = timeStr,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 2.dp, end = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (!message.avatar_url.isNullOrEmpty()) {
            AsyncImage(
                model = message.avatar_url,
                contentDescription = message.username,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { navController.navigate("user_profile/${message.user_id}") },
                contentScale = ContentScale.Crop,
                error = null,
                fallback = null,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .clickable { navController.navigate("user_profile/${message.user_id}") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.username.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showFullImage && !message.image_url.isNullOrEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullImage = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = message.image_url,
                    contentDescription = "Foto penuh",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.FillWidth
                )
                IconButton(
                    onClick = { showFullImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
