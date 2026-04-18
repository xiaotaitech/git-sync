package com.gitsync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.ui.addrepo.AddRepoScreen
import com.gitsync.ui.repolist.RepoListScreen
import com.gitsync.ui.repolist.RepoListViewModel
import com.gitsync.ui.settings.SettingsScreen
import com.gitsync.ui.synclog.SyncLogScreen

sealed class NavRoute(val route: String, val label: String, val icon: ImageVector) {
    object Repos : NavRoute("repos", "仓库", Icons.Default.FolderOpen)
    object Logs : NavRoute("logs", "日志", Icons.Default.History)
    object Settings : NavRoute("settings", "设置", Icons.Default.Settings)
}

private val topLevelRoutes = listOf(NavRoute.Repos, NavRoute.Logs, NavRoute.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(repoListViewModel: RepoListViewModel? = null) {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val reposViewModel: RepoListViewModel = repoListViewModel ?: hiltViewModel()

    val title = when (currentRoute) {
        NavRoute.Repos.route -> "仓库"
        NavRoute.Logs.route -> "同步日志"
        NavRoute.Settings.route -> "设置"
        else -> ""
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (currentRoute in topLevelRoutes.map { it.route }) {
                LargeTopAppBar(
                    title = { Text(title) },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = {
            NavigationBar {
                topLevelRoutes.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
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
