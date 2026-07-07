package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.DownloadRecord
import com.example.network.DownloadStatus
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onPlayOffline: (DownloadRecord) -> Unit,
    onLoginRequired: () -> Unit = {}
) {
    val session by viewModel.session.collectAsState()
    val isLoggedIn = session.token != null
    val downloads by viewModel.downloads.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    var recordToDelete by remember { mutableStateOf<DownloadRecord?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.refreshDownloads()
    }

    // Poll ringan selama ada yang statusnya masih PENDING/DOWNLOADING, biar progress
    // "Downloading..." otomatis berubah jadi "Selesai" tanpa user harus keluar-masuk layar.
    LaunchedEffect(downloads) {
        val stillActive = downloads.any {
            it.status == DownloadStatus.PENDING.name || it.status == DownloadStatus.DOWNLOADING.name
        }
        if (stillActive) {
            delay(3000)
            viewModel.refreshDownloads()
        }
    }

    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus Unduhan?", fontWeight = FontWeight.Bold) },
            text = { Text("File video di perangkat kamu juga akan ikut dihapus.") },
            confirmButton = {
                Button(
                    onClick = {
                        recordToDelete?.let { viewModel.deleteDownload(it) }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("Batal") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Unduhan", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                if (isLoggedIn && downloads.isNotEmpty()) {
                    Text(
                        "${downloads.size} episode tersimpan",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        when {
            !isLoggedIn -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(44.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Login Dulu Yuk", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Download episode buat ditonton offline cuma bisa dipakai kalau kamu udah login.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onLoginRequired, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) {
                        Text("Login Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
            downloads.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(44.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Belum Ada Unduhan", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Episode yang kamu download buat nonton offline akan muncul di sini.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(downloads, key = { it.downloadId }) { record ->
                        DownloadListCard(
                            record = record,
                            accentColor = accentColor,
                            onClick = {
                                if (record.status == DownloadStatus.COMPLETE.name && !record.localPath.isNullOrBlank()) {
                                    onPlayOffline(record)
                                }
                            },
                            onDelete = { recordToDelete = record }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadListCard(
    record: DownloadRecord,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = record.status == DownloadStatus.COMPLETE.name) { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(width = 60.dp, height = 85.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (record.animePoster.isNotBlank()) {
                AsyncImage(
                    model = record.animePoster,
                    contentDescription = record.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.animeTitle.ifBlank { "Anime" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.episodeTitle.ifBlank { record.fileName },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            when (record.status) {
                DownloadStatus.COMPLETE.name -> StatusChip("Siap ditonton offline", accentColor, Icons.Default.CheckCircle)
                DownloadStatus.FAILED.name -> StatusChip("Gagal, coba download ulang", Color(0xFFE53935), Icons.Default.ErrorOutline)
                else -> StatusChip("Mengunduh...", Color(0xFFFFA726), Icons.Default.Downloading)
            }
        }

        if (record.status == DownloadStatus.COMPLETE.name) {
            IconButton(onClick = onClick) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = "Putar", tint = accentColor, modifier = Modifier.size(26.dp))
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
