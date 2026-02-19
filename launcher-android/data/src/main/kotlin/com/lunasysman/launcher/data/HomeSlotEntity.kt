package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "home_slots",
    indices = [
        Index(value = ["launchPointId"], unique = true),
    ],
)
data class HomeSlotEntity(
    @PrimaryKey val slotIndex: Int,
    val launchPointId: String,
)

