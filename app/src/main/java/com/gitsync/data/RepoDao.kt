package com.gitsync.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos ORDER BY name ASC")
    fun getAllFlow(): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos ORDER BY name ASC")
    suspend fun getAll(): List<RepoEntity>

    @Query("SELECT * FROM repos WHERE id = :id")
    suspend fun getById(id: Long): RepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: RepoEntity): Long

    @Update
    suspend fun update(repo: RepoEntity)

    @Delete
    suspend fun delete(repo: RepoEntity)
}
