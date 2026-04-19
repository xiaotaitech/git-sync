package com.gitsync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.ui.addrepo.AddRepoScreen
import com.gitsync.ui.repolist.RepoListScreen
import com.gitsync.ui.repolist.RepoListViewModel
import com.gitsync.ui.settings.SettingsScreen
import com.gitsync.ui.synclog.SyncLogScreen

sealed class NavRoute(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Repos : NavRoute("repos", "仓库", Icons.Outlined.FolderOpen, Icons.Filled.FolderOpen)
    object Logs : NavRoute("logs", "日志", Icons.Outlined.History, Icons.Filled.History)
    object Settings : NavRoute("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
}

private val topLevelRoutes = listOf(NavRoute.Repos, NavRoute.Logs, NavRoute.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(repoListViewModel: RepoListViewModel? = null) {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route

    val reposViewModel: RepoListViewModel = repoListViewModel ?: hiltViewModel()

    val title = when (currentRoute) {
        NavRoute.Repos.route -> "仓库"
        NavRoute.Logs.route -> "同步日志"
        NavRoute.Settings.route -> "设置"
        else -> ""
    }

    val barColor = MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            if (currentRoute in topLevelRoutes.map { it.route }) {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = barColor,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = barColor) {
                topLevelRoutes.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) dest.selectedIcon else dest.icon,
                                contentDescription = dest.label
                            )
                        },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = NavRoute.Repos.route) {
            composable(NavRoute.Repos.route) {
                RepoListScreen(
                    contentPadding = padding,
                    onNavigateToAdd = { navController.navigate("add_repo") },
                    viewModel = reposViewModel
                )
            }
            composable("add_repo") { AddRepoScreen(onBack = { navController.popBackStack() }) }
            composable(NavRoute.Logs.route) { SyncLogScreen(contentPadding = padding) }
            composable(NavRoute.Settings.route) { SettingsScreen(contentPadding = padding) }
        }
    }
}
