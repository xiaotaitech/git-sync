package com.gitsync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import com.gitsync.git.GitSyncManager
import com.gitsync.git.SyncResult
import com.gitsync.notification.SyncNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val gitSyncManager: GitSyncManager,
    private val syncLogDao: SyncLogDao,
    private val repoDao: RepoDao,
    private val prefsRepository: PrefsRepository,
    private val syncNotifier: SyncNotifier
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repoId = inputData.getLong(KEY_REPO_ID, -1L)
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val remoteUrl = inputData.getString(KEY_REMOTE_URL) ?: return Result.failure()
        val repoName = inputData.getString(KEY_REPO_NAME) ?: remoteUrl

        // Read PAT from DataStore at runtime — not stored in WorkManager InputData
        val pat = prefsRepository.getPat().first()
        if (pat.isBlank()) {
            syncNotifier.notifyError(repoName, "PAT not configured")
            return Result.failure()
        }

        val syncResult = gitSyncManager.sync(localPath, pat)

        val log = SyncLogEntity(
            repoId = repoId,
            repoName = repoName,
            timestamp = System.currentTimeMillis(),
            success = syncResult is SyncResult.Success,
            message = when (syncResult) {
                is SyncResult.Success -> syncResult.message
                is SyncResult.Conflict -> "Conflict: ${syncResult.modifiedFiles.size} file(s) modified"
                is SyncResult.Error -> syncResult.error
            }
        )
        syncLogDao.insert(log)

        // Update RepoEntity status in Room so UI card reflects background sync result
        repoDao.getById(repoId)?.let { repo ->
            val (status) = when (syncResult) {
                is SyncResult.Success -> Triple("idle", "", true)
                is SyncResult.Conflict -> Triple("conflict", "", false)
                is SyncResult.Error -> Triple("error", "", false)
            }
            repoDao.update(repo.copy(syncStatus = status, lastSyncTime = System.currentTimeMillis()))
        }

        return when (syncResult) {
            is SyncResult.Success -> {
                syncNotifier.notifySuccess(repoName, syncResult.message)
                Result.success()
            }
            is SyncResult.Conflict -> {
                syncNotifier.notifyConflict(repoId, repoName, syncResult.modifiedFiles)
                Result.success()
            }
            is SyncResult.Error -> {
                syncNotifier.notifyError(repoName, syncResult.error)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_REPO_ID = "repo_id"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_REMOTE_URL = "remote_url"
        const val KEY_REPO_NAME = "repo_name"
        // KEY_PAT removed — PAT is read from DataStore at runtime

        fun buildWorkRequest(
            repoId: Long,
            repoName: String,
            localPath: String,
            remoteUrl: String,
            intervalMinutes: Int
        ): PeriodicWorkRequest {
            val data = workDataOf(
                KEY_REPO_ID to repoId,
                KEY_REPO_NAME to repoName,
                KEY_LOCAL_PATH to localPath,
                KEY_REMOTE_URL to remoteUrl
                // PAT not included
            )
            return PeriodicWorkRequestBuilder<SyncWorker>(
                maxOf(intervalMinutes.toLong(), 15L), java.util.concurrent.TimeUnit.MINUTES
            ).setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        }
    }
}
