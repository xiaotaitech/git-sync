package com.gitsync.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gitsync.GitSyncApp
import com.gitsync.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notifySuccess(repoName: String, message: String) {
        val n = NotificationCompat.Builder(context, GitSyncApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(repoName)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(repoName.hashCode(), n)
    }

    fun notifyError(repoName: String, error: String) {
        val n = NotificationCompat.Builder(context, GitSyncApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Sync failed: $repoName")
            .setContentText(error)
            .setAutoCancel(true)
            .build()
        manager.notify(repoName.hashCode() + 1, n)
    }

    fun notifyConflict(repoId: Long, repoName: String, modifiedFiles: List<String>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conflict_repo_id", repoId)
            putStringArrayListExtra("conflict_files", ArrayList(modifiedFiles))
        }
        val pi = PendingIntent.getActivity(
            context, repoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, GitSyncApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("冲突: $repoName")
            .setContentText("${modifiedFiles.size} 个文件有本地修改，点击解决")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        manager.notify(repoId.toInt() + 1000, n)
    }
}
