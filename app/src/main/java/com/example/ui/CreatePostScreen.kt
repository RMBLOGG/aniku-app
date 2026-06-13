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
        uri?.let { pendingImageUri = it; uploadedImageUrl = null }
    }

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
    val canPost = (caption.trim().isNotEmpty() || uploadedImageUrl != null || pendingImageUri != null) && !isBusy

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Buat Postingan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (pendingImageUri != null && uploadedImageUrl == null) {
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
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "Posting",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (canPost) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            // Avatar + caption
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                AvatarCircle(
                    avatarUrl = session.avatarUrl,
                    username = session.username ?: "A",
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.username ?: session.email?.substringBefore("@") ?: "Kamu",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 500) caption = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        decorationBox = { inner ->
                            if (caption.isEmpty()) {
                                Text(
                                    "Apa yang kamu pikirkan?",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${caption.length}/500",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // Image preview
            AnimatedVisibility(
                visible = pendingImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                pendingImageUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        IconButton(
                            onClick = { pendingImageUri = null; uploadedImageUrl = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Hapus foto",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (isUploadingImage) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                modifier = Modifier.padding(top = 8.dp)
            )

            // Pilih foto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy) { imagePickerLauncher.launch("image/*") }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Foto",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }
    }
}

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        decorationBox = decorationBox
    )
}
