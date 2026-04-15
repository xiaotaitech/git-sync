package com.gitsync.git

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ConflictDetectorTest {
    private val manager = GitSyncManager()
    private val detector = ConflictDetector(manager)
    private lateinit var tmpDir: File

    @Before
    fun setup() { tmpDir = createTempDir("conflict_test") }

    @After
    fun teardown() { tmpDir.deleteRecursively() }

    @Test
    fun hasConflict_falseForCleanRepo() = runBlocking {
        val git = Git.init().setDirectory(tmpDir).call()
        val f = File(tmpDir, "a.md").apply { writeText("x") }
        git.add().addFilepattern("a.md").call()
        git.commit().setMessage("init").setAuthor("t", "t@t.com").call()

        assertThat(detector.hasConflict(tmpDir.absolutePath)).isFalse()
    }

    @Test
    fun hasConflict_trueAfterModification() = runBlocking {
        val git = Git.init().setDirectory(tmpDir).call()
        val f = File(tmpDir, "a.md").apply { writeText("x") }
        git.add().addFilepattern("a.md").call()
        git.commit().setMessage("init").setAuthor("t", "t@t.com").call()
        f.writeText("modified")

        assertThat(detector.hasConflict(tmpDir.absolutePath)).isTrue()
        assertThat(detector.getModifiedFiles(tmpDir.absolutePath)).containsExactly("a.md")
    }
}
