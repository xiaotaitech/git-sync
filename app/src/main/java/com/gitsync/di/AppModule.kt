package com.gitsync.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.gitsync.data.AppDatabase
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.SyncLogDao
import com.gitsync.git.GitSyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gitsync.db").build()

    @Provides
    fun provideRepoDao(db: AppDatabase): RepoDao = db.repoDao()

    @Provides
    fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    @Singleton
    fun providePrefsRepository(@ApplicationContext context: Context): PrefsRepository =
        PrefsRepository(context)

    @Provides
    @Singleton
    fun provideGitSyncManager(): GitSyncManager = GitSyncManager()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
