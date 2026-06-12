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
    val isLoggedIn = !session.token.isNullOrEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val postComments by viewModel.postComments.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFeed()
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Feed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
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
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Buat Post", tint = Color.White)
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
                isFeedLoading && posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                posts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📸", fontSize = 52.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Belum ada post",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                if (isLoggedIn) "Jadilah yang pertama posting!" else "Login untuk mulai posting",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            if (!isLoggedIn) {
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { navController.navigate("auth") }) {
                                    Text("Login", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                onLike = {
                                    if (!isLoggedIn) {
                                        // Show snackbar via feedError
                                        viewModel.toggleLike(post.id) // will set error if not logged in
                                    } else {
                                        viewModel.toggleLike(post.id)
                                    }
                                },
                                onComment = { onOpenPost(post.id) },
                                onDelete = { viewModel.deletePost(post.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
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

    val timeStr = remember(post.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(post.created_at.take(19)) ?: Date()
            val now = Date()
            val diffMs = now.time - date.time
            val diffMin = diffMs / 60000
            val diffHour = diffMin / 60
            val diffDay = diffHour / 24
            when {
                diffMin < 1 -> "Baru saja"
                diffMin < 60 -> "${diffMin}m lalu"
                diffHour < 24 -> "${diffHour}j lalu"
                diffDay < 7 -> "${diffDay}h lalu"
                else -> {
                    val fmt = SimpleDateFormat("d MMM", Locale("id"))
                    fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                    fmt.format(date)
                }
            }
        } catch (e: Exception) { "" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { if (canDelete) showDeleteDialog = true }
            ),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: avatar + username + time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!post.avatar_url.isNullOrEmpty()) {
                    AsyncImage(
                        model = post.avatar_url,
                        contentDescription = post.username,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.username.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.username,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (post.is_admin == true) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.error)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "ADMIN",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Post image
            if (!post.image_url.isNullOrEmpty()) {
                AsyncImage(
                    model = post.image_url,
                    contentDescription = "Foto post",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .clickable { showFullImage = true },
                    contentScale = ContentScale.FillWidth
                )
            }

            // Caption
            if (!post.caption.isNullOrEmpty()) {
                Text(
                    text = post.caption,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Action bar: like + comment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like button
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
                        text = likeCount.toString(),
                        fontSize = 13.sp,
                        color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = if (isLiked) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Comment button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onComment() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Komentar",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = commentCount.toString(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Divider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                thickness = 0.5.dp
            )
        }
    }

    // Full screen image preview
    if (showFullImage && !post.image_url.isNullOrEmpty()) {
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
                    model = post.image_url,
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
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }
        }
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus post?") },
            text = { Text("Post ini akan dihapus permanen.") },
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
