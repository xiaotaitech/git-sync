package com.gitsync.ui.repolist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.ui.conflict.ConflictDialog

@Composable
fun RepoListScreen(
    contentPadding: PaddingValues,
    onNavigateToAdd: () -> Unit,
    viewModel: RepoListViewModel = hiltViewModel()
) {
    val repos by viewModel.repos.collectAsState()
    val syncingIds by viewModel.syncingIds.collectAsState()
    val conflictState by viewModel.conflictState.collectAsState()

    val okCount = repos.count { it.syncStatus == "idle" }
    val problemCount = repos.count { it.syncStatus == "conflict" || it.syncStatus == "error" }
    val syncingCount = syncingIds.size

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
                    RepoSummaryRow(
                        total = repos.size,
                        okCount = okCount,
                        problemCount = problemCount,
                        syncingCount = syncingCount
                    )
                    Spacer(Modifier.height(4.dp))
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
private fun RepoSummaryRow(
    total: Int,
    okCount: Int,
    problemCount: Int,
    syncingCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = "$total 个仓库",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (syncingCount > 0) {
            SummaryChip(
                modifier = Modifier.weight(1f),
                icon = { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) },
                label = "同步中 $syncingCount",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else if (problemCount > 0) {
            SummaryChip(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = "$problemCount 个问题",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            SummaryChip(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = "全部已同步",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun SummaryChip(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}
