package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.round

/**
 * Data class representing the live drag state of an icon.
 *
 * This holds temporary position and rotation values during drag operations.
 * When null, the icon uses its persisted database position.
 *
 * @property xPx Absolute X position in pixels
 * @property yPx Absolute Y position in pixels
 * @property rotationDeg Rotation in degrees
 */
internal data class IconDragState(
    val xPx: Float,
    val yPx: Float,
    val rotationDeg: Float,
)

/**
 * Gesture modifier for single-finger drag of home screen icons in edit mode.
 *
 * Handles only translation (position). Two-finger rotation is handled by
 * [editModeCanvasGestures] at the surface level, which has a large enough
 * touch target for multi-finger gestures.
 *
 * When a second finger is detected, this handler releases the [GestureLock]
 * and exits, allowing the surface rotation handler to take over. The icon's
 * [dragState] is preserved across the handoff so position is not lost.
 *
 * @param iconId Unique identifier for the icon (used for gesture lock tagging)
 * @param gestureLock Centralized lock to prevent simultaneous gestures
 * @param thresholds Gesture thresholds (rotation step for snapping)
 * @param maxX Maximum X position in pixels (for bounds clamping)
 * @param maxY Maximum Y position in pixels (for bounds clamping)
 * @param initialXNorm Initial normalized X position [0,1] from database
 * @param initialYNorm Initial normalized Y position [0,1] from database
 * @param initialRotationDeg Initial rotation in degrees from database
 * @param dragState Mutable state holding live drag position (null when not dragging)
 * @param onSelect Callback when icon is selected (invoked on initial touch)
 * @param onUpdate Callback when drag completes with final normalized position and rotation
 */
internal fun Modifier.iconDrag(
    iconId: String,
    gestureLock: GestureLock,
    thresholds: GestureThresholds,
    maxX: Float,
    maxY: Float,
    initialXNorm: Double,
    initialYNorm: Double,
    initialRotationDeg: Float,
    dragState: MutableState<IconDragState?>,
    onSelect: (String) -> Unit,
    onUpdate: (launchPointId: String, xNorm: Double, yNorm: Double, rotationDeg: Float) -> Unit,
): Modifier = pointerInput(iconId, maxX, maxY) {
    awaitEachGesture {
        val tag = "icon:$iconId"
        val down = awaitFirstDown(requireUnconsumed = false)

        // Acquire gesture lock to prevent conflicts with surface or widget gestures
        if (!gestureLock.tryAcquire(tag)) return@awaitEachGesture

        var delegatedToSurface = false

        try {
            down.consume()
            onSelect(iconId)

            // Initialize drag state from database or previous drag
            var currentX = dragState.value?.xPx ?: (initialXNorm.toFloat() * maxX)
            var currentY = dragState.value?.yPx ?: (initialYNorm.toFloat() * maxY)
            val currentRot = dragState.value?.rotationDeg ?: initialRotationDeg
            var changed = false
            var prevPosition: Offset? = null

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pressedChanges = event.changes.filter { it.pressed }
                if (pressedChanges.isEmpty()) break

                // Second finger detected — delegate to surface rotation handler
                if (pressedChanges.size >= 2) {
                    delegatedToSurface = true
                    gestureLock.release(tag)
                    break
                }

                val change = pressedChanges.first()
                val position = change.position

                // Compute single-finger pan delta
                val prev = prevPosition
                val pan = if (prev != null) {
                    position - prev
                } else {
                    Offset.Zero
                }
                prevPosition = position

                currentX = (currentX + pan.x).coerceIn(0f, maxX)
                currentY = (currentY + pan.y).coerceIn(0f, maxY)
                changed = true

                dragState.value = IconDragState(currentX, currentY, currentRot)

                change.consume()
            }

            if (!delegatedToSurface) {
                if (changed) {
                    val finalXNorm = (currentX / maxX).toDouble().coerceIn(0.0, 1.0)
                    val finalYNorm = (currentY / maxY).toDouble().coerceIn(0.0, 1.0)
                    val snappedRot = round(currentRot / thresholds.rotationStepDeg) *
                        thresholds.rotationStepDeg

                    // Keep drag state at final values to prevent flicker while DB updates
                    dragState.value = IconDragState(currentX, currentY, snappedRot)

                    onUpdate(iconId, finalXNorm, finalYNorm, snappedRot)
                } else {
                    dragState.value = null
                }
            }
            // If delegatedToSurface, leave dragState as-is for the surface handler
        } finally {
            if (!delegatedToSurface) {
                gestureLock.release(tag)
            }
        }
    }
}
