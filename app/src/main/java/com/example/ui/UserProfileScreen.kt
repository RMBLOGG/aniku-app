package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: AnikuViewModel,
    userId: String,
    onBack: () -> Unit,
    onEditOwnProfile: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val profile by viewModel.viewedProfile.collectAsState()
    val donationTotal by viewModel.viewedProfileDonationTotal.collectAsState()
    val chatCount by viewModel.viewedProfileChatCount.collectAsState()
    val isLoading by viewModel.isViewedProfileLoading.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    val isOwnProfile = session.userId == userId
    val canModerate = session.canModerate()

    val banStatusMessage by viewModel.banStatusMessage.collectAsState()
    var hasMounted by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadPublicUserProfile(userId)
        hasMounted = true
    }

    LaunchedEffect(banStatusMessage) {
        if (hasMounted && banStatusMessage != null) {
            viewModel.loadPublicUserProfile(userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil Pengguna") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading || profile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = accentColor)
                } else {
                    Text("Pengguna tidak ditemukan", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
            return@Scaffold
        }

        val p = profile!!
        val seasonLevel = p.season_level ?: 1
        val seasonXp = p.season_xp ?: 0
        val currentLevelBaseXp = 20 * (seasonLevel - 1) * (seasonLevel - 1)
        val nextLevelXp = 20 * seasonLevel * seasonLevel
        val xpIntoLevel = (seasonXp - currentLevelBaseXp).coerceAtLeast(0)
        val xpNeededForLevel = (nextLevelXp - currentLevelBaseXp).coerceAtLeast(1)
        val progress = (xpIntoLevel.toFloat() / xpNeededForLevel.toFloat()).coerceIn(0f, 1f)

        val joinedDate = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(p.created_at?.take(19) ?: "")
            SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(parsed ?: Any())
        } catch (e: Exception) {
            "-"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Banner (read-only)
            if (!p.banner_url.isNullOrEmpty()) {
                AsyncImage(
                    model = p.banner_url,
                    contentDescription = "Banner",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
            // Avatar & identitas
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!p.avatar_url.isNullOrBlank()) {
                    AsyncImage(
                        model = p.avatar_url,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            p.username?.take(1)?.uppercase() ?: "?",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p.username ?: "Pengguna",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                p.user_number?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("#$it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val (label, color) = when {
                    p.isAdmin() -> "ADMIN" to Color(0xFFFFD200)
                    p.role == "moderator" -> "MODERATOR" to Color(0xFFB388FF)
                    else -> "USER" to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Level & XP
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Level Musim Ini", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("Lv.$seasonLevel", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "$xpIntoLevel / $xpNeededForLevel XP ke Level ${seasonLevel + 1}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Statistik: Donasi & Chat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Dukungan", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (donationTotal > 0) "Rp${"%,d".format(donationTotal).replace(",", ".")}" else "Belum ada",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFFFD200)
                        )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Pesan Chat", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$chatCount pesan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Bergabung Sejak", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(joinedDate, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isOwnProfile) {
                OutlinedButton(
                    onClick = onEditOwnProfile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Profil Saya")
                }
            } else if (canModerate) {
                val isBanned = p.is_banned == true
                Button(
                    onClick = { viewModel.toggleUserBanStatus(p) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBanned) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (isBanned) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBanned) "Aktifkan Kembali User" else "Banned User")
                }
            }
            }
        }
    }
}
