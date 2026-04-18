package com.gitsync.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
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
            val creds = UsernamePasswordCredentialsProvider(pat, "")

            // 1. Fetch latest from remote without merging
            git.fetch().setCredentialsProvider(creds).call()

            val localChanges = getLocalChangesInternal(git).toSet()

            if (localChanges.isEmpty()) {
                // Clean working tree — just pull
                val result = git.pull().setCredentialsProvider(creds).call()
                git.close()
                return@withContext if (result.isSuccessful) {
                    val msg = if (result.mergeResult?.mergedCommits?.isNotEmpty() == true)
                        "Pulled new commits" else "Already up to date"
                    SyncResult.Success(msg)
                } else {
                    SyncResult.Error("Pull failed: ${result.mergeResult?.mergeStatus}")
                }
            }

            // 2. Find files changed on remote since our HEAD
            val repo = git.repository
            val headId = repo.resolve("HEAD")
            val fetchHeadId = repo.resolve("FETCH_HEAD")

            if (fetchHeadId == null || headId == fetchHeadId) {
                // No new remote commits — nothing to merge, local changes are fine
                git.close()
                return@withContext SyncResult.Success("Already up to date")
            }

            val remoteChanges = getChangedFilesBetween(git, headId, fetchHeadId)

            // 3. Check for overlap between local and remote changes
            val conflicting = localChanges.intersect(remoteChanges)

            if (conflicting.isNotEmpty()) {
                git.close()
                return@withContext SyncResult.Conflict(conflicting.toList())
            }

            // 4. No overlap — stash local changes, pull, pop stash
            git.stashCreate().call()
            val pullResult = git.pull().setCredentialsProvider(creds).call()
            if (!pullResult.isSuccessful) {
                // Pull failed — restore stash and report error
                git.stashApply().call()
                git.close()
                return@withContext SyncResult.Error("Pull failed: ${pullResult.mergeResult?.mergeStatus}")
            }
            git.stashApply().call()
            git.close()
            SyncResult.Success("Pulled new commits (local changes preserved)")
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

    /** Returns set of file paths changed between two commits (e.g. HEAD..FETCH_HEAD). */
    private fun getChangedFilesBetween(git: Git, fromId: ObjectId, toId: ObjectId): Set<String> {
        val repo = git.repository
        val reader = repo.newObjectReader()
        return try {
            val oldTree: AbstractTreeIterator = CanonicalTreeParser().also { parser ->
                val treeId = repo.parseCommit(fromId).tree.id
                parser.reset(reader, treeId)
            }
            val newTree: AbstractTreeIterator = CanonicalTreeParser().also { parser ->
                val treeId = repo.parseCommit(toId).tree.id
                parser.reset(reader, treeId)
            }
            git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .call()
                .flatMap { entry ->
                    listOfNotNull(
                        entry.oldPath.takeIf { it != DiffEntry.DEV_NULL },
                        entry.newPath.takeIf { it != DiffEntry.DEV_NULL }
                    )
                }
                .toSet()
        } finally {
            reader.close()
        }
    }
}

