package com.gitsync.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Conflict(val modifiedFiles: List<String>) : SyncResult()
    data class Error(val error: String) : SyncResult()
}

@Singleton
class GitSyncManager @Inject constructor() {

    /** Clone a remote repo into localPath. Throws on failure. */
    fun clone(remoteUrl: String, localPath: String, pat: String) {
        val dir = File(localPath)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        Git.cloneRepository()
            .setURI(remoteUrl)
            .setDirectory(dir)
            .setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
            .call()
            .close()
    }

    /**
     * Pull latest from remote.
     * Returns Conflict if local changes detected, Success/Error otherwise.
     */
    fun pull(localPath: String, pat: String): SyncResult {
        val dir = File(localPath)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            return SyncResult.Error("Not a git repository: $localPath")
        }
        return try {
            val git = Git.open(dir)
            val changes = getLocalChanges(localPath)
            if (changes.isNotEmpty()) {
                git.close()
                return SyncResult.Conflict(changes)
            }
            val result = git.pull()
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
                .call()
            git.close()
            if (result.isSuccessful) {
                val msg = if (result.mergeResult?.mergedCommits?.isNotEmpty() == true)
                    "Pulled new commits" else "Already up to date"
                SyncResult.Success(msg)
            } else {
                SyncResult.Error("Pull failed: ${result.mergeResult?.mergeStatus}")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Force pull: discard local changes, hard reset, then pull. */
    fun forcePull(localPath: String, pat: String): SyncResult {
        val dir = File(localPath)
        return try {
            val git = Git.open(dir)
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call()
            git.clean().setForce(true).setCleanDirectories(true).call()
            git.close()
            pull(localPath, pat)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Returns list of modified/untracked file paths in the working tree. */
    fun getLocalChanges(localPath: String): List<String> {
        val dir = File(localPath)
        if (!File(dir, ".git").exists()) return emptyList()
        return try {
            val git = Git.open(dir)
            val status = git.status().call()
            git.close()
            (status.modified + status.untracked + status.added + status.missing).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
