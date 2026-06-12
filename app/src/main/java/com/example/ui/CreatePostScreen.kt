package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    var caption by remember { mutableStateOf("") }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImageUri = it
            uploadedImageUrl = null
        }
    }

    // Listen for errors
    LaunchedEffect(feedError) {
        feedError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedError()
        }
    }

    // Track post creation success: posts will reload, then we pop back
    val posts by viewModel.posts.collectAsState()
    var postCountOnEnter by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        postCountOnEnter = posts.size
    }
    LaunchedEffect(posts.size) {
        if (postCountOnEnter >= 0 && posts.size > postCountOnEnter && !isCreatingPost) {
            onBack()
        }
    }

    val canPost = (caption.trim().isNotEmpty() || uploadedImageUrl != null) && !isCreatingPost && !isUploadingImage

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Buat Post", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (pendingImageUri != null && uploadedImageUrl == null) {
                                // Upload foto dulu
                                isUploadingImage = true
                                viewModel.uploadPostImage(context, pendingImageUri!!) { url ->
                                    isUploadingImage = false
                                    uploadedImageUrl = url
                                    viewModel.createPost(
                                        caption = caption.trim().ifEmpty { null },
                                        imageUrl = url
                                    )
                                }
                            } else {
                                viewModel.createPost(
                                    caption = caption.trim().ifEmpty { null },
                                    imageUrl = uploadedImageUrl
                                )
                            }
                        },
                        enabled = canPost
                    ) {
                        if (isCreatingPost || isUploadingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Post",
                                fontWeight = FontWeight.Bold,
                                color = if (canPost) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Avatar + caption row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Avatar
                if (!session.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = session.avatarUrl,
                        contentDescription = session.username,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (session.username ?: "A").take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.username ?: session.email?.substringBefore("@") ?: "Kamu",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 500) caption = it },
                        placeholder = { Text("Ceritakan sesuatu...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        supportingText = {
                            Text(
                                "${caption.length}/500",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    )
                }
            }

            // Preview image yang dipilih
            AnimatedVisibility(
                visible = pendingImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                pendingImageUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Preview foto",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        // Hapus foto
                        IconButton(
                            onClick = {
                                pendingImageUri = null
                                uploadedImageUrl = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Hapus foto",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isUploadingImage) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Tombol pilih foto dari galeri
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isCreatingPost && !isUploadingImage) {
                        imagePickerLauncher.launch("image/*")
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "Pilih foto",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Tambah foto",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
    }
}
