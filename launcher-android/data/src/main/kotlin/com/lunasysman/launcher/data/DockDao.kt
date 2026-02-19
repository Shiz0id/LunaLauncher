package com.lunasysman.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DockDao {
    @Query("SELECT * FROM dock_entries ORDER BY position ASC")
    fun observeDock(): Flow<List<DockEntryEntity>>

    @Query("SELECT * FROM dock_entries ORDER BY position ASC")
    suspend fun getDock(): List<DockEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DockEntryEntity)

    @Query("DELETE FROM dock_entries WHERE position = :position")
    suspend fun deleteAt(position: Int)

    @Query("DELETE FROM dock_entries WHERE launchPointId = :launchPointId")
    suspend fun deleteByLaunchPointId(launchPointId: String)

    @Query("DELETE FROM dock_entries")
    suspend fun clear()
}

