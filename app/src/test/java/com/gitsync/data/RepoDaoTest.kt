package com.gitsync.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepoDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RepoDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.repoDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun insertAndGetRepo() = runBlocking {
        val repo = RepoEntity(
            id = 0,
            name = "my-vault",
            remoteUrl = "https://github.com/user/my-vault.git",
            localPath = "/storage/emulated/0/Documents/my-vault",
            intervalMinutes = 30,
            lastSyncTime = 0L,
            syncStatus = "idle"
        )
        dao.insert(repo)
        val loaded = dao.getAll()
        assertThat(loaded).hasSize(1)
        assertThat(loaded[0].remoteUrl).isEqualTo("https://github.com/user/my-vault.git")
    }

    @Test
    fun deleteRepo() = runBlocking {
        val repo = RepoEntity(
            id = 0,
            name = "vault",
            remoteUrl = "https://github.com/u/v.git",
            localPath = "/tmp/v",
            intervalMinutes = 0,
            lastSyncTime = 0L,
            syncStatus = "idle"
        )
        val id = dao.insert(repo)
        val inserted = dao.getById(id)!!
        dao.delete(inserted)
        assertThat(dao.getAll()).isEmpty()
    }
}
