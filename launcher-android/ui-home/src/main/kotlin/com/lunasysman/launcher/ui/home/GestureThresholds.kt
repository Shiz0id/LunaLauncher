package com.lunasysman.launcher.ui.home

import android.util.Log
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized gesture thresholds for the launcher.
 *
 * All gesture-related constants should be defined here to ensure consistency
 * across Home, All Apps, and other surfaces.
 */
@Immutable
data class GestureThresholds(
    /** Distance in px before a touch is considered a drag (system default). */
    val touchSlopPx: Float,

    /** Duration in ms before a press becomes a long-press. */
    val longPressTimeoutMs: Long,

    /** Vertical distance in px to trigger a swipe gesture on the Home surface. */
    val homeSwipeThresholdPx: Float,

    /** Vertical distance in px to trigger swipe-to-dismiss in All Apps. */
    val allAppsSwipeThresholdPx: Float,

    /** Rotation snap step in degrees for icon rotation in edit mode. */
    val rotationStepDeg: Float,

    /** Max distance in px from two-finger centroid to icon center for rotation targeting. */
    val rotationCaptureRadiusPx: Float,
)

/**
 * Compose-friendly accessor for gesture thresholds.
 * Uses system defaults where appropriate, with launcher-specific overrides.
 */
@Composable
fun rememberGestureThresholds(): GestureThresholds {
    val viewConfig = LocalViewConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    return remember(viewConfig, density) {
        val systemTouchSlop = viewConfig.touchSlop
        val systemLongPressMs = viewConfig.longPressTimeoutMillis.toLong()

        // Use a slightly longer long-press timeout for edit mode activation
        // to make it more intentional than the system default.
        val editModeLongPressMs = maxOf(systemLongPressMs, 650L)

        GestureThresholds(
            touchSlopPx = systemTouchSlop,
            longPressTimeoutMs = editModeLongPressMs,
            homeSwipeThresholdPx = with(density) { HOME_SWIPE_THRESHOLD_DP.toPx() },
            allAppsSwipeThresholdPx = with(density) { ALL_APPS_SWIPE_THRESHOLD_DP.toPx() },
            rotationStepDeg = ROTATION_STEP_DEG,
            rotationCaptureRadiusPx = with(density) { ROTATION_CAPTURE_RADIUS_DP.toPx() },
        )
    }
}

// Dp constants for easy tweaking
private val HOME_SWIPE_THRESHOLD_DP: Dp = 32.dp
private val ALL_APPS_SWIPE_THRESHOLD_DP: Dp = 52.dp
private const val ROTATION_STEP_DEG: Float = 5f
private val ROTATION_CAPTURE_RADIUS_DP: Dp = 120.dp

/**
 * Debug flag for gesture logging.
 *
 * When enabled, gesture state transitions are logged to Logcat with tag "GestureDebug".
 * Can be toggled via build config or settings in the future.
 */
object GestureDebug {
    /**
     * Set to true to enable gesture debug logging.
     * In production, this should be false or controlled by a build flag.
     */
    var enabled: Boolean = false

    private const val TAG = "GestureDebug"

    fun log(surface: String, message: String) {
        if (enabled) {
            Log.d(TAG, "[$surface] $message")
        }
    }

    fun logStateTransition(surface: String, from: String, to: String, reason: String = "") {
        if (enabled) {
            val reasonSuffix = if (reason.isNotEmpty()) " ($reason)" else ""
            Log.d(TAG, "[$surface] State: $from -> $to$reasonSuffix")
        }
    }
}
