package com.gitsync.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun clone(remoteUrl: String, localPath: String, pat: String) = withContext(Dispatchers.IO) {
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

    suspend fun pull(localPath: String, pat: String): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(localPath)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            return@withContext SyncResult.Error("Not a git repository: $localPath")
        }
        try {
            val git = Git.open(dir)
            val changes = getLocalChangesInternal(git)
            if (changes.isNotEmpty()) {
                git.close()
                return@withContext SyncResult.Conflict(changes)
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

    suspend fun forcePull(localPath: String, pat: String): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(localPath)
        try {
            val git = Git.open(dir)
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call()
            git.clean().setForce(true).setCleanDirectories(true).call()
            git.close()
            pull(localPath, pat)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getLocalChanges(localPath: String): List<String> = withContext(Dispatchers.IO) {
        val dir = File(localPath)
        if (!File(dir, ".git").exists()) return@withContext emptyList()
        try {
            val git = Git.open(dir)
            val result = getLocalChangesInternal(git)
            git.close()
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getLocalChangesInternal(git: Git): List<String> {
        val status = git.status().call()
        return (status.modified + status.untracked + status.added + status.missing).toList()
    }
}
