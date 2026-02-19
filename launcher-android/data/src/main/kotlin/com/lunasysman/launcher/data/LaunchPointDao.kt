package com.lunasysman.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LaunchPointDao {
    @Query("SELECT * FROM launch_points WHERE hidden = 0")
    fun observeVisible(): Flow<List<LaunchPointEntity>>

    @Query("SELECT * FROM launch_points WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<LaunchPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LaunchPointEntity>)

    @Query("DELETE FROM launch_points WHERE type = :type AND id NOT IN (:ids)")
    suspend fun deleteMissingOfType(type: String, ids: List<String>)

    @Query("UPDATE launch_points SET lastLaunchedAtEpochMs = :epochMs WHERE id = :id")
    suspend fun setLastLaunchedAt(id: String, epochMs: Long)

    @Query("UPDATE launch_points SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE launch_points SET pinnedRank = :pinnedRank WHERE id = :id")
    suspend fun setPinnedRank(id: String, pinnedRank: Long?)

    @Query("UPDATE launch_points SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)
}
