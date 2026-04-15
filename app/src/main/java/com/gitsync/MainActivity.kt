package com.gitsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.gitsync.ui.navigation.AppNavigation
import com.gitsync.ui.repolist.RepoListViewModel
import com.gitsync.ui.theme.GitSyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val repoListViewModel: RepoListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleConflictIntent()
        setContent {
            GitSyncTheme {
                AppNavigation(repoListViewModel = repoListViewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleConflictIntent()
    }

    private fun handleConflictIntent() {
        val repoId = intent?.getLongExtra("conflict_repo_id", -1L) ?: -1L
        val files = intent?.getStringArrayListExtra("conflict_files")
        if (repoId != -1L && !files.isNullOrEmpty()) {
            repoListViewModel.showConflict(repoId, files)
            intent?.removeExtra("conflict_repo_id")
        }
    }
}
