package com.example.ui

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
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.Post
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: AnikuViewModel,
    navController: NavController,
    onCreatePost: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    val session by viewModel.session.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val isFeedLoading by viewModel.isFeedLoading.collectAsState()
    val feedError by viewModel.feedError.collectAsState()
    val postLikes by viewModel.postLikes.collectAsState()
    val postComments by viewModel.postComments.collectAsState()
    val isLoggedIn = !session.token.isNullOrEmpty()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadFeed() }

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
                title = {
                    Text(
                        "Feed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFeed() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(
                    onClick = onCreatePost,
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Buat Post", tint = Color.White)
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isFeedLoading && posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
                posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Text(
                                "Belum ada postingan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isLoggedIn) "Jadilah yang pertama posting"
                                else "Login untuk mulai posting",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            if (!isLoggedIn) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { navController.navigate("auth") }) {
                                    Text("Masuk", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(posts, key = { it.id }) { post ->
                            val likes = postLikes[post.id] ?: emptyList()
                            val commentCount = postComments[post.id]?.size ?: 0
                            val isLiked = !session.userId.isNullOrEmpty() && session.userId in likes
                            val canDelete = post.user_id == session.userId || session.isAdmin

                            PostCard(
                                post = post,
                                likeCount = likes.size,
                                commentCount = commentCount,
                                isLiked = isLiked,
                                canDelete = canDelete,
                                onLike = { viewModel.toggleLike(post.id) },
                                onComment = { onOpenPost(post.id) },
                                onDelete = { viewModel.deletePost(post.id) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                thickness = 0.5.dp
                            )
                        }
                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCard(
    post: Post,
    likeCount: Int,
    commentCount: Int,
    isLiked: Boolean,
    canDelete: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }

    val timeStr = remember(post.created_at) { relativeTime(post.created_at) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { if (canDelete) showDeleteDialog = true }
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(
                avatarUrl = post.avatar_url,
                username = post.username,
                size = 38.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
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
            if (canDelete) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opsi",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Caption
        if (!post.caption.isNullOrEmpty()) {
            Text(
                text = post.caption,
                modifier = Modifier.padding(horizontal = 14.dp, bottom = if (post.image_url.isNullOrEmpty()) 10.dp else 8.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Image
        if (!post.image_url.isNullOrEmpty()) {
            AsyncImage(
                model = post.image_url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp)
                    .clickable { showFullImage = true },
                contentScale = ContentScale.FillWidth
            )
        }

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            ActionButton(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (likeCount > 0) likeCount.toString() else "Suka",
                tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                onClick = onLike
            )
            ActionButton(
                icon = Icons.Default.ChatBubbleOutline,
                label = if (commentCount > 0) "$commentCount Komentar" else "Komentar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                onClick = onComment
            )
        }
    }

    // Full screen image
    if (showFullImage && !post.image_url.isNullOrEmpty()) {
        FullScreenImage(url = post.image_url!!, onDismiss = { showFullImage = false })
    }

    if (showDeleteDialog) {
        DeleteDialog(
            title = "Hapus postingan?",
            text = "Postingan ini akan dihapus permanen.",
            onConfirm = { onDelete(); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

// ─── Shared helpers ───────────────────────────────────────

@Composable
fun AvatarCircle(avatarUrl: String?, username: String, size: androidx.compose.ui.unit.Dp) {
    if (!avatarUrl.isNullOrEmpty()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = username,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AdminBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            "ADMIN",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FullScreenImage(url: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentScale = ContentScale.FillWidth
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
            }
        }
    }
}

@Composable
fun DeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

fun relativeTime(createdAt: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(createdAt.take(19)) ?: Date()
        val now = Date()
        val diffMin = (now.time - date.time) / 60000
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24
        when {
            diffMin < 1 -> "Baru saja"
            diffMin < 60 -> "${diffMin} menit lalu"
            diffHour < 24 -> "${diffHour} jam lalu"
            diffDay < 7 -> "${diffDay} hari lalu"
            else -> {
                val fmt = SimpleDateFormat("d MMM yyyy", Locale("id"))
                fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                fmt.format(date)
            }
        }
    } catch (e: Exception) { "" }
}
