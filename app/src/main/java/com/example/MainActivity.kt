package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.network.AnikuViewModel
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

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
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomRoutes = listOf("home", "search", "explore", "bookmark", "schedule", "chat")
                val showBottomBar = currentRoute in bottomRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .testTag("bottom_nav_bar"),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                val currentDestination = navBackStackEntry?.destination
                                val items = listOf(
                                    Triple("home", "Home", Icons.Default.Home),
                                    Triple("search", "Cari", Icons.Default.Search),
                                    Triple("explore", "Eksplor", Icons.Default.PlayArrow),
                                    Triple("bookmark", "Bookmark", Icons.Default.Favorite),
                                    Triple("schedule", "Jadwal", Icons.Default.DateRange),
                                    Triple("chat", "Chat", Icons.Default.Message)
                                )

                                items.forEach { (route, label, icon) ->
                                    val isSelected = currentDestination?.route == route
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentDestination?.route != route) {
                                                navController.navigate(route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.testTag("nav_item_$route")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(
                            bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp
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
                    }
                }
            }
        }
    }
}
