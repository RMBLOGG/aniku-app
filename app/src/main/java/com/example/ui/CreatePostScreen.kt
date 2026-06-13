package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val isCreatingPost by viewModel.isCreatingPost.collectAsState()
    val feedError by viewModel.feedError.collectAsState()
    val pendingSharedAnime by viewModel.pendingSharedAnime.collectAsState()
    val context = LocalContext.current

    var caption by remember { mutableStateOf("") }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { pendingImageUri = it; uploadedImageUrl = null } }

    LaunchedEffect(feedError) {
        feedError?.let { snackbarHostState.showSnackbar(it); viewModel.clearFeedError() }
    }

    val posts by viewModel.posts.collectAsState()
    var postCountOnEnter by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) { postCountOnEnter = posts.size }
    LaunchedEffect(posts.size) {
        if (postCountOnEnter >= 0 && posts.size > postCountOnEnter && !isCreatingPost) onBack()
    }

    val isBusy = isCreatingPost || isUploadingImage
    val canPost = (caption.trim().isNotEmpty() || pendingImageUri != null || pendingSharedAnime != null) && !isBusy

    fun handleBack() {
        viewModel.clearPendingSharedAnime()
        onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (pendingImageUri != null && uploadedImageUrl == null) {
                                isUploadingImage = true
                                viewModel.uploadPostImage(context, pendingImageUri!!) { url ->
                                    isUploadingImage = false
                                    uploadedImageUrl = url
                                    viewModel.createPost(caption.trim().ifEmpty { null }, url, pendingSharedAnime)
                                }
                            } else {
                                viewModel.createPost(caption.trim().ifEmpty { null }, uploadedImageUrl, pendingSharedAnime)
                            }
                        },
                        enabled = canPost,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Posting", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Avatar
                AvatarCircle(
                    avatarUrl = session.avatarUrl,
                    username = session.username ?: "A",
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.username ?: session.email?.substringBefore("@") ?: "Kamu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Caption input
                    BasicTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 500) caption = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            lineHeight = 24.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (caption.isEmpty()) {
                                    Text(
                                        "Apa yang sedang terjadi?",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        fontSize = 17.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    // Image preview
                    AnimatedVisibility(
                        visible = pendingImageUri != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        pendingImageUri?.let { uri ->
                            Box(modifier = Modifier.padding(top = 12.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                                IconButton(
                                    onClick = { pendingImageUri = null; uploadedImageUrl = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (isUploadingImage) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }

                    // Shared anime preview
                    AnimatedVisibility(
                        visible = pendingSharedAnime != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        pendingSharedAnime?.let { anime ->
                            Box(modifier = Modifier.padding(top = 12.dp)) {
                                SharedAnimeCard(anime = anime)
                                IconButton(
                                    onClick = { viewModel.clearPendingSharedAnime() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Toolbar bawah — pilih foto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = !isBusy
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Foto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (caption.isNotEmpty()) {
                    Text(
                        "${caption.length}/500",
                        fontSize = 12.sp,
                        color = if (caption.length > 450)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}
