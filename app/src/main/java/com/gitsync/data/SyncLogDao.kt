package com.gitsync.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllFlow(): Flow<List<SyncLogEntity>>

    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE repoId = :repoId")
    suspend fun deleteForRepo(repoId: Long)
}
