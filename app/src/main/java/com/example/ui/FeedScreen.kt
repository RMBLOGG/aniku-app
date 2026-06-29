package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        feedError?.let { snackbarHostState.showSnackbar(it); viewModel.clearFeedError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Feed",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFeed() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(
                    onClick = onCreatePost,
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Buat Post",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isFeedLoading && posts.isEmpty() -> {
                    LoadingScreen("Memuat postingan...")
                }
                posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Belum ada postingan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isLoggedIn) "Jadilah yang pertama posting"
                                else "Masuk untuk mulai posting",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            if (!isLoggedIn) {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { navController.navigate("auth") },
                                    shape = CircleShape
                                ) {
                                    Text("Masuk", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(posts, key = { it.id }) { post ->
                            val likes = postLikes[post.id] ?: emptyList()
                            val commentCount = postComments[post.id]?.size ?: 0
                            val isLiked = !session.userId.isNullOrEmpty() && session.userId in likes
                            val canDelete = post.user_id == session.userId || session.canModerate()

                            TweetCard(
                                post = post,
                                likeCount = likes.size,
                                commentCount = commentCount,
                                isLiked = isLiked,
                                canDelete = canDelete,
                                onLike = { viewModel.toggleLike(post.id) },
                                onComment = { onOpenPost(post.id) },
                                onDelete = { viewModel.deletePost(post.id) },
                                onOpenAnime = { slug -> navController.navigate("detail/$slug") }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TweetCard(
    post: Post,
    likeCount: Int,
    commentCount: Int,
    isLiked: Boolean,
    canDelete: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onOpenAnime: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    val timeStr = remember(post.created_at) { twitterTime(post.created_at) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onComment() },
                onLongClick = { if (canDelete) showDeleteDialog = true }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar kiri
        AvatarCircle(
            avatarUrl = post.avatar_url,
            username = post.username,
            size = 42.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Konten kanan
        Column(modifier = Modifier.weight(1f)) {

            // Baris nama + waktu + more
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (post.is_admin == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AdminBadge()
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "· $timeStr",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canDelete) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "Opsi",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Caption
            if (!post.caption.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = linkifyText(post.caption),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    letterSpacing = 0.sp
                )
            }

            // Image
            if (!post.image_url.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = post.image_url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showFullImage = true },
                    contentScale = ContentScale.FillWidth
                )
            }

            // Shared anime
            if (!post.anime_slug.isNullOrEmpty() && !post.anime_title.isNullOrEmpty() && !post.anime_poster.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                SharedAnimeCard(
                    title = post.anime_title,
                    poster = post.anime_poster,
                    type = post.anime_type,
                    onClick = { onOpenAnime(post.anime_slug) }
                )
            }

            // Action bar — style Twitter
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TweetAction(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    count = commentCount,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    onClick = onComment
                )
                Spacer(modifier = Modifier.width(28.dp))
                TweetAction(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    count = likeCount,
                    tint = if (isLiked) Color(0xFFE0245E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    onClick = onLike
                )
            }
        }
    }

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

@Composable
private fun TweetAction(
    icon: ImageVector,
    count: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                color = tint,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────

@Composable
fun AvatarCircle(avatarUrl: String?, username: String, size: Dp) {
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
fun ModeratorBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF7C4DFF))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            "MOD",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
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
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, fontSize = 13.sp, color = tint, fontWeight = FontWeight.Medium)
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
        title = { Text(title, fontWeight = FontWeight.Bold) },
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

fun relativeTime(createdAt: String): String = twitterTime(createdAt)

fun twitterTime(createdAt: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(createdAt.take(19)) ?: Date()
        val now = Date()
        val diffSec = (now.time - date.time) / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24
        when {
            diffSec < 60 -> "${diffSec}d"
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
