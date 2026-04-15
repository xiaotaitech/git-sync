package com.gitsync.ui.repolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (repos.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有仓库", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text("点击 + 添加 GitHub 仓库", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repos, key = { it.id }) { repo ->
                    RepoCard(
                        repo = repo,
                        isSyncing = repo.id in syncingIds,
                        onSyncClick = { viewModel.syncRepo(repo) }
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
