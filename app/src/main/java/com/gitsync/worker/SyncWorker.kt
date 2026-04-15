package com.gitsync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import com.gitsync.git.GitSyncManager
import com.gitsync.git.SyncResult
import com.gitsync.notification.SyncNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val gitSyncManager: GitSyncManager,
    private val syncLogDao: SyncLogDao,
    private val syncNotifier: SyncNotifier
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repoId = inputData.getLong(KEY_REPO_ID, -1L)
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val remoteUrl = inputData.getString(KEY_REMOTE_URL) ?: return Result.failure()
        val pat = inputData.getString(KEY_PAT) ?: return Result.failure()
        val repoName = inputData.getString(KEY_REPO_NAME) ?: remoteUrl

        val syncResult = gitSyncManager.pull(localPath, pat)

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

        return when (syncResult) {
            is SyncResult.Success -> {
                syncNotifier.notifySuccess(repoName, syncResult.message)
                Result.success()
            }
            is SyncResult.Conflict -> {
                syncNotifier.notifyConflict(repoId, repoName, syncResult.modifiedFiles)
                Result.success() // Worker succeeded; conflict handled via notification
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
        const val KEY_PAT = "pat"
        const val KEY_REPO_NAME = "repo_name"

        fun buildWorkRequest(
            repoId: Long,
            repoName: String,
            localPath: String,
            remoteUrl: String,
            pat: String,
            intervalMinutes: Int
        ): PeriodicWorkRequest {
            val data = workDataOf(
                KEY_REPO_ID to repoId,
                KEY_REPO_NAME to repoName,
                KEY_LOCAL_PATH to localPath,
                KEY_REMOTE_URL to remoteUrl,
                KEY_PAT to pat
            )
            return PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.toLong(), java.util.concurrent.TimeUnit.MINUTES
            ).setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        }
    }
}
