package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.network.AnikuViewModel
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = AnikuViewModel(this)

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

                val bottomRoutes = listOf("home", "search", "explore", "bookmark", "schedule", "chat", "feed")
                val showBottomBar = currentRoute in bottomRoutes
                var showMoreSheet by remember { mutableStateOf(false) }
                val hasUnreadChat by viewModel.hasUnreadChat.collectAsState()

                // Tutup sheet kalau navigasi berubah
                LaunchedEffect(currentRoute) { showMoreSheet = false }

                val mainNavItems = listOf(
                    Triple("search", "Cari", Icons.Default.Search),
                    Triple("explore", "Eksplor", Icons.Default.Apps),
                    Triple("bookmark", "Bookmark", Icons.Default.Favorite),
                )
                val sheetNavItems = listOf(
                    Triple("chat", "Chat", Icons.Default.Chat),
                    Triple("feed", "Feed", Icons.Default.GridView),
                    Triple("schedule", "Jadwal", Icons.Default.DateRange),
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
                            bottom = if (showBottomBar) 88.dp else 0.dp
                        )
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
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") }
                            )
                        }
                        composable("explore") {
                            ExploreScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { slug -> navController.navigate("detail/$slug") }
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
                                onBack = { navController.popBackStack() }
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
                    }
                }
            }
            } // end else (unlocked)
        }
    }
}
