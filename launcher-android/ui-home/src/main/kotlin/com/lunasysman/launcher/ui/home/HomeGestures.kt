package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeConsumed

/**
 * Unified gesture modifier for the Home surface.
 *
 * Handles three primary gestures using a single awaitEachGesture pipeline:
 * - Swipe up: Opens All Apps
 * - Swipe down: Opens Search
 * - Long-press on background: Enters Edit Mode
 *
 * This modifier follows Google's recommended gesture handling patterns by:
 * 1. Using a unified gesture pipeline to avoid conflicts
 * 2. Properly checking for consumption before claiming gestures
 * 3. Implementing a centralized gesture lock to prevent simultaneous handlers
 * 4. Using typed threshold objects for configurability
 *
 * @param gestureLock Centralized lock to coordinate with icon and widget gestures
 * @param thresholds Gesture threshold configuration (touch slop, long-press timeout, swipe distance)
 * @param enabled Whether gestures are enabled (typically disabled when search/edit mode is active)
 * @param onSwipeUp Callback invoked when user swipes up beyond threshold
 * @param onSwipeDown Callback invoked when user swipes down beyond threshold
 * @param onLongPress Callback invoked when user long-presses empty space
 */
internal fun Modifier.homeSurfaceGestures(
    gestureLock: GestureLock,
    thresholds: GestureThresholds,
    enabled: Boolean = true,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(gestureLock.owner, thresholds) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            GestureDebug.log("HomeSurface", "Down at ${down.position}")

            // Respect gesture lock from other handlers (icons, widgets)
            if (gestureLock.owner != null) {
                GestureDebug.log("HomeSurface", "Gesture lock held (${gestureLock.owner}), ignoring surface gesture")
                return@awaitEachGesture
            }

            val startPos = down.position
            val startUptimeMs = down.uptimeMillis
            val touchSlopSq = thresholds.touchSlopPx * thresholds.touchSlopPx

            var wonDrag = false
            var handled = false
            var totalDy = 0f
            var locked = false
            val tag = "surface"

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    // If a child consumes movement before we cross slop, we lost the gesture
                    if (!wonDrag && change.positionChangeConsumed()) {
                        GestureDebug.log("HomeSurface", "Canceled (another handler consumed movement)")
                        return@awaitEachGesture
                    }

                    val dx = change.position.x - startPos.x
                    val dy = change.position.y - startPos.y
                    val distSq = dx * dx + dy * dy

                    // Check if we've crossed touch slop threshold
                    if (!wonDrag && distSq > touchSlopSq) {
                        wonDrag = true
                        GestureDebug.log("HomeSurface", "Moved beyond touch slop")
                    }

                    // Handle long-press detection before winning drag
                    if (!wonDrag) {
                        val elapsedMs = change.uptimeMillis - startUptimeMs
                        if (!handled && elapsedMs >= thresholds.longPressTimeoutMs) {
                            if (!gestureLock.tryAcquire(tag)) return@awaitEachGesture
                            locked = true
                            GestureDebug.log("HomeSurface", "Long-press detected, entering edit mode")
                            onLongPress()
                            handled = true
                            change.consumeAllChanges()
                        }
                        if (handled) {
                            // After background long-press, keep consuming until pointer lifts
                            change.consumeAllChanges()
                        }
                        continue
                    }

                    // We won the gesture; treat it as a swipe candidate
                    totalDy = dy
                    if (!handled) {
                        when {
                            totalDy > thresholds.homeSwipeThresholdPx -> {
                                if (!gestureLock.tryAcquire(tag)) return@awaitEachGesture
                                locked = true
                                GestureDebug.log("HomeSurface", "Swipe down detected, opening search")
                                onSwipeDown()
                                handled = true
                                change.consumeAllChanges()
                            }
                            totalDy < -thresholds.homeSwipeThresholdPx -> {
                                if (!gestureLock.tryAcquire(tag)) return@awaitEachGesture
                                locked = true
                                GestureDebug.log("HomeSurface", "Swipe up detected, opening All Apps")
                                onSwipeUp()
                                handled = true
                                change.consumeAllChanges()
                            }
                        }
                    }

                    if (handled) {
                        change.consumeAllChanges()
                    }
                }
            } finally {
                if (locked) gestureLock.release(tag)
            }
        }
    }
}
