package com.gitsync.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RepoEntity::class, SyncLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun syncLogDao(): SyncLogDao
}
