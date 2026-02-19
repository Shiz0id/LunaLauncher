package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "home_icon_positions",
    indices = [
        Index(value = ["updatedAtEpochMs"]),
    ],
)
data class HomeIconEntity(
    @PrimaryKey val launchPointId: String,
    val xNorm: Double,
    val yNorm: Double,
    val rotationDeg: Float,
    val zIndex: Int,
    val updatedAtEpochMs: Long,
)

