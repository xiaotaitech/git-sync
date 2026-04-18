package com.gitsync.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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

    /**
     * Full two-way sync:
     * 1. Commit any local changes
     * 2. Fetch remote
     * 3. If same files changed on both sides → Conflict (user decides)
     * 4. Otherwise rebase local commits on top of remote → push
     */
    suspend fun sync(localPath: String, pat: String): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(localPath)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            return@withContext SyncResult.Error("Not a git repository: $localPath")
        }
        try {
            val git = Git.open(dir)
            val creds = UsernamePasswordCredentialsProvider(pat, "")

            // 1. Commit local changes if any
            val localChanges = getLocalChangesInternal(git)
            val hadLocalChanges = localChanges.isNotEmpty()
            if (hadLocalChanges) {
                // Stage new + modified files
                git.add().addFilepattern(".").call()
                // Stage deleted (missing) files — git add -A equivalent
                if (localChanges.isNotEmpty()) {
                    val missing = git.status().call().missing
                    if (missing.isNotEmpty()) {
                        val rm = git.rm()
                        missing.forEach { rm.addFilepattern(it) }
                        rm.call()
                    }
                }
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                git.commit()
                    .setMessage("Sync: $timestamp")
                    .setAuthor(PersonIdent("GitSync", "gitsync@local"))
                    .call()
            }

            // 2. Fetch remote
            git.fetch().setCredentialsProvider(creds).call()

            val repo = git.repository
            val headId = repo.resolve("HEAD")
            val fetchHeadId = repo.resolve("FETCH_HEAD")

            if (fetchHeadId == null || headId == fetchHeadId) {
                // Nothing new on remote — if we committed, push it
                if (hadLocalChanges) {
                    git.push().setCredentialsProvider(creds).call()
                    git.close()
                    return@withContext SyncResult.Success("Pushed local changes")
                }
                git.close()
                return@withContext SyncResult.Success("Already up to date")
            }

            // 3. Check for file-level conflict between local commit and remote changes
            if (hadLocalChanges) {
                // Find common ancestor so we compare each side's changes from the same base
                val mergeBase = repo.resolve(
                    repo.newObjectReader().use {
                        val walk = RevWalk(repo)
                        walk.revFilter = org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE
                        walk.markStart(walk.parseCommit(headId))
                        walk.markStart(walk.parseCommit(fetchHeadId))
                        val base = walk.next()
                        walk.dispose()
                        base?.name
                    }
                ) ?: repo.resolve("HEAD~1") ?: headId
                val remoteChanges = getChangedFilesBetween(git, mergeBase, fetchHeadId)
                val localCommittedChanges = getChangedFilesBetween(git, mergeBase, headId)
                val conflicting = localCommittedChanges.intersect(remoteChanges)
                if (conflicting.isNotEmpty()) {
                    // Undo the auto-commit so user can decide
                    git.reset().setMode(ResetCommand.ResetType.SOFT).setRef("HEAD~1").call()
                    git.close()
                    return@withContext SyncResult.Conflict(conflicting.toList())
                }
            }

            // 4. Rebase local commits on top of remote, then push
            // Guard: if working tree still dirty after commit attempt, stash first
            val dirtyBeforeRebase = getLocalChangesInternal(git).isNotEmpty()
            if (dirtyBeforeRebase) {
                git.stashCreate().call()
            }
            val rebaseResult = git.rebase()
                .setUpstream(fetchHeadId)
                .call()

            if (rebaseResult.status == RebaseResult.Status.OK ||
                rebaseResult.status == RebaseResult.Status.FAST_FORWARD ||
                rebaseResult.status == RebaseResult.Status.UP_TO_DATE
            ) {
                git.push().setCredentialsProvider(creds).call()
                if (dirtyBeforeRebase) git.stashApply().call()
                git.close()
                val msg = if (hadLocalChanges) "Synced — pushed local changes and pulled remote"
                          else "Pulled new commits"
                SyncResult.Success(msg)
            } else {
                // Rebase failed (unexpected) — abort and report
                if (dirtyBeforeRebase) git.stashApply().call()
                git.rebase().setOperation(org.eclipse.jgit.api.RebaseCommand.Operation.ABORT).call()
                git.close()
                SyncResult.Error("Rebase failed: ${rebaseResult.status}")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Legacy pull-only (used by forcePull path). */
    suspend fun pull(localPath: String, pat: String): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(localPath)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            return@withContext SyncResult.Error("Not a git repository: $localPath")
        }
        try {
            val git = Git.open(dir)
            val creds = UsernamePasswordCredentialsProvider(pat, "")
            git.fetch().setCredentialsProvider(creds).call()

            val localChanges = getLocalChangesInternal(git).toSet()
            if (localChanges.isEmpty()) {
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

            val repo = git.repository
            val headId = repo.resolve("HEAD")
            val fetchHeadId = repo.resolve("FETCH_HEAD")
            if (fetchHeadId == null || headId == fetchHeadId) {
                git.close()
                return@withContext SyncResult.Success("Already up to date")
            }

            val remoteChanges = getChangedFilesBetween(git, headId, fetchHeadId)
            val conflicting = localChanges.intersect(remoteChanges)
            if (conflicting.isNotEmpty()) {
                git.close()
                return@withContext SyncResult.Conflict(conflicting.toList())
            }

            git.stashCreate().call()
            val pullResult = git.pull().setCredentialsProvider(creds).call()
            if (!pullResult.isSuccessful) {
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

    /** Force pull: discard all local changes, hard reset, pull. */
    suspend fun forcePull(localPath: String, pat: String): SyncResult = withContext(Dispatchers.IO) {
        val dir = File(localPath)
        return@withContext try {
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
        return@withContext try {
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

    private fun getChangedFilesBetween(git: Git, fromId: ObjectId, toId: ObjectId): Set<String> {
        val repo = git.repository
        val reader = repo.newObjectReader()
        return try {
            val oldTree: AbstractTreeIterator = CanonicalTreeParser().also { parser ->
                parser.reset(reader, repo.parseCommit(fromId).tree.id)
            }
            val newTree: AbstractTreeIterator = CanonicalTreeParser().also { parser ->
                parser.reset(reader, repo.parseCommit(toId).tree.id)
            }
            git.diff().setOldTree(oldTree).setNewTree(newTree).call()
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
