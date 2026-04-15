package com.gitsync.git

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictDetector @Inject constructor(private val gitSyncManager: GitSyncManager) {

    suspend fun hasConflict(localPath: String): Boolean =
        gitSyncManager.getLocalChanges(localPath).isNotEmpty()

    suspend fun getModifiedFiles(localPath: String): List<String> =
        gitSyncManager.getLocalChanges(localPath)
}
