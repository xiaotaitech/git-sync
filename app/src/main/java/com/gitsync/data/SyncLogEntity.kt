package com.gitsync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoId: Long,
    val repoName: String,
    val timestamp: Long,        // epoch millis
    val success: Boolean,
    val message: String         // "Pulled 3 commits" | "Conflict detected" | "Already up to date" | error message
)
