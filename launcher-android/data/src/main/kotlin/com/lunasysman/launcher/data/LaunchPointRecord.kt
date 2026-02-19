package com.lunasysman.launcher.data

import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LaunchPointType

data class LaunchPointRecord(
    override val id: String,
    override val type: LaunchPointType,
    override val title: String,
    override val iconKey: String?,
    override val pinned: Boolean,
    override val hidden: Boolean,
    override val lastLaunchedAtEpochMs: Long?,
    val sortKey: String,
    val pinnedRank: Long?,
    val installSource: String?,
    val badges: Int,
    val androidPackageName: String?,
    val androidActivityName: String?,
) : LaunchPoint

