package com.gitsync.ui.repolist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.ui.conflict.ConflictDialog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RepoListScreen(
    contentPadding: PaddingValues,
    onNavigateToAdd: () -> Unit,
    viewModel: RepoListViewModel = hiltViewModel()
) {
    val repos by viewModel.repos.collectAsState()
    val syncingIds by viewModel.syncingIds.collectAsState()
    val conflictState by viewModel.conflictState.collectAsState()

    val problemCount = repos.count { it.syncStatus == "conflict" || it.syncStatus == "error" }
    val syncingCount = syncingIds.size
    val lastSyncTime = repos.filter { it.lastSyncTime > 0 }.maxOfOrNull { it.lastSyncTime }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (repos.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.FolderOff,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(16.dp))
                Text("还没有仓库", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                Spacer(Modifier.height(4.dp))
                Text("点击右下角 + 添加 GitHub 仓库", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f))
                Spacer(Modifier.height(24.dp))
                FilledTonalButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加仓库")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RepoHeroBanner(
                        total = repos.size,
                        problemCount = problemCount,
                        syncingCount = syncingCount,
                        lastSyncTime = lastSyncTime,
                        onSyncAll = { viewModel.syncAll() }
                    )
                }
                items(repos, key = { it.id }) { repo ->
                    RepoCard(
                        repo = repo,
                        isSyncing = repo.id in syncingIds,
                        onSyncClick = { viewModel.syncRepo(repo) },
                        onDeleteClick = { viewModel.deleteRepo(repo) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加仓库")
        }

        conflictState?.let { (repoId, files) ->
            ConflictDialog(
                modifiedFiles = files,
                onForce = { viewModel.resolveConflictForce(repoId) },
                onSkip = { viewModel.resolveConflictSkip(repoId) }
            )
        }
    }
}

@Composable
private fun RepoHeroBanner(
    total: Int,
    problemCount: Int,
    syncingCount: Int,
    lastSyncTime: Long?,
    onSyncAll: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val (statusLabel, statusIcon, statusColor) = when {
        syncingCount > 0 -> Triple("同步中 $syncingCount 个", Icons.Default.Sync, MaterialTheme.colorScheme.primaryContainer)
        problemCount > 0 -> Triple("$problemCount 个问题", Icons.Default.Warning, MaterialTheme.colorScheme.errorContainer)
        else -> Triple("全部已同步", Icons.Default.CheckCircle, MaterialTheme.colorScheme.tertiaryContainer)
    }
    val statusContentColor = when {
        syncingCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        problemCount > 0 -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    val lastSyncStr = lastSyncTime?.let {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(primary, tertiary),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .padding(20.dp)
    ) {
        // Decorative circle background
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-20).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = 20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column {
            Text(
                "共 $total 个仓库",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$total",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 52.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "repos",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(statusIcon, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = statusContentColor)
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                        color = statusContentColor)
                }

                if (lastSyncStr != null) {
                    Text(
                        "上次 $lastSyncStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Sync all button
                FilledTonalIconButton(
                    onClick = onSyncAll,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "全部同步",
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
