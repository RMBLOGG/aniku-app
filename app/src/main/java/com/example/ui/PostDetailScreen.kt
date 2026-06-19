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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.Post
import com.example.network.PostComment
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val session by viewModel.session.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val postLikes by viewModel.postLikes.collectAsState()
    val postComments by viewModel.postComments.collectAsState()
    val feedError by viewModel.feedError.collectAsState()

    val post = posts.find { it.id == postId }
    val likes = postLikes[postId] ?: emptyList()
    // Build tree: top-level + replies keyed by parent id
    val allComments = postComments[postId] ?: emptyList()
    val topLevel = allComments.filter { it.reply_to_id == null }
    val repliesMap = allComments.filter { it.reply_to_id != null }.groupBy { it.reply_to_id }

    val isLoggedIn = !session.token.isNullOrEmpty()
    val isLiked = !session.userId.isNullOrEmpty() && session.userId in likes

    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var showFullImage by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadComments(postId) }

    LaunchedEffect(feedError) {
        feedError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Postingan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

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
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Membalas ${target.username}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = target.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyTarget = null }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Batal",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
                        AvatarCircle(
                            avatarUrl = session.avatarUrl,
                            username = session.username ?: "A",
                            size = 32.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { if (it.length <= 300) inputText = it },
                            placeholder = {
                                Text(
                                    if (replyTarget != null) "Balas ${replyTarget!!.username}..."
                                    else "Tulis komentar...",
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                val msg = inputText.trim()
                                if (msg.isNotEmpty()) {
                                    viewModel.addComment(
                                        postId = postId,
                                        message = msg,
                                        replyToId = replyTarget?.id,
                                        replyToUsername = replyTarget?.username
                                    )
                                    inputText = ""
                                    replyTarget = null
                                }
                            },
                            enabled = inputText.trim().isNotEmpty(),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Kirim", modifier = Modifier.size(18.dp))
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
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Masuk untuk berkomentar",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (post == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Post header
            item {
                PostHeader(
                    post = post,
                    likeCount = likes.size,
                    commentCount = allComments.size,
                    isLiked = isLiked,
                    onLike = { viewModel.toggleLike(postId) },
                    onShowFullImage = { showFullImage = true },
                    onOpenAnime = { slug -> onNavigateToDetail(slug) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    thickness = 4.dp
                )
            }

            // Section komentar
            item {
                Text(
                    text = if (allComments.isEmpty()) "Komentar" else "Komentar  •  ${allComments.size}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 0.sp
                )
            }

            if (allComments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Text(
                                "Belum ada komentar",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                // Top-level komentar beserta reply-nya masing-masing
                items(topLevel, key = { it.id }) { comment ->
                    val canDelete = comment.user_id == session.userId || session.isAdmin
                    val replies = repliesMap[comment.id] ?: emptyList()
                    var expanded by remember { mutableStateOf(false) }

                    // Komentar utama
                    CommentRow(
                        comment = comment,
                        isReply = false,
                        canDelete = canDelete,
                        onReply = if (isLoggedIn) { { replyTarget = comment } } else null,
                        onDelete = { viewModel.deleteComment(postId, comment.id) }
                    )

                    // Reply block (Facebook style)
                    if (replies.isNotEmpty()) {
                        Column(modifier = Modifier.padding(start = 52.dp)) {
                            // Selalu tampilkan reply pertama
                            val visibleReplies = if (expanded || replies.size <= 1) replies else replies.take(1)

                            visibleReplies.forEach { reply ->
                                val canDeleteReply = reply.user_id == session.userId || session.isAdmin
                                CommentRow(
                                    comment = reply,
                                    isReply = true,
                                    canDelete = canDeleteReply,
                                    onReply = if (isLoggedIn) { { replyTarget = comment } } else null,
                                    onDelete = { viewModel.deleteComment(postId, reply.id) }
                                )
                            }

                            // Tombol "Lihat X balasan lainnya"
                            if (!expanded && replies.size > 1) {
                                val remaining = replies.size - 1
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = true }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Garis horizontal kecil seperti FB
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(1.5.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Lihat $remaining balasan lainnya",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                            }

                            // Tombol sembunyikan
                            if (expanded && replies.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = false }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(1.5.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sembunyikan balasan",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }
        }
    }

    if (showFullImage && !post?.image_url.isNullOrEmpty()) {
        FullScreenImage(url = post!!.image_url!!, onDismiss = { showFullImage = false })
    }
}

@Composable
private fun PostHeader(
    post: Post,
    likeCount: Int,
    commentCount: Int,
    isLiked: Boolean,
    onLike: () -> Unit,
    onShowFullImage: () -> Unit,
    onOpenAnime: (String) -> Unit
) {
    val timeStr = remember(post.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(post.created_at.take(19)) ?: Date()
            val fmt = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("id"))
            fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
            fmt.format(date)
        } catch (e: Exception) { "" }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(avatarUrl = post.avatar_url, username = post.username, size = 42.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.sp
                    )
                    if (post.is_admin == true) {
                        Spacer(modifier = Modifier.width(5.dp))
                        AdminBadge()
                    }
                }
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        if (!post.caption.isNullOrEmpty()) {
            Text(
                text = linkifyText(post.caption),
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = if (post.image_url.isNullOrEmpty()) 12.dp else 8.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!post.image_url.isNullOrEmpty()) {
            AsyncImage(
                model = post.image_url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 460.dp)
                    .clickable { onShowFullImage() },
                contentScale = ContentScale.FillWidth
            )
        }

        // Shared anime
        if (!post.anime_slug.isNullOrEmpty() && !post.anime_title.isNullOrEmpty() && !post.anime_poster.isNullOrEmpty()) {
            SharedAnimeCard(
                title = post.anime_title,
                poster = post.anime_poster,
                type = post.anime_type,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 8.dp),
                onClick = { onOpenAnime(post.anime_slug) }
            )
        }

        // Like + comment count summary (mirip FB)
        if (likeCount > 0 || commentCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (likeCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = likeCount.toString(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else Spacer(modifier = Modifier.size(1.dp))

                if (commentCount > 0) {
                    Text(
                        text = "$commentCount komentar",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            ActionButton(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Suka",
                tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                onClick = onLike
            )
            ActionButton(
                icon = Icons.Default.ChatBubbleOutline,
                label = "Komentar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                onClick = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: PostComment,
    isReply: Boolean,
    canDelete: Boolean,
    onReply: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val timeStr = remember(comment.created_at) { relativeTime(comment.created_at) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { if (canDelete) showDeleteDialog = true }
            )
            .padding(
                start = if (isReply) 10.dp else 14.dp,
                end = 14.dp,
                top = 8.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        AvatarCircle(
            avatarUrl = comment.avatar_url,
            username = comment.username,
            size = if (isReply) 28.dp else 34.dp
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Bubble komentar (mirip FB)
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.username,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isReply) 12.sp else 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.sp
                        )
                        if (!comment.reply_to_username.isNullOrEmpty()) {
                            Text(
                                text = " -> ${comment.reply_to_username}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = comment.message,
                        fontSize = if (isReply) 13.sp else 14.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                }
            }

            // Waktu + tombol balas
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                if (onReply != null) {
                    Text(
                        text = "Balas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.clickable { onReply() }
                    )
                }
                if (canDelete) {
                    Text(
                        text = "Hapus",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { showDeleteDialog = true }
                    )
                }
            }
        }
    }

    if (showDeleteDialog && canDelete) {
        DeleteDialog(
            title = "Hapus komentar?",
            text = "Komentar ini akan dihapus permanen.",
            onConfirm = { onDelete(); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
