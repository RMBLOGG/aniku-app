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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
class MainActivity : ComponentActivity() {

    companion object {
        var isWatchingDirectStream = false
        var pipExoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
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

        // Immersive sticky: nav bar hilang, muncul lagi kalau swipe dari bawah, lalu auto-hilang
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
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
            else -> null
        }

        setContent {
            val isDark by viewModel.isDark.collectAsState()
            val accentColorName by viewModel.accentColorName.collectAsState()
            val textSizeScale by viewModel.textSize.collectAsState()

            MyApplicationTheme(
                darkTheme = isDark,
                accentName = accentColorName,
                textScale = textSizeScale
            ) {
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

                if (appLockEnabled && !isUnlocked) {
                    LockScreen(
                        lockType = appLockType,
                        savedPin = appPin,
                        onUnlocked = { isUnlocked = true }
                    )
                } else {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Navigate ke deep link kalau ada (dari notifikasi)
                LaunchedEffect(deepLinkRoute) {
                    deepLinkRoute?.let {
                        navController.navigate(it) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                        }
                    }
                }

                val bottomRoutes = listOf("home", "search", "explore", "bookmark", "schedule", "chat", "feed")
                val showBottomBar = currentRoute in bottomRoutes
                var showMoreSheet by remember { mutableStateOf(false) }
                val hasUnreadChat by viewModel.hasUnreadChat.collectAsState()
                val hasNewDonation by viewModel.hasNewDonation.collectAsState()
                val latestDonation by viewModel.latestDonation.collectAsState()
                var showDonationBanner by remember { mutableStateOf(false) }

                // Tampilkan banner saat ada donasi baru
                LaunchedEffect(hasNewDonation) {
                    if (hasNewDonation) {
                        showDonationBanner = true
                        kotlinx.coroutines.delay(5000)
                        showDonationBanner = false
                        viewModel.markDonationSeen()
                    }
                }

                // Banner donasi popup
                if (showDonationBanner && latestDonation != null) {
                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose {} }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            .zIndex(999f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDonationBanner = false
                                    viewModel.markDonationSeen()
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("☕", fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Support baru masuk!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${latestDonation!!.supporter_name} men-support ${latestDonation!!.amount} ${latestDonation!!.unit ?: "cup"}! 🙏",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showDonationBanner = false
                                        viewModel.markDonationSeen()
                                    },
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
                    Triple("feed", "Feed", Icons.Default.GridView),
                    Triple("schedule", "Jadwal", Icons.Default.DateRange),
                    Triple("top_supporter", "Top Supporter", Icons.Default.EmojiEvents),
                )
                val sheetRoutes = sheetNavItems.map { it.first }
                val isSheetRouteActive = currentRoute in sheetRoutes

                if (showMoreSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showMoreSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surface,
                        dragHandle = {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier
                                    .padding(vertical = 10.dp)
                                    .size(width = 32.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            )
                        }
                    ) {
                        Text(
                            text = "Menu Lainnya",
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            letterSpacing = 1.sp,
                            modifier = androidx.compose.ui.Modifier.padding(start = 18.dp, bottom = 6.dp)
                        )
                        val sheetItemColors = mapOf(
                            "chat" to Triple(0xFF1a2233, 0xFF5b9cf6, "Ngobrol bareng komunitas"),
                            "feed" to Triple(0xFF2a1a1a, 0xFFe53935, "Postingan dari pengguna"),
                            "schedule" to Triple(0xFF1a2a1a, 0xFF4caf50, "Jadwal tayang anime"),
                            "top_supporter" to Triple(0xFF2a2000, 0xFFFFD700, "Daftar donatur terbaik Aniku"),
                        )
                        sheetNavItems.forEach { (route, label, icon) ->
                            val meta = sheetItemColors[route]
                            val bgColor = Color(meta?.first ?: 0xFF1e1e1e)
                            val iconColor = Color(meta?.second ?: 0xFFaaaaaa)
                            val desc = meta?.third ?: ""
                            val isActive = currentRoute == route
                            androidx.compose.foundation.layout.Row(
                                modifier = androidx.compose.ui.Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMoreSheet = false
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .background(if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bgColor),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = iconColor,
                                        modifier = androidx.compose.ui.Modifier.size(22.dp)
                                    )
                                    if (route == "chat" && hasUnreadChat) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = androidx.compose.ui.Modifier
                                                .size(10.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(MaterialTheme.colorScheme.error)
                                                .align(androidx.compose.ui.Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                        )
                                    }
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(14.dp))
                                androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontSize = 15.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    modifier = androidx.compose.ui.Modifier.size(20.dp)
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showBottomBar) {
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
                                onMoreClick = { showMoreSheet = true }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(
                            bottom = if (showBottomBar) 76.dp else 0.dp
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
                            route = "watch/{slug}/{title}",
                            arguments = listOf(
                                navArgument("slug") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val epSlug = backStackEntry.arguments?.getString("slug") ?: ""
                            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val title = java.net.URLDecoder.decode(encodedTitle, "UTF-8")
                            WatchScreen(
                                episodeSlug = epSlug,
                                animeTitle = title,
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
                        composable("profile") {
                            ProfileScreen(
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
                    }
                }
            }
            } // end else (unlocked)
        }
    }
}
