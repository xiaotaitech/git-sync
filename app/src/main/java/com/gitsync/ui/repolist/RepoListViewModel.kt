package com.gitsync.ui.repolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.RepoEntity
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import com.gitsync.git.GitSyncManager
import com.gitsync.git.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RepoListViewModel @Inject constructor(
    private val repoDao: RepoDao,
    private val syncLogDao: SyncLogDao,
    private val gitSyncManager: GitSyncManager,
    private val prefsRepository: PrefsRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val repos: StateFlow<List<RepoEntity>> = repoDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncingIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingIds: StateFlow<Set<Long>> = _syncingIds.asStateFlow()

    private val _conflictState = MutableStateFlow<Pair<Long, List<String>>?>(null)
    val conflictState: StateFlow<Pair<Long, List<String>>?> = _conflictState.asStateFlow()

    fun syncRepo(repo: RepoEntity) {
        viewModelScope.launch {
            _syncingIds.update { it + repo.id }
            repoDao.update(repo.copy(syncStatus = "syncing"))

            val pat = prefsRepository.getPat().first()
            val result = gitSyncManager.sync(repo.localPath, pat)

            val (status, logMsg, success) = when (result) {
                is SyncResult.Success -> Triple("idle", result.message, true)
                is SyncResult.Conflict -> {
                    _conflictState.value = Pair(repo.id, result.modifiedFiles)
                    Triple("conflict", "Conflict: ${result.modifiedFiles.size} file(s)", false)
                }
                is SyncResult.Error -> Triple("error", result.error, false)
            }

            repoDao.update(repo.copy(syncStatus = status, lastSyncTime = System.currentTimeMillis()))
            syncLogDao.insert(
                SyncLogEntity(
                    repoId = repo.id, repoName = repo.name,
                    timestamp = System.currentTimeMillis(), success = success, message = logMsg
                )
            )
            _syncingIds.update { it - repo.id }
        }
    }

    fun resolveConflictForce(repoId: Long) {
        viewModelScope.launch {
            val repo = repoDao.getById(repoId) ?: return@launch
            val pat = prefsRepository.getPat().first()
            _conflictState.value = null
            _syncingIds.update { it + repoId }
            repoDao.update(repo.copy(syncStatus = "syncing"))
            val result = gitSyncManager.forcePull(repo.localPath, pat)
            val (status, logMsg, success) = when (result) {
                is SyncResult.Success -> Triple("idle", result.message, true)
                is SyncResult.Conflict -> Triple("conflict", "Conflict persists", false)
                is SyncResult.Error -> Triple("error", result.error, false)
            }
            repoDao.update(repo.copy(syncStatus = status, lastSyncTime = System.currentTimeMillis()))
            syncLogDao.insert(SyncLogEntity(repoId = repo.id, repoName = repo.name,
                timestamp = System.currentTimeMillis(), success = success, message = logMsg))
            _syncingIds.update { it - repoId }
        }
    }

    fun resolveConflictSkip(repoId: Long) {
        viewModelScope.launch {
            val repo = repoDao.getById(repoId) ?: return@launch
            _conflictState.value = null
            repoDao.update(repo.copy(syncStatus = "conflict"))
        }
    }

    fun deleteRepo(repo: RepoEntity) {
        viewModelScope.launch {
            workManager.cancelUniqueWork("sync_${repo.id}")
            syncLogDao.deleteForRepo(repo.id)
            repoDao.delete(repo)
        }
    }

    fun showConflict(repoId: Long, files: List<String>) {
        _conflictState.value = Pair(repoId, files)
    }
}
