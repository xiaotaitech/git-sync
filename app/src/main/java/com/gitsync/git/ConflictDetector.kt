package com.gitsync.git

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictDetector @Inject constructor(private val gitSyncManager: GitSyncManager) {

    /** Returns true if local path has uncommitted changes. */
    fun hasConflict(localPath: String): Boolean =
        gitSyncManager.getLocalChanges(localPath).isNotEmpty()

    /** Returns the list of modified file names for display in ConflictDialog. */
    fun getModifiedFiles(localPath: String): List<String> =
        gitSyncManager.getLocalChanges(localPath)
}
