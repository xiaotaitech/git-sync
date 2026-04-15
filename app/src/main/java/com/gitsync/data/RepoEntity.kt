package com.gitsync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repos")
data class RepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val remoteUrl: String,
    val localPath: String,
    val intervalMinutes: Int,   // 0 = off, 15, 30, 60, 360
    val lastSyncTime: Long,     // epoch millis, 0 = never
    val syncStatus: String      // "idle" | "syncing" | "conflict" | "error"
)
