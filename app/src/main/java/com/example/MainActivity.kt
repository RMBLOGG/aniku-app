package com.example

import android.os.Bundle
import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.network.AnikuViewModel
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

private val TAB_ROUTES = setOf(
    "home", "search", "explore", "feed", "bookmark",
    "schedule", "top_supporter", "chat", "profile", "settings"
)

private fun isTabRoute(route: String?): Boolean {
    if (route == null) return false
    return TAB_ROUTES.contains(route)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : FragmentActivity() {

    companion object {
        var isWatchingDirectStream = false
        var pipExoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isWatchingDirectStream && pipExoPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Immersive sticky — juga diperkuat di onResume & onWindowFocusChanged
        hideSystemBars()
        val viewModel = AnikuViewModel(this)

        // Minta izin notifikasi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // Jadwalkan pengecekan feed berkala
        FeedNotificationWorker.schedule(this)

        // Init Analytics
        AnikuAnalytics.init(this)

        // Ambil FCM token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val prefs = getSharedPreferences("aniku_fcm", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("fcm_token", token).apply()
                android.util.Log.d("FCM", "Token: $token")
                // Kalau user udah login duluan, langsung sync sekarang.
                // Kalau belum, viewModel bakal auto-sync pas session login masuk.
                viewModel.syncPushToken(token)
            }

        // Subscribe ke topic feed_updates
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic("feed_updates")
            .addOnSuccessListener {
                android.util.Log.d("FCM", "Subscribed to feed_updates")
            }

        // Subscribe ke topic chat_updates
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic("chat_updates")
            .addOnSuccessListener {
                android.util.Log.d("FCM", "Subscribed to chat_updates")
            }

        // Handle deep link dari notifikasi
        val deepLinkRoute = when {
            intent?.data?.scheme == "aniku" && intent.data?.host == "feed" -> "feed"
            intent?.data?.scheme == "aniku" && intent.data?.host == "chat" -> "chat"
            intent?.data?.scheme == "aniku" && intent.data?.host == "private_chat" -> {
                val otherUserId = intent.data?.lastPathSegment
                if (!otherUserId.isNullOrBlank()) "private_chat/$otherUserId" else "chat"
            }
            intent?.data?.scheme == "aniku" && intent.data?.host == "reset-password" -> "reset_password"
            else -> null
        }

        // Supabase taro token di URL fragment (#access_token=...&type=recovery/signup),
        // bukan query param biasa, jadi harus diparse manual dari fragment-nya.
        // PENTING: link "konfirmasi email" (signup) dan "reset password" (recovery) sama-sama
        // ngarah ke host "reset-password" (satu-satunya redirect URL yang kedaftar di Supabase),
        // jadi kita bedain lewat "type" biar konfirmasi email gak nyasar ke layar ganti password.
        val fragmentParams: Map<String, String> = if (deepLinkRoute == "reset_password") {
            val fragment = intent?.data?.fragment ?: intent?.data?.encodedQuery
            fragment?.split("&")
                ?.mapNotNull { val p = it.split("=", limit = 2); if (p.size == 2) p[0] to p[1] else null }
                ?.toMap() ?: emptyMap()
        } else emptyMap()

        val linkType = fragmentParams["type"] // "signup" atau "recovery"
        val resetAccessToken: String? = fragmentParams["access_token"]
        val resetRefreshToken: String? = fragmentParams["refresh_token"]

        // Kalau linknya dari konfirmasi email (bukan recovery), langsung auto-login
        val isEmailConfirmLink = deepLinkRoute == "reset_password" && linkType == "signup"

        setContent {
            val isDark by viewModel.isDark.collectAsState()
            val accentColorName by viewModel.accentColorName.collectAsState()
            val textSizeScale by viewModel.textSize.collectAsState()
            val themePreset by viewModel.themePreset.collectAsState()
            val navStyle by viewModel.navStyle.collectAsState()
            val cardStyle by viewModel.cardStyle.collectAsState()


            MyApplicationTheme(
                darkTheme = isDark,
                accentName = accentColorName,
                textScale = textSizeScale,
                themePreset = themePreset
            ) {
                val shutdownEnabled by viewModel.remoteConfigManager.shutdownEnabled.collectAsState()
                val shutdownMessage by viewModel.remoteConfigManager.shutdownMessage.collectAsState()
                val shutdownSupportInfo by viewModel.remoteConfigManager.shutdownSupportInfo.collectAsState()
                val maintenanceMode by viewModel.remoteConfigManager.maintenanceMode.collectAsState()
                val maintenanceMessage by viewModel.remoteConfigManager.maintenanceMessage.collectAsState()
                val forceBannedLogout by viewModel.forceBannedLogout.collectAsState()
                val forceBannedReason by viewModel.bannedReason.collectAsState()
                val appLockEnabled by viewModel.appLockEnabled.collectAsState()
                val appLockType by viewModel.appLockType.collectAsState()
                val appPin by viewModel.appPin.collectAsState()
                var isUnlocked by remember { mutableStateOf(false) }

                // Re-lock saat app kembali dari background
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            if (appLockEnabled) isUnlocked = false
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                var showSplash by remember { mutableStateOf(true) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2200)
                    showSplash = false
                }

                if (showSplash) {
                    com.example.ui.SplashScreen()
                } else if (shutdownEnabled) {
                    // Kill-switch PERMANEN, prioritas paling atas -- di atas maintenance
                    // & ban. Dipakai kalau aplikasi resmi ditutup total (misal biaya
                    // server/database bulanan udah gak sanggup ditanggung admin lagi).
                    com.example.ui.ShutdownScreen(
                        message = shutdownMessage,
                        supportInfo = shutdownSupportInfo
                    )
                } else if (maintenanceMode) {
                    // Kill-switch paling atas — begitu nyala di Firebase Console, semua user
                    // langsung ke-block di sini real-time, gak peduli lagi login/lock/dimana.
                    com.example.ui.MaintenanceScreen(message = maintenanceMessage)
                } else if (forceBannedLogout) {
                    // Sama kayak maintenance mode, tapi per-user -- begitu admin nge-ban
                    // akun ini (dipush via Firebase RTDB), device langsung ke-kick di sini
                    // real-time, gak perlu nunggu logout manual/token expired.
                    com.example.ui.BannedScreen(
                        reason = forceBannedReason,
                        onAcknowledge = { viewModel.acknowledgeBannedLogout() }
                    )
                } else if (appLockEnabled && !isUnlocked) {
                    LockScreen(
                        lockType = appLockType,
                        savedPin = appPin,
                        onUnlocked = { isUnlocked = true }
                    )
                } else {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Navigate ke deep link kalau ada (dari notifikasi / reset password / konfirmasi email)
                LaunchedEffect(deepLinkRoute) {
                    if (isEmailConfirmLink && resetAccessToken != null) {
                        // Link konfirmasi email: langsung login pakai token dari Supabase, gak usah ke halaman ganti password
                        viewModel.confirmEmailAndLogin(resetAccessToken, resetRefreshToken) {
                            navController.navigate("home") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    } else {
                        deepLinkRoute?.let {
                            val target = if (it == "reset_password") {
                                val encodedToken = java.net.URLEncoder.encode(resetAccessToken ?: "", "UTF-8")
                                "reset_password?token=$encodedToken"
                            } else it
                            navController.navigate(target) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }

                val bottomRoutes = listOf("home", "search", "explore", "bookmark", "schedule", "feed", "user_list", "request_anime", "clans", "diamond_topup", "downloads")
                val showBottomBar = currentRoute in bottomRoutes
                var showMoreSheet by remember { mutableStateOf(false) }
                val hasUnreadChat by viewModel.hasUnreadChat.collectAsState()
                val nobarEnabled by viewModel.remoteConfigManager.nobarEnabled.collectAsState()
                val feedEnabled by viewModel.remoteConfigManager.feedEnabled.collectAsState()
                val hasNewSupportEvent by viewModel.hasNewSupportEvent.collectAsState()
                val latestSupportEvent by viewModel.latestSupportEvent.collectAsState()
                var showDonationBanner by remember { mutableStateOf(false) }

                // Popup ban real-time — muncul begitu ban kedeteksi (polling tiap 15s),
                // session udah otomatis di-clear di ViewModel, di sini cuma redirect + tampilkan info
                val showBannedDialog by viewModel.showBannedDialog.collectAsState()
                val bannedDialogReason by viewModel.bannedReason.collectAsState()
                if (showBannedDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* tidak bisa di-dismiss tanpa konfirmasi */ },
                        title = { Text("Akun Dibanned") },
                        text = {
                            Text(
                                if (!bannedDialogReason.isNullOrBlank())
                                    "Akunmu telah ditangguhkan (banned) oleh Admin.\n\nAlasan: $bannedDialogReason\n\nKamu akan keluar dari sesi ini."
                                else
                                    "Akunmu telah ditangguhkan (banned) oleh Admin. Kamu akan keluar dari sesi ini."
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                viewModel.dismissBannedDialog()
                                navController.navigate("auth") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }

                // Banner support popup (Trakteer + Top-up Diamond) — modern: animasi masuk/keluar, swipe buat nutup, progress bar countdown
                LaunchedEffect(hasNewSupportEvent) {
                    if (hasNewSupportEvent) {
                        showDonationBanner = true
                        kotlinx.coroutines.delay(5000)
                        showDonationBanner = false
                        viewModel.markSupportEventSeen()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .zIndex(999f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedVisibility(
                        visible = showDonationBanner && latestSupportEvent != null,
                        enter = fadeIn(tween(280)) + slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        exit = fadeOut(tween(220)) + slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        )
                    ) {
                        val event = latestSupportEvent
                        if (event != null) {
                            val scope = rememberCoroutineScope()
                            val offsetX = remember { Animatable(0f) }
                            val density = LocalDensity.current
                            val dismissThresholdPx = with(density) { 90.dp.toPx() }

                            fun dismiss() {
                                showDonationBanner = false
                                viewModel.markSupportEventSeen()
                            }

                            // Progress bar countdown 5 detik, ngikutin auto-dismiss di atas
                            val progress = remember { Animatable(1f) }
                            LaunchedEffect(event.key) {
                                progress.snapTo(1f)
                                progress.animateTo(0f, animationSpec = tween(5000, easing = LinearEasing))
                            }

                            // Icon bernapas pelan biar kerasa "hidup"
                            val iconTransition = rememberInfiniteTransition(label = "donationIcon")
                            val iconScale by iconTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "iconScale"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationX = offsetX.value
                                        alpha = 1f - (kotlin.math.abs(offsetX.value) / dismissThresholdPx / 1.6f)
                                            .coerceIn(0f, 0.85f)
                                    }
                                    .pointerInput(event.key) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                scope.launch {
                                                    if (kotlin.math.abs(offsetX.value) > dismissThresholdPx) {
                                                        val target = if (offsetX.value > 0) 1200f else -1200f
                                                        offsetX.animateTo(target, animationSpec = tween(180))
                                                        dismiss()
                                                    } else {
                                                        offsetX.animateTo(
                                                            0f,
                                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                        )
                                                    }
                                                }
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                        }
                                    }
                                    .clickable { dismiss() },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                elevation = CardDefaults.cardElevation(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                    }
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (event.isDiamond) "💎" else "☕", fontSize = 20.sp)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-2).dp)
                                            ) {
                                                Text("✨", fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Support baru masuk!",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    if (event.isDiamond) "${event.displayName} top-up Diamond"
                                                        else "${event.displayName} men-support",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                        .padding(horizontal = 7.dp, vertical = 1.5.dp)
                                                ) {
                                                    Text(
                                                        event.amountLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("🙏", fontSize = 12.sp)
                                            }
                                        }
                                        IconButton(
                                            onClick = { dismiss() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Tutup",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    // Progress bar countdown auto-dismiss
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tutup sheet kalau navigasi berubah
                LaunchedEffect(currentRoute) {
                    showMoreSheet = false
                    currentRoute?.let { AnikuAnalytics.trackScreen(it) }
                }

                val mainNavItems = listOf(
                    Triple("search", "Cari", Icons.Default.Search),
                    Triple("explore", "Eksplor", Icons.Default.Apps),
                    Triple("bookmark", "Bookmark", Icons.Default.Favorite),
                )
                val sheetNavItems = listOf(
                    Triple("chat", "Chat", Icons.Default.Chat),
                    Triple("friends", "Teman", Icons.Default.People),
                    Triple("feed", "Feed", Icons.Default.GridView),
                    Triple("nobar_list", "Nobar", Icons.Default.Groups),
                    Triple("schedule", "Jadwal", Icons.Default.DateRange),
                    Triple("downloads", "Unduhan", Icons.Default.Download),
                    Triple("top_supporter", "Top Supporter", Icons.Default.EmojiEvents),
                    Triple("user_list", "Pengguna", Icons.Default.People),
                    Triple("clans", "Clan", Icons.Default.Diamond),
                    Triple("gacha", "Gacha Karakter", Icons.Default.Casino),
                    Triple("request_anime", "Anime Request", Icons.Default.VideoLibrary),
                ).filter { (route, _, _) ->
                    when (route) {
                        "feed" -> feedEnabled
                        "nobar_list" -> nobarEnabled
                        // "chat" tetap ditampilkan walau dimatikan, karena ChatScreen
                        // sendiri sudah nampilin layar maintenance-nya.
                        else -> true
                    }
                }
                val sheetRoutes = sheetNavItems.map { it.first }
                val isSheetRouteActive = currentRoute in sheetRoutes

                if (showMoreSheet) {
                    var sheetContentVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(40)
                        sheetContentVisible = true
                    }

                    ModalBottomSheet(
                        onDismissRequest = { showMoreSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        dragHandle = {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    ) {
                        // ── Header ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Menu Lainnya",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Semua fitur Aniku dalam satu tempat",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // ── Palet warna M3 tonal, konsisten sama tema (dark/light & dynamic color) ──
                        data class MenuTone(val container: Color, val onContainer: Color)
                        val tones = listOf(
                            MenuTone(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                            MenuTone(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
                            MenuTone(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                            MenuTone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer),
                        )
                        val sheetDescriptions = mapOf(
                            "chat" to "Ngobrol bareng komunitas",
                            "friends" to "Daftar teman & permintaan pertemanan",
                            "feed" to "Postingan dari pengguna",
                            "nobar_list" to "Nonton bareng, real-time",
                            "schedule" to "Jadwal tayang anime",
                            "downloads" to "Episode yang udah didownload",
                            "top_supporter" to "Daftar donatur terbaik Aniku",
                            "user_list" to "Cari & lihat profil pengguna",
                            "clans" to "Buat clan & kumpulin Diamond",
                            "gacha" to "Tukar Diamond buat dapetin karakter anime favoritmu",
                            "request_anime" to "Anime hasil request user",
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(sheetNavItems.size) { index ->
                                val (route, label, icon) = sheetNavItems[index]
                                val tone = tones[index % tones.size]
                                val desc = sheetDescriptions[route] ?: ""
                                val isActive = currentRoute == route

                                AnimatedVisibility(
                                    visible = sheetContentVisible,
                                    enter = fadeIn(
                                        animationSpec = tween(durationMillis = 260, delayMillis = index * 35)
                                    ) + slideInVertically(
                                        initialOffsetY = { it / 3 },
                                        animationSpec = tween(durationMillis = 320, delayMillis = index * 35, easing = FastOutSlowInEasing)
                                    ) + scaleIn(
                                        initialScale = 0.9f,
                                        animationSpec = tween(durationMillis = 320, delayMillis = index * 35, easing = FastOutSlowInEasing)
                                    )
                                ) {
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.94f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                                        label = "menuItemScale"
                                    )

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer(scaleX = scale, scaleY = scale)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = LocalIndication.current,
                                                onClick = {
                                                    showMoreSheet = false
                                                    navController.navigate(route) {
                                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            ),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isActive)
                                                tone.container
                                            else
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = if (isActive) 3.dp else 0.dp
                                        ),
                                        border = if (isActive) BorderStroke(1.5.dp, tone.onContainer.copy(alpha = 0.25f)) else null
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(tone.container),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        tint = tone.onContainer,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    if (route == "chat" && hasUnreadChat) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(11.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 4.dp, y = (-4).dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.error)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (isActive) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(tone.onContainer)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = label,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = desc,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val miniPlayerData by viewModel.miniPlayer.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        // "Floating" dirender lewat overlay Box di bawah (sibling Scaffold),
                        // BUKAN lewat slot bottomBar ini -- karena Scaffold.bottomBar selalu
                        // mereservasi area solid di belakangnya, jadi nav-nya gak bisa beneran
                        // "ngambang" di atas konten kayak punya Kuroflix.
                        if (showBottomBar && navStyle != "Floating") {
                            CurvedBottomNav(
                                mainNavItems = mainNavItems,
                                currentRoute = currentRoute,
                                isSheetRouteActive = isSheetRouteActive,
                                hasUnreadChat = hasUnreadChat,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onMoreClick = { showMoreSheet = true },
                                navStyle = navStyle
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(
                            // "Floating" gak butuh bottom padding -- konten sengaja scroll di
                            // BELAKANG nav overlay-nya, persis kayak Kuroflix.
                            bottom = if (showBottomBar && navStyle != "Floating") 76.dp else 0.dp
                        ),
                        enterTransition = {
                            val toTab = isTabRoute(targetState.destination.route)
                            val fromTab = isTabRoute(initialState.destination.route)
                            if (toTab && fromTab) {
                                fadeIn(animationSpec = tween(220, easing = EaseOutCubic))
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { it / 3 },
                                    animationSpec = tween(300, easing = EaseOutCubic)
                                ) + fadeIn(animationSpec = tween(300, easing = EaseOutCubic))
                            }
                        },
                        exitTransition = {
                            val toTab = isTabRoute(targetState.destination.route)
                            val fromTab = isTabRoute(initialState.destination.route)
                            if (toTab && fromTab) {
                                fadeOut(animationSpec = tween(180, easing = EaseInCubic))
                            } else {
                                slideOutHorizontally(
                                    targetOffsetX = { -it / 4 },
                                    animationSpec = tween(300, easing = EaseInCubic)
                                ) + fadeOut(animationSpec = tween(220, easing = EaseInCubic))
                            }
                        },
                        popEnterTransition = {
                            val toTab = isTabRoute(targetState.destination.route)
                            val fromTab = isTabRoute(initialState.destination.route)
                            if (toTab && fromTab) {
                                fadeIn(animationSpec = tween(220, easing = EaseOutCubic))
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { -it / 4 },
                                    animationSpec = tween(300, easing = EaseOutCubic)
                                ) + fadeIn(animationSpec = tween(300, easing = EaseOutCubic))
                            }
                        },
                        popExitTransition = {
                            val toTab = isTabRoute(targetState.destination.route)
                            val fromTab = isTabRoute(initialState.destination.route)
                            if (toTab && fromTab) {
                                fadeOut(animationSpec = tween(180, easing = EaseInCubic))
                            } else {
                                slideOutHorizontally(
                                    targetOffsetX = { it / 3 },
                                    animationSpec = tween(300, easing = EaseInCubic)
                                ) + fadeOut(animationSpec = tween(220, easing = EaseInCubic))
                            }
                        }
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") },
                                onSeeAllClicked = { tabName ->
                                    viewModel.setExploreTab(tabName)
                                    navController.navigate("explore")
                                }
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") },
                                onLoginRequired = { navController.navigate("auth") }
                            )
                        }
                        composable("explore") {
                            ExploreScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") },
                                onLoginRequired = { navController.navigate("auth") }
                            )
                        }
                        composable("feed") {
                            FeedScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onCreatePost = { navController.navigate("create_post") },
                                onOpenPost = { postId -> navController.navigate("post_detail/$postId") }
                            )
                        }
                        composable("create_post") {
                            CreatePostScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "post_detail/{postId}",
                            arguments = listOf(navArgument("postId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId") ?: ""
                            PostDetailScreen(
                                postId = postId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") }
                            )
                        }
                        composable("bookmark") {
                            BookmarkScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") }
                            )
                        }
                        composable("schedule") {
                            ScheduleScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") }
                            )
                        }
                        composable("top_supporter") {
                            TopSupporterScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "detail/{slug}",
                            arguments = listOf(navArgument("slug") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val slug = backStackEntry.arguments?.getString("slug") ?: ""
                            AnimeDetailScreen(
                                slug = slug,
                                viewModel = viewModel,
                                navController = navController,
                                onBack = { navController.popBackStack() },
                                onNavigateToWatch = { epSlug, title ->
                                    navController.navigate("watch/$epSlug/${java.net.URLEncoder.encode(title, "UTF-8")}")
                                }
                            )
                        }
                        composable(
                            route = "watch/{slug}/{title}?joinRoom={joinRoom}",
                            arguments = listOf(
                                navArgument("slug") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType },
                                navArgument("joinRoom") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val epSlug = backStackEntry.arguments?.getString("slug") ?: ""
                            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val title = java.net.URLDecoder.decode(encodedTitle, "UTF-8")
                            val joinRoomCode = backStackEntry.arguments?.getString("joinRoom")?.takeIf { it.isNotBlank() }
                            WatchScreen(
                                episodeSlug = epSlug,
                                animeTitle = title,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                autoJoinRoomCode = joinRoomCode,
                                onLoginRequired = { navController.navigate("auth") }
                            )
                        }
                        composable("nobar_list") {
                            NobarListScreen(
                                viewModel = viewModel,
                                onJoinRoom = { slug, title, roomCode ->
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("watch/$slug/$encodedTitle?joinRoom=$roomCode")
                                }
                            )
                        }
                        composable("request_anime") {
                            RequestedAnimeListScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onItemClick = { id -> navController.navigate("request_anime_detail/$id") }
                            )
                        }
                        composable(
                            route = "request_anime_detail/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            RequestedAnimeDetailScreen(
                                id = id,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onWatch = { animeId -> navController.navigate("request_anime_watch/$animeId") }
                            )
                        }
                        composable(
                            route = "request_anime_watch/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            RequestedAnimeWatchScreen(
                                id = id,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("auth") {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = { navController.popBackStack() },
                                onGuestMode = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "reset_password?token={token}",
                            arguments = listOf(
                                navArgument("token") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val encodedToken = backStackEntry.arguments?.getString("token") ?: ""
                            val token = if (encodedToken.isNotBlank())
                                java.net.URLDecoder.decode(encodedToken, "UTF-8")
                            else null
                            ResetPasswordScreen(
                                accessToken = token,
                                viewModel = viewModel,
                                onDone = {
                                    navController.navigate("auth") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToSecurity = { navController.navigate("keamanan") }
                            )
                        }
                        composable(
                            route = "user_profile/{userId}",
                            arguments = listOf(
                                navArgument("userId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId") ?: ""
                            UserProfileScreen(
                                viewModel = viewModel,
                                userId = userId,
                                onBack = { navController.popBackStack() },
                                onEditOwnProfile = {
                                    navController.navigate("profile")
                                },
                                onNavigateToAnime = { slug -> navController.navigate("detail/$slug") },
                                onOpenPrivateChat = { otherUserId -> navController.navigate("private_chat/$otherUserId") },
                                onOpenPremiumList = { navController.navigate("premium_list") }
                            )
                        }
                        composable("user_list") {
                            UserListScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onUserClick = { userId -> navController.navigate("user_profile/$userId") }
                            )
                        }
                        composable("friends") {
                            FriendsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenChat = { otherUserId -> navController.navigate("private_chat/$otherUserId") }
                            )
                        }
                        composable(
                            route = "private_chat/{userId}",
                            arguments = listOf(
                                navArgument("userId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val otherUserId = backStackEntry.arguments?.getString("userId") ?: ""
                            PrivateChatScreen(
                                viewModel = viewModel,
                                otherUserId = otherUserId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("clans") {
                            ClanScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onTopUpClick = { navController.navigate("diamond_topup") },
                                onQuizClick = { navController.navigate("quiz") }
                            )
                        }
                        composable("gacha") {
                            GachaScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onTopUpClick = { navController.navigate("diamond_topup") },
                                onTradeClick = { navController.navigate("trade") }
                            )
                        }
                        composable("trade") {
                            TradeScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("quiz") {
                            QuizScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onJoinClanClick = { navController.navigate("clans") },
                                onTopUpClick = { navController.navigate("diamond_topup") }
                            )
                        }
                        composable("diamond_topup") {
                            DiamondTopUpScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("premium_list") {
                            PremiumListScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("admin") {
                            AdminPanelScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("tampilan") {
                            TampilanScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("keamanan") {
                            SecurityScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("sumber_data") {
                            SumberDataScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("watch_history") {
                            com.example.ui.WatchHistoryScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("downloads") {
                            com.example.ui.DownloadsScreen(
                                viewModel = viewModel,
                                onPlayOffline = { record ->
                                    navController.navigate("offline_player/${record.downloadId}")
                                },
                                onLoginRequired = { navController.navigate("auth") }
                            )
                        }
                        composable(
                            route = "offline_player/{downloadId}",
                            arguments = listOf(navArgument("downloadId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val downloadId = backStackEntry.arguments?.getLong("downloadId") ?: -1L
                            val downloads by viewModel.downloads.collectAsState()
                            val record = downloads.firstOrNull { it.downloadId == downloadId }
                            if (record != null) {
                                com.example.ui.OfflinePlayerScreen(
                                    record = record,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                navController.popBackStack()
                            }
                        }
                    }
                }

                // Mini player in-app — overlay di ATAS NavHost, jadi tetap kelihatan &
                // tetap muter walaupun user pindah tab/halaman lain di dalam Aniku.
                miniPlayerData?.let { data ->
                    com.example.ui.MiniPlayerOverlay(
                        data = data,
                        onExpand = { resumeMs ->
                            viewModel.expandMiniPlayer(resumeMs)
                            val encodedTitle = java.net.URLEncoder.encode(data.animeTitle, "UTF-8")
                            navController.navigate("watch/${data.episodeSlug}/$encodedTitle")
                        },
                        onClose = { viewModel.closeMiniPlayer() }
                    )
                }

                // Bottom nav "Floating" — overlay beneran ngambang di atas konten,
                // niru persis FloatingBottomNavigation-nya Kuroflix (bukan lewat
                // Scaffold.bottomBar yang reserve area solid).
                if (showBottomBar && navStyle == "Floating") {
                    CurvedBottomNav(
                        mainNavItems = mainNavItems,
                        currentRoute = currentRoute,
                        isSheetRouteActive = isSheetRouteActive,
                        hasUnreadChat = hasUnreadChat,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onMoreClick = { showMoreSheet = true },
                        navStyle = navStyle,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                } // end Box wrapper
            }
            } // end else (unlocked)
        }
    }
}
