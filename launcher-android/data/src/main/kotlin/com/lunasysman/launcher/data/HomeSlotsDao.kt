package com.lunasysman.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeSlotsDao {
    @Query("SELECT * FROM home_slots ORDER BY slotIndex ASC")
    fun observeHomeSlots(): Flow<List<HomeSlotEntity>>

    @Query("SELECT * FROM home_slots ORDER BY slotIndex ASC")
    suspend fun getHomeSlots(): List<HomeSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HomeSlotEntity)

    @Query("DELETE FROM home_slots WHERE slotIndex = :slotIndex")
    suspend fun deleteAt(slotIndex: Int)

    @Query("DELETE FROM home_slots WHERE launchPointId = :launchPointId")
    suspend fun deleteByLaunchPointId(launchPointId: String)

    @Query("DELETE FROM home_slots")
    suspend fun clear()
}

