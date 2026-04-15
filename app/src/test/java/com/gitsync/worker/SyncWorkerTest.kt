package com.gitsync.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncWorkerTest {
    @Test
    fun workerReturnsResultTypeForAnyInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestWorkerBuilder<SyncWorker>(
            context = context,
            executor = Executors.newSingleThreadExecutor(),
            inputData = workDataOf(
                SyncWorker.KEY_REPO_ID to 1L,
                SyncWorker.KEY_LOCAL_PATH to "/tmp/nonexistent",
                SyncWorker.KEY_REMOTE_URL to "https://github.com/x/y.git",
                SyncWorker.KEY_PAT to "token",
                SyncWorker.KEY_REPO_NAME to "test-repo"
            )
        ).build()
        val result = worker.doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result::class.java)
    }
}
