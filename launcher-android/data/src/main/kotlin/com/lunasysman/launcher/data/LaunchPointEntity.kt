package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "launch_points",
    indices = [
        Index(value = ["hidden", "pinned", "lastLaunchedAtEpochMs"]),
        Index(value = ["type", "hidden"]),
        Index(value = ["sortKey"]),
    ],
)
data class LaunchPointEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val sortKey: String,
    val iconKey: String?,
    val installSource: String?,
    val badges: Int,
    val androidPackageName: String?,
    val androidActivityName: String?,
    val lastLaunchedAtEpochMs: Long?,
    val pinned: Boolean,
    val pinnedRank: Long?,
    val hidden: Boolean,
)
