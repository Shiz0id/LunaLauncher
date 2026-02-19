package com.lunasysman.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JustTypeProviderDao {
    @Query("SELECT * FROM just_type_provider ORDER BY orderIndex ASC, id ASC")
    fun observeAllOrdered(): Flow<List<JustTypeProviderEntity>>

    @Query("SELECT * FROM just_type_provider WHERE enabled = 1 ORDER BY orderIndex ASC, id ASC")
    fun observeEnabledOrdered(): Flow<List<JustTypeProviderEntity>>

    @Query("SELECT * FROM just_type_provider")
    suspend fun getAll(): List<JustTypeProviderEntity>

    @Query("SELECT * FROM just_type_provider WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JustTypeProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<JustTypeProviderEntity>)

    @Query("DELETE FROM just_type_provider WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE just_type_provider SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE just_type_provider SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun setOrderIndex(id: String, orderIndex: Int)
}

