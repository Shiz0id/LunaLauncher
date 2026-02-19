package com.lunasysman.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeIconPositionsDao {
    @Query("SELECT * FROM home_icon_positions ORDER BY zIndex ASC, updatedAtEpochMs ASC")
    fun observeAll(): Flow<List<HomeIconEntity>>

    @Query("SELECT * FROM home_icon_positions")
    suspend fun getAll(): List<HomeIconEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HomeIconEntity)

    @Query(
        """
        UPDATE home_icon_positions
        SET xNorm = :xNorm, yNorm = :yNorm, rotationDeg = :rotationDeg, zIndex = :zIndex, updatedAtEpochMs = :updatedAtEpochMs
        WHERE launchPointId = :launchPointId
        """,
    )
    suspend fun update(
        launchPointId: String,
        xNorm: Double,
        yNorm: Double,
        rotationDeg: Float,
        zIndex: Int,
        updatedAtEpochMs: Long,
    )

    @Query("DELETE FROM home_icon_positions WHERE launchPointId = :launchPointId")
    suspend fun deleteByLaunchPointId(launchPointId: String)
}

