package com.gitsync.ui.addrepo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.RepoEntity
import com.gitsync.git.GitSyncManager
import com.gitsync.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddRepoUiState(
    val remoteUrl: String = "",
    val localPath: String = "",
    val intervalMinutes: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class AddRepoViewModel @Inject constructor(
    private val repoDao: RepoDao,
    private val prefsRepository: PrefsRepository,
    private val gitSyncManager: GitSyncManager,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRepoUiState())
    val uiState: StateFlow<AddRepoUiState> = _uiState.asStateFlow()

    fun onUrlChange(v: String) { _uiState.update { it.copy(remoteUrl = v, error = null) } }
    fun onPathChange(v: String) { _uiState.update { it.copy(localPath = v, error = null) } }
    fun onIntervalChange(v: Int) { _uiState.update { it.copy(intervalMinutes = v) } }

    fun save() {
        val state = _uiState.value
        if (state.remoteUrl.isBlank()) {
            _uiState.update { it.copy(error = "请输入仓库 URL") }
            return
        }
        if (state.localPath.isBlank()) {
            _uiState.update { it.copy(error = "请选择本地文件夹") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val pat = prefsRepository.getPat().first()
            if (pat.isBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "请先在设置中配置 PAT") }
                return@launch
            }
            try {
                val name = state.remoteUrl.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
                gitSyncManager.clone(state.remoteUrl, state.localPath, pat)
                val entity = RepoEntity(
                    name = name,
                    remoteUrl = state.remoteUrl,
                    localPath = state.localPath,
                    intervalMinutes = state.intervalMinutes,
                    lastSyncTime = System.currentTimeMillis(),
                    syncStatus = "idle"
                )
                val id = repoDao.insert(entity)
                if (state.intervalMinutes > 0) {
                    val workRequest = SyncWorker.buildWorkRequest(
                        repoId = id,
                        repoName = entity.name,
                        localPath = entity.localPath,
                        remoteUrl = entity.remoteUrl,
                        intervalMinutes = state.intervalMinutes
                    )
                    workManager.enqueueUniquePeriodicWork(
                        "sync_$id",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                    )
                }
                _uiState.update { it.copy(isLoading = false, done = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Clone 失败") }
            }
        }
    }
}
