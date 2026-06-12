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
import androidx.navigation.NavController
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
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val postLikes by viewModel.postLikes.collectAsState()
    val postComments by viewModel.postComments.collectAsState()
    val feedError by viewModel.feedError.collectAsState()

    val post = posts.find { it.id == postId }
    val likes = postLikes[postId] ?: emptyList()
    val comments = postComments[postId] ?: emptyList()
    val isLoggedIn = !session.token.isNullOrEmpty()
    val isLiked = !session.userId.isNullOrEmpty() && session.userId in likes

    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var showFullImage by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.loadComments(postId)
    }

    LaunchedEffect(feedError) {
        feedError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedError()
        }
    }

    // Auto-scroll ke komentar terbaru
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size + 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Post", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

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
                                    .height(34.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Balas ${target.username}",
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
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Kirim",
                                modifier = Modifier.size(20.dp)
                            )
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
                            "Login untuk komentar",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (post == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Post header
            item {
                PostDetailHeader(
                    post = post,
                    likeCount = likes.size,
                    commentCount = comments.size,
                    isLiked = isLiked,
                    onLike = { viewModel.toggleLike(postId) },
                    onShowFullImage = { showFullImage = true }
                )
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
            }

            // Judul komentar
            item {
                if (comments.isNotEmpty()) {
                    Text(
                        text = "Komentar",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Komentar list
            if (comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💬", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Belum ada komentar",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                if (isLoggedIn) "Jadilah yang pertama komentar!" else "Login untuk komentar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    val canDeleteComment = comment.user_id == session.userId || session.isAdmin
                    CommentItem(
                        comment = comment,
                        canDelete = canDeleteComment,
                        onReply = if (isLoggedIn) { { replyTarget = comment } } else null,
                        onDelete = { viewModel.deleteComment(postId, comment.id) }
                    )
                }
            }
        }
    }

    // Full screen image
    if (showFullImage && !post?.image_url.isNullOrEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullImage = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.97f))
                    .clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = post!!.image_url,
                    contentDescription = "Foto penuh",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.FillWidth
                )
                IconButton(
                    onClick = { showFullImage = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PostDetailHeader(
    post: Post,
    likeCount: Int,
    commentCount: Int,
    isLiked: Boolean,
    onLike: () -> Unit,
    onShowFullImage: () -> Unit
) {
    val timeStr = remember(post.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(post.created_at.take(19)) ?: Date()
            val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id"))
            fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
            fmt.format(date)
        } catch (e: Exception) { "" }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!post.avatar_url.isNullOrEmpty()) {
                AsyncImage(
                    model = post.avatar_url,
                    contentDescription = post.username,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        post.username.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (post.is_admin == true) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("ADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        if (!post.image_url.isNullOrEmpty()) {
            AsyncImage(
                model = post.image_url,
                contentDescription = "Foto post",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 450.dp)
                    .clickable { onShowFullImage() },
                contentScale = ContentScale.FillWidth
            )
        }

        if (!post.caption.isNullOrEmpty()) {
            Text(
                text = post.caption,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 15.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLike() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$likeCount",
                    fontSize = 13.sp,
                    color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = if (isLiked) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$commentCount",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    comment: PostComment,
    canDelete: Boolean,
    onReply: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isReply = !comment.reply_to_username.isNullOrEmpty()

    val timeStr = remember(comment.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(comment.created_at.take(19)) ?: Date()
            val now = Date()
            val diffMin = (now.time - date.time) / 60000
            val diffHour = diffMin / 60
            val diffDay = diffHour / 24
            when {
                diffMin < 1 -> "Baru saja"
                diffMin < 60 -> "${diffMin}m"
                diffHour < 24 -> "${diffHour}j"
                diffDay < 7 -> "${diffDay}h"
                else -> {
                    val fmt = SimpleDateFormat("d MMM", Locale("id"))
                    fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                    fmt.format(date)
                }
            }
        } catch (e: Exception) { "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onReply?.invoke() },
                onLongClick = { if (canDelete) showDeleteDialog = true }
            )
            .padding(
                start = if (isReply) 46.dp else 14.dp,
                end = 14.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        if (!comment.avatar_url.isNullOrEmpty()) {
            AsyncImage(
                model = comment.avatar_url,
                contentDescription = comment.username,
                modifier = Modifier
                    .size(if (isReply) 28.dp else 34.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(if (isReply) 28.dp else 34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.username.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isReply) 11.sp else 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Reply indicator
            if (isReply) {
                Text(
                    text = "↩ @${comment.reply_to_username}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.username,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            if (onReply != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Balas",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.clickable { onReply() }
                )
            }
        }
    }

    if (showDeleteDialog && canDelete) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus komentar?") },
            text = { Text("Komentar ini akan dihapus permanen.") },
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
