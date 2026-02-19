package com.lunasysman.launcher.core.model

/**
 * A runtime-agnostic description of something the user can launch.
 *
 * Public contract:
 * - Stable, immutable [id]
 * - Immutable [type]
 * - Mutable user state: [pinned], [hidden], [lastLaunchedAtEpochMs]
 *
 * UI modules must only depend on this interface and actions; platform-specific details live elsewhere.
 */
interface LaunchPoint {
    val id: String
    val type: LaunchPointType
    val title: String
    val iconKey: String?
    val pinned: Boolean
    val hidden: Boolean
    val lastLaunchedAtEpochMs: Long?
}

enum class LaunchPointType {
    ANDROID_APP,
    WEBOS_APP,
}

