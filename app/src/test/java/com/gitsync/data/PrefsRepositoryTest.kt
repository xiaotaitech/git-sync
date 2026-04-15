package com.gitsync.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrefsRepositoryTest {
    private lateinit var repo: PrefsRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = PrefsRepository(context)
    }

    @Test
    fun savesAndReadsToken() = runBlocking {
        repo.savePat("ghp_testtoken123")
        val token = repo.getPat().first()
        assertThat(token).isEqualTo("ghp_testtoken123")
    }

    @Test
    fun defaultIntervalIsZero() = runBlocking {
        val interval = repo.getDefaultInterval().first()
        assertThat(interval).isEqualTo(0)
    }
}
