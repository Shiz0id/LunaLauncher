package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dock_entries",
    indices = [
        Index(value = ["launchPointId"], unique = true),
    ],
)
data class DockEntryEntity(
    @PrimaryKey val position: Int,
    val launchPointId: String,
)

