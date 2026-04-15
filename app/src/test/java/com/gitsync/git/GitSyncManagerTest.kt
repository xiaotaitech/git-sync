package com.gitsync.git

import com.google.common.truth.Truth.assertThat
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class GitSyncManagerTest {
    private val manager = GitSyncManager()
    private lateinit var tmpDir: File

    @Before
    fun setup() {
        tmpDir = createTempDir("gitsync_test")
    }

    @After
    fun teardown() { tmpDir.deleteRecursively() }

    @Test
    fun getLocalChanges_returnsEmptyForCleanRepo() {
        val git = Git.init().setDirectory(tmpDir).call()
        val testFile = File(tmpDir, "note.md")
        testFile.writeText("hello")
        git.add().addFilepattern("note.md").call()
        git.commit().setMessage("init").setAuthor("test", "t@t.com").call()

        val changes = manager.getLocalChanges(tmpDir.absolutePath)
        assertThat(changes).isEmpty()
    }

    @Test
    fun getLocalChanges_returnsModifiedFiles() {
        val git = Git.init().setDirectory(tmpDir).call()
        val testFile = File(tmpDir, "note.md")
        testFile.writeText("hello")
        git.add().addFilepattern("note.md").call()
        git.commit().setMessage("init").setAuthor("test", "t@t.com").call()

        testFile.writeText("modified")

        val changes = manager.getLocalChanges(tmpDir.absolutePath)
        assertThat(changes).containsExactly("note.md")
    }

    @Test
    fun pull_returnsErrorForNonGitDirectory() {
        val nonGitDir = createTempDir("notgit")
        val result = manager.pull(nonGitDir.absolutePath, "token")
        assertThat(result).isInstanceOf(SyncResult.Error::class.java)
        nonGitDir.deleteRecursively()
    }

    @Test
    fun pull_returnsConflictWhenLocalChangesExist() {
        val git = Git.init().setDirectory(tmpDir).call()
        val testFile = File(tmpDir, "note.md")
        testFile.writeText("hello")
        git.add().addFilepattern("note.md").call()
        git.commit().setMessage("init").setAuthor("test", "t@t.com").call()

        // Modify without committing
        testFile.writeText("local change")

        val result = manager.pull(tmpDir.absolutePath, "fake_token")
        assertThat(result).isInstanceOf(SyncResult.Conflict::class.java)
        val conflict = result as SyncResult.Conflict
        assertThat(conflict.modifiedFiles).containsExactly("note.md")
    }
}
