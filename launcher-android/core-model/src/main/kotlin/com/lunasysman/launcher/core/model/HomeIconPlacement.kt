package com.lunasysman.launcher.core.model

data class HomeIconPlacement(
    val launchPointId: String,
    val xNorm: Double,
    val yNorm: Double,
    val rotationDeg: Float,
    val zIndex: Int,
    val updatedAtEpochMs: Long,
)

