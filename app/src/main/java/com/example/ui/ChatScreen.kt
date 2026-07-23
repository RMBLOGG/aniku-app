package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
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
import com.example.network.ChatPreview
import com.example.network.ClanChatMessage
import com.example.network.ProfileDto
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
// Beta - badge kosmetik doang, gak ada hak akses moderasi apapun di chat
internal val betaGradientColors = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6))
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

// Reuse ChatBubble/list logic Global buat Clan tab: field-nya identik,
// cuma ClanChatMessage punya tambahan clan_id yang gak dipakai buat render.
private fun ClanChatMessage.toChatMessage() = ChatMessage(
    id = id,
    user_id = user_id,
    username = username,
    avatar_url = avatar_url,
    role = role,
    is_admin = is_admin,
    custom_name_color = custom_name_color,
    user_number = user_number,
    season_level = season_level,
    message = message,
    created_at = created_at,
    reply_to_id = reply_to_id,
    reply_to_username = reply_to_username,
    reply_to_message = reply_to_message,
    image_url = image_url
)

@Composable
private fun DiscussionTabRow(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    friendUnreadCount: Int
) {
    val tabs = listOf("Global", "Clan", "Teman")
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = selectedTab == index,
                onClick = { onSelect(index) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        if (index == 2 && friendUnreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (friendUnreadCount > 9) "9+" else "$friendUnreadCount",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

// Banner kecil di atas clan chat - ngingetin & ngajak main quiz "Tebak Anime dari
// Poster". Cuma dimunculin kalau user emang udah punya clan (quiz wajib clan).
@Composable
private fun ClanQuizBanner(onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "chat_quiz_banner")
    val edgeGlow by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "edgeGlow"
    )
    val iconPulse by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconPulse"
    )
    val shimmer by infinite.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2200, delayMillis = 400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val arrowNudge by infinite.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrowNudge"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(Brush.verticalGradient(listOf(Color(0xFF14101F), Color(0xFF0A0812))))
            .drawWithContent {
                drawContent()
                // Garis neon berdenyut di tepi kiri, bertindak sebagai "power line"
                drawRect(
                    color = Color(0xFF2FE0FF).copy(alpha = edgeGlow),
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                )
                // Sapuan kilau
                val x = shimmer * size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent),
                        start = Offset(x - 100f, 0f),
                        end = Offset(x + 100f, size.height)
                    )
                )
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { scaleX = iconPulse; scaleY = iconPulse }
                .clip(CircleShape)
                .background(Color(0xFF1B1430))
                .border(1.dp, Color(0xFF2FE0FF).copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Theaters,
                contentDescription = null,
                tint = Color(0xFF2FE0FF),
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "TEBAK ANIME DARI POSTER",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 11.5.sp,
                letterSpacing = 0.4.sp
            )
            Text(
                "Jawab bener nambah XP kamu & clan-mu",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color(0xFF2FE0FF),
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { translationX = arrowNudge }
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
    val onlineCount by viewModel.onlineCount.collectAsState()
    val chatRoomEnabled by viewModel.remoteConfigManager.chatRoomEnabled.collectAsState()
    val chatImageUploadEnabled by viewModel.remoteConfigManager.chatImageUploadEnabled.collectAsState()
    val clanTagMap by viewModel.clanTagMap.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    // Snapshot list terakhir yang masih ada isinya. AnimatedVisibility tetap
    // ngerender content-nya pas lagi animasi keluar (fade out), padahal
    // typingUsers udah keburu kosong -> pake snapshot ini biar ga IndexOutOfBounds.
    var lastTypingUsers by remember { mutableStateOf<List<com.example.network.TypingStatus>>(emptyList()) }
    if (typingUsers.isNotEmpty()) lastTypingUsers = typingUsers
    val chatReads by viewModel.chatReads.collectAsState()

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

    // ── Tab: Global / Clan / Teman ── (HorizontalPager biar bisa di-swipe)
    val pagerState = rememberPagerState(pageCount = { 3 })
    val selectedTab = pagerState.currentPage

    // Clan tab state
    val myClanMembership by viewModel.myClanMembership.collectAsState()
    val myClanDetail by viewModel.myClanDetail.collectAsState()
    val clanChatMessagesRaw by viewModel.clanChatMessages.collectAsState()
    val clanChatMessages = remember(clanChatMessagesRaw) { clanChatMessagesRaw.map { it.toChatMessage() } }
    val isClanChatLoading by viewModel.isClanChatLoading.collectAsState()
    val clanChatError by viewModel.clanChatError.collectAsState()
    var clanInputText by remember { mutableStateOf("") }
    var clanReplyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val clanListState = rememberLazyListState()

    // Teman tab state (daftar DM 1-on-1 yang sudah ada, dipindah ke sini)
    val friendsList by viewModel.friendsList.collectAsState()
    val userDirectory by viewModel.userDirectory.collectAsState()
    val userChats by viewModel.userChats.collectAsState()
    val friendUnreadCount = remember(friendsList, userChats, currentUserId) {
        friendsList.count { fs ->
            val otherId = if (fs.requester_id == currentUserId) fs.addressee_id else fs.requester_id
            val chat = userChats.firstOrNull { it.otherUserId == otherId }
            chat != null && chat.lastSenderId.isNotBlank() && chat.lastSenderId != currentUserId && chat.lastMessageAt > chat.lastReadAt
        }
    }
    fun profileForFriend(userId: String): ProfileDto? = userDirectory.firstOrNull { it.id == userId }
    fun chatForFriend(otherId: String): ChatPreview? = userChats.firstOrNull { it.otherUserId == otherId }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingImageUri = it }
    }

    // Auto-poll setiap 5 detik -- berhenti total kalau chat_room_enabled
    // dimatiin dari Firebase Remote Config (kill-switch darurat kalau egress boros).
    // loadClanTagMap tetap jalan sekali di awal karena dipakai fitur lain (tag clan),
    // bukan bagian dari polling chat.
    LaunchedEffect(Unit) {
        viewModel.loadClanTagMap()
    }
    LaunchedEffect(chatRoomEnabled) {
        if (!chatRoomEnabled) return@LaunchedEffect
        viewModel.loadChatMessages()
        viewModel.markChatRead()
        while (chatRoomEnabled) {
            delay(5000)
            viewModel.loadChatMessages()
            viewModel.markChatRead()
        }
    }

    // Tab Clan: auto-poll pesan clan cuma pas tab-nya lagi kebuka & user punya clan
    LaunchedEffect(selectedTab, myClanMembership?.clan_id) {
        val clanId = myClanMembership?.clan_id
        if (selectedTab == 1 && clanId != null) {
            viewModel.loadClanChatMessages(clanId)
            while (true) {
                delay(5000)
                viewModel.loadClanChatMessages(clanId)
            }
        }
    }

    // Tab Teman: muat daftar pertemanan pas tab-nya dibuka
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            viewModel.loadFriendships()
            viewModel.loadUserDirectory()
        }
    }

    // Typing indicator & read receipt: mulai polling pas chat dibuka, stop pas keluar
    DisposableEffect(Unit) {
        viewModel.startTypingPolling()
        viewModel.startChatReadsPolling()
        onDispose {
            viewModel.stopTypingPolling()
            viewModel.stopChatReadsPolling()
            if (isLoggedIn) viewModel.clearTyping()
        }
    }

    // Auto-scroll ke bawah saat ada pesan baru + update read receipt ke pesan terakhir
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialLoad) {
                // Load pertama: langsung snap ke bawah, tanpa animasi
                listState.scrollToItem(messages.size - 1)
                isInitialLoad = false
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
            if (isLoggedIn) viewModel.markChatReadReceipt(messages.last().id)
        }
    }

    // Tampilkan error via snackbar
    LaunchedEffect(chatError) {
        chatError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearChatError()
        }
    }

    LaunchedEffect(clanChatError) {
        clanChatError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearClanChatError()
        }
    }

    LaunchedEffect(clanChatMessages.size) {
        if (clanChatMessages.isNotEmpty() && selectedTab == 1) {
            clanListState.animateScrollToItem(clanChatMessages.size - 1)
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
                                                color = Color(0xFF4CAF50),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        if (onlineCount > 0) "$onlineCount user online" else "Menghitung user online...",
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
                DiscussionTabRow(
                    selectedTab = selectedTab,
                    onSelect = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    friendUnreadCount = friendUnreadCount
                )
                AnimatedVisibility(
                    visible = typingUsers.isNotEmpty() && selectedTab == 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dotProgress = rememberGlossyShimmer(durationMillis = 900)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(3) { i ->
                                val alpha = (kotlin.math.sin((dotProgress * 6.28f) - i * 1.2f) + 1f) / 2f
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            Color(0xFFFF6B6B).copy(alpha = 0.35f + alpha * 0.65f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                        val typingText = when (lastTypingUsers.size) {
                            1 -> "${lastTypingUsers[0].username} sedang mengetik..."
                            2 -> "${lastTypingUsers[0].username} dan ${lastTypingUsers[1].username} sedang mengetik..."
                            else -> "${lastTypingUsers.getOrNull(0)?.username ?: ""} dan ${(lastTypingUsers.size - 1).coerceAtLeast(0)} lainnya sedang mengetik..."
                        }
                        Text(
                            text = typingText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }
        },
        bottomBar = {
            when (selectedTab) {
                0 -> {
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
                            onValueChange = {
                                if (it.length <= 300) inputText = it
                                if (it.isNotBlank()) viewModel.notifyTyping()
                            },
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
                                    viewModel.clearTyping()
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
                1 -> {
                    val clanId = myClanMembership?.clan_id
                    if (clanId != null) {
                        Column {
                            Divider()
                            AnimatedVisibility(
                                visible = clanReplyTarget != null,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut()
                            ) {
                                clanReplyTarget?.let { target ->
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
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(target.username, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                            Text(target.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { clanReplyTarget = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Batal reply", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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
                                    OutlinedTextField(
                                        value = clanInputText,
                                        onValueChange = { if (it.length <= 300) clanInputText = it },
                                        placeholder = { Text(if (clanReplyTarget != null) "Balas ${clanReplyTarget!!.username}..." else "Ketik pesan ke clan...") },
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
                                            val msg = clanInputText.trim()
                                            if (msg.isNotEmpty()) {
                                                viewModel.sendClanChatMessage(
                                                    clanId = clanId,
                                                    message = msg,
                                                    replyToId = clanReplyTarget?.id,
                                                    replyToUsername = clanReplyTarget?.username,
                                                    replyToMessage = clanReplyTarget?.message
                                                )
                                                clanInputText = ""
                                                clanReplyTarget = null
                                            }
                                        },
                                        enabled = clanInputText.trim().isNotEmpty(),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Kirim", modifier = Modifier.size(20.dp))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Login untuk ikut chat clan", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Tab Teman: cuma daftar DM, ga ada input bar di sini - kirim pesan
                    // dilakuin di PrivateChatScreen pas tap salah satu teman.
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
            when (page) {
                0 -> {
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
                                viewModel = viewModel,
                                onReply = if (isLoggedIn) { { replyTarget = message } } else null,
                                onDelete = if (isOwn || session.canModerate()) {
                                    { viewModel.deleteChatMessage(message.id) }
                                } else null
                            )
                            val readers = remember(chatReads, message.id, currentUserId) {
                                chatReads.filter {
                                    it.last_read_message_id == message.id && it.user_id != currentUserId
                                }
                            }
                            if (readers.isNotEmpty()) {
                                ReadReceiptRow(readers = readers, isOwnMessage = isOwn)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }
                }
            }
                }
                1 -> {
                    val clanId = myClanMembership?.clan_id
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (clanId != null) {
                            ClanQuizBanner(onClick = { navController.navigate("quiz") })
                        }
                        Box(modifier = Modifier.weight(1f)) {
                    when {
                        clanId == null -> {
                            EmptyState(
                                Icons.Default.Shield,
                                "Kamu belum join clan.\nJoin atau bikin clan dulu buat bisa chat di sini!"
                            )
                        }
                        isClanChatLoading && clanChatMessages.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        clanChatMessages.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("\uD83D\uDEE1\uFE0F", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Belum ada obrolan di clan${myClanDetail?.name?.let { " $it" } ?: ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        if (isLoggedIn) "Jadilah yang pertama chat di clan!" else "Login untuk mulai chat",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = clanListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(clanChatMessages, key = { it.id }) { message ->
                                    val isOwn = message.user_id == currentUserId
                                    ChatBubble(
                                        message = message,
                                        isOwnMessage = isOwn,
                                        navController = navController,
                                        clanTagMap = clanTagMap,
                                        viewModel = viewModel,
                                        onReply = if (isLoggedIn) { { clanReplyTarget = message } } else null,
                                        onDelete = if (isOwn || session.canModerate()) {
                                            { viewModel.deleteClanChatMessage(message.id) }
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
                else -> {
                    // Tab Teman: daftar DM 1-on-1, tap buka PrivateChatScreen
                    if (friendsList.isEmpty()) {
                        EmptyState(Icons.Default.People, "Belum ada teman.\nCari user lain terus kirim permintaan pertemanan!")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(friendsList, key = { it.id ?: "" }) { fs ->
                                val otherId = if (fs.requester_id == currentUserId) fs.addressee_id else fs.requester_id
                                val profile = profileForFriend(otherId)
                                val chat = chatForFriend(otherId)
                                val isUnread = chat != null && chat.lastSenderId.isNotBlank() &&
                                    chat.lastSenderId != currentUserId && chat.lastMessageAt > chat.lastReadAt
                                FriendRow(
                                    profile = profile,
                                    fallbackId = otherId,
                                    chat = chat,
                                    myId = currentUserId,
                                    isUnread = isUnread,
                                    onClick = { navController.navigate("private_chat/$otherId") },
                                    trailing = {}
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

/**
 * Baris avatar kecil ber-stack (kayak Discord/Slack) yang muncul di bawah pesan,
 * nunjukin user mana aja yang last-read-nya udah nyampe pesan ini.
 */
@Composable
private fun ReadReceiptRow(
    readers: List<com.example.network.ChatReadStatus>,
    isOwnMessage: Boolean
) {
    val shown = readers.take(5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isOwnMessage) 0.dp else 8.dp,
                end = if (isOwnMessage) 8.dp else 0.dp,
                top = 1.dp,
                bottom = 2.dp
            ),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Row {
            shown.forEachIndexed { index, reader ->
                Box(
                    modifier = Modifier
                        .offset(x = (-6 * index).dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .zIndex((shown.size - index).toFloat())
                ) {
                    if (!reader.avatar_url.isNullOrBlank()) {
                        AsyncImage(
                            model = reader.avatar_url,
                            contentDescription = reader.username,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(defaultNameGradientColors.first()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = reader.username.take(1).uppercase(),
                                fontSize = 7.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
    viewModel: com.example.network.AnikuViewModel,
    onReply: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    // Pesan giveaway "War Premium" dirender pakai bubble khusus (ada tombol
    // Klaim), bukan bubble teks biasa.
    if (message.message_type == "giveaway") {
        GiveawayBubble(message = message, viewModel = viewModel)
        return
    }

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
    val customColorParsed = remember(message.custom_name_color) {
        message.custom_name_color?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
        }
    }
    val nameColor = when {
        customColorParsed != null -> customColorParsed
        message.role == "admin" || message.is_admin == true -> Color(0xFFFF6B6B)
        message.role == "moderator" -> Color(0xFFB388FF)
        message.role == "beta" -> Color(0xFF22D3EE)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val nameGradient = when {
        customColorParsed != null -> null
        message.role == "admin" || message.is_admin == true ->
            Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)))
        message.role == "moderator" ->
            Brush.linearGradient(listOf(Color(0xFFB388FF), Color(0xFF7C4DFF)))
        message.role == "beta" ->
            Brush.linearGradient(betaGradientColors)
        else -> null
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val dragModifier = Modifier
        .fillMaxWidth()
        .pointerInput(onReply) {
            // Deteksi arah drag secara manual: baru dianggap "swipe reply" (horizontal)
            // kalau pergerakan dx udah jelas lebih dominan dibanding dy. Sebelum arah
            // kekunci, event *tidak* di-consume, jadi LazyColumn di atasnya tetap bisa
            // nangkep drag vertikal buat scroll walau jari nyentuh di tengah bubble
            // (sebelumnya detectHorizontalDragGestures selalu nangkep dx duluan,
            // makanya scroll cuma jalan kalau jari bener-bener di pinggir layar).
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var lockedHorizontal: Boolean? = null
                var accDx = 0f
                var accDy = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed()) {
                        if (lockedHorizontal == true) {
                            if (swipeOffset > swipeThreshold && onReply != null) {
                                onReply()
                            }
                        }
                        swipeOffset = 0f
                        break
                    }
                    val pos = change.positionChange()
                    if (lockedHorizontal == null) {
                        accDx += pos.x
                        accDy += pos.y
                        val absDx = kotlin.math.abs(accDx)
                        val absDy = kotlin.math.abs(accDy)
                        if (absDx > touchSlop || absDy > touchSlop) {
                            lockedHorizontal = absDx > absDy * 1.5f
                            if (lockedHorizontal == false) {
                                // Gerakan ini vertikal (scroll) - jangan consume,
                                // biarin LazyColumn di parent yang nangkep.
                                swipeOffset = 0f
                                break
                            }
                        }
                    }
                    if (lockedHorizontal == true) {
                        change.consume()
                        val delta = if (isOwnMessage) -pos.x else pos.x
                        if (delta > 0) {
                            swipeOffset = (swipeOffset + delta).coerceAtMost(swipeThreshold * 1.2f)
                        }
                    }
                }
            }
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
        // Avatar user Beta dibungkus cincin gradient muter - efek eksklusif kosmetik.
        val avatarContent: @Composable () -> Unit = {
            if (!message.avatar_url.isNullOrEmpty()) {
                AsyncImage(
                    model = message.avatar_url,
                    contentDescription = message.username,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { navController.navigate("user_profile/${message.user_id}") },
                    contentScale = ContentScale.Crop,
                    error = null,
                    fallback = null,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
        if (message.role == "beta") {
            BetaAvatarRing(size = 30.dp) { avatarContent() }
        } else {
            Box(modifier = Modifier.size(30.dp)) { avatarContent() }
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
                        customColorParsed != null -> listOf(customColorParsed, customColorParsed)
                        message.role == "admin" || message.is_admin == true -> adminGradientColors
                        message.role == "moderator" -> moderatorGradientColors
                        message.role == "beta" -> betaGradientColors
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
                } else if (message.role == "beta") {
                    GlossyGradientText(
                        text = "BETA",
                        colors = betaGradientColors,
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

// Bubble khusus buat pesan giveaway "War Premium" - beda dari bubble teks
// biasa, ada tombol "Klaim Sekarang" yang manggil RPC claim_giveaway (atomic
// di server, jadi cuma 1 orang yang bisa menang walau banyak yang tap bareng).
@Composable
private fun GiveawayBubble(
    message: ChatMessage,
    viewModel: com.example.network.AnikuViewModel
) {
    var isClaimed by remember(message.giveaway_status) {
        mutableStateOf(message.giveaway_status == "claimed")
    }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFD54F), Color(0xFFFF8A65))
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🎁", fontSize = 32.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (isClaimed) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = resultText ?: "Sudah diklaim",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = {
                        if (isLoading) return@Button
                        val claimId = message.giveaway_claim_id ?: return@Button
                        isLoading = true
                        viewModel.claimGiveaway(claimId) { result, error ->
                            isLoading = false
                            isClaimed = true
                            resultText = if (result != null && result.success) {
                                val left = result.slots_left
                                if (left != null && left > 0) {
                                    "Kamu dapat Premium ${result.package_label ?: ""}! (Sisa $left slot)"
                                } else {
                                    "Kamu dapat Premium ${result.package_label ?: ""}!"
                                }
                            } else {
                                result?.message ?: error ?: "Giveaway sudah diklaim orang lain"
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFFF6F00)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFFF6F00)
                        )
                    } else {
                        Text("Klaim Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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

    val customColorParsed = remember(message.custom_name_color) {
        message.custom_name_color?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
        }
    }

    // Efek partikel cuma muncul kalau pesan ini baru banget dikirim (<5 detik lalu) - biar
    // gak nge-replay animasi tiap kali chat di-scroll/reload buat pesan-pesan lama.
    val isRecentMessage = remember(message.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val sentAt = parser.parse(message.created_at.take(19))?.time ?: 0L
            (System.currentTimeMillis() - sentAt) < 5000L
        } catch (e: Exception) {
            false
        }
    }

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
                } else if (message.role == "beta") {
                    GlossyGradientText(
                        text = "BETA",
                        colors = betaGradientColors,
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
                        customColorParsed != null -> listOf(customColorParsed, customColorParsed)
                        message.role == "admin" || message.is_admin == true -> adminGradientColors
                        message.role == "moderator" -> moderatorGradientColors
                        message.role == "beta" -> betaGradientColors
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

        val ownAvatarContent: @Composable () -> Unit = {
            if (!message.avatar_url.isNullOrEmpty()) {
                AsyncImage(
                    model = message.avatar_url,
                    contentDescription = message.username,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { navController.navigate("user_profile/${message.user_id}") },
                    contentScale = ContentScale.Crop,
                    error = null,
                    fallback = null,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
        if (message.role == "beta") {
            Box {
                BetaAvatarRing(size = 30.dp) { ownAvatarContent() }
                if (isRecentMessage) {
                    MessageSendBurst(key = message.id, modifier = Modifier.matchParentSize())
                }
            }
        } else {
            Box(modifier = Modifier.size(30.dp)) { ownAvatarContent() }
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
