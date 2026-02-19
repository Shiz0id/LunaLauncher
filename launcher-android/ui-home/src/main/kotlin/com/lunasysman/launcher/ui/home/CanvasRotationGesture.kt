package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.round

/**
 * Registry of icon positions and drag states for the HomeCanvas surface.
 *
 * The surface-level rotation gesture needs to:
 * 1. Find which icon is nearest to a two-finger centroid
 * 2. Read/write that icon's [IconDragState] to apply rotation
 *
 * Icons register themselves during composition and unregister on disposal.
 */
@Stable
internal class EditModeIconStates {

    private val entries = mutableMapOf<String, IconEntry>()

    internal data class IconEntry(
        val dragState: MutableState<IconDragState?>,
        val baseCenterPx: Offset,
        val initialRotationDeg: Float,
        val initialXNorm: Double,
        val initialYNorm: Double,
    )

    fun register(
        iconId: String,
        dragState: MutableState<IconDragState?>,
        baseCenterPx: Offset,
        initialRotationDeg: Float,
        initialXNorm: Double,
        initialYNorm: Double,
    ) {
        entries[iconId] = IconEntry(dragState, baseCenterPx, initialRotationDeg, initialXNorm, initialYNorm)
    }

    fun unregister(iconId: String) {
        entries.remove(iconId)
    }

    /**
     * Find the icon whose center is nearest to [centroid], within [maxDistancePx].
     * Returns the icon ID or null if nothing is close enough.
     */
    fun findNearest(centroid: Offset, maxDistancePx: Float): String? {
        var bestId: String? = null
        var bestDistSq = maxDistancePx * maxDistancePx
        for ((id, entry) in entries) {
            val center = entry.baseCenterPx
            val dx = centroid.x - center.x
            val dy = centroid.y - center.y
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestId = id
            }
        }
        return bestId
    }

    fun getDragState(iconId: String): MutableState<IconDragState?>? = entries[iconId]?.dragState

    fun getEntry(iconId: String): IconEntry? = entries[iconId]

    fun clearAll() {
        for ((_, entry) in entries) {
            entry.dragState.value = null
        }
    }
}

/**
 * Surface-level gesture modifier for two-finger icon rotation during edit mode.
 *
 * This runs at [PointerEventPass.Initial] so it sees pointer events before
 * per-icon handlers (which use [PointerEventPass.Main]). Single-finger events
 * pass through unconsumed so icon drag still works. When two fingers are
 * detected, the modifier finds the nearest icon and applies rotation.
 *
 * Cooperation with icon drag:
 * - If an icon already holds the [GestureLock] (single-finger drag in progress),
 *   this handler retries on the next event. The icon handler detects the second
 *   finger and releases the lock, allowing this handler to take over.
 * - The icon's [IconDragState] is preserved across the handoff so position is
 *   not lost.
 *
 * @param editMode Whether edit mode is active
 * @param gestureLock Centralized lock to coordinate with icon drag
 * @param iconStates Registry of icon positions and drag states
 * @param thresholds Gesture thresholds (capture radius, rotation step)
 * @param onSelectIcon Callback to mark an icon as selected
 * @param onUpdateIcon Callback to persist final position and rotation
 * @param maxX Maximum X in pixels (for normalization)
 * @param maxY Maximum Y in pixels (for normalization)
 */
internal fun Modifier.editModeCanvasGestures(
    editMode: Boolean,
    gestureLock: GestureLock,
    iconStates: EditModeIconStates,
    thresholds: GestureThresholds,
    onSelectIcon: (String?) -> Unit,
    onUpdateIcon: (id: String, xNorm: Double, yNorm: Double, rotationDeg: Float) -> Unit,
    maxX: Float,
    maxY: Float,
): Modifier {
    if (!editMode) return this

    return pointerInput(editMode, maxX, maxY) {
        awaitEachGesture {
            // Wait for the first finger. Don't consume — let it pass to icon handlers.
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            val tag = "surface-rotate"
            var locked = false
            var rotationIconId: String? = null
            var prevAngleRad: Float? = null

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }

                    if (pressed.isEmpty()) break

                    if (pressed.size >= 2 && rotationIconId == null) {
                        // Two fingers detected — find nearest icon
                        val p1 = pressed[0]
                        val p2 = pressed[1]
                        val centroid = (p1.position + p2.position) / 2f

                        val nearestId = iconStates.findNearest(
                            centroid,
                            thresholds.rotationCaptureRadiusPx,
                        )

                        if (nearestId == null) {
                            // No icon nearby — let events pass through
                            continue
                        }

                        if (!gestureLock.tryAcquire(tag)) {
                            // Icon handler still holds the lock — it will release
                            // when it sees the second finger. Retry next event.
                            continue
                        }

                        // Lock acquired — start rotation session
                        locked = true
                        rotationIconId = nearestId
                        onSelectIcon(nearestId)

                        // Initialize drag state if the icon wasn't already being dragged
                        val dragState = iconStates.getDragState(nearestId)
                        val entry = iconStates.getEntry(nearestId)
                        if (dragState != null && entry != null && dragState.value == null) {
                            dragState.value = IconDragState(
                                xPx = entry.initialXNorm.toFloat() * maxX,
                                yPx = entry.initialYNorm.toFloat() * maxY,
                                rotationDeg = entry.initialRotationDeg,
                            )
                        }

                        prevAngleRad = atan2(
                            p2.position.y - p1.position.y,
                            p2.position.x - p1.position.x,
                        )

                        event.changes.forEach { it.consume() }
                    } else if (rotationIconId != null && pressed.size >= 2) {
                        // Rotation in progress — compute angle delta
                        val targetId = rotationIconId
                        val p1 = pressed[0]
                        val p2 = pressed[1]

                        val angleRad = atan2(
                            p2.position.y - p1.position.y,
                            p2.position.x - p1.position.x,
                        )

                        val prev = prevAngleRad
                        if (prev != null) {
                            val pi = PI.toFloat()
                            var delta = angleRad - prev
                            while (delta > pi) delta -= 2f * pi
                            while (delta < -pi) delta += 2f * pi
                            val rotDegDelta = delta * (180f / pi)

                            val dragState = iconStates.getDragState(targetId)
                            if (dragState != null) {
                                val current = dragState.value
                                if (current != null) {
                                    dragState.value = current.copy(
                                        rotationDeg = current.rotationDeg + rotDegDelta,
                                    )
                                }
                            }
                        }
                        prevAngleRad = angleRad

                        event.changes.forEach { it.consume() }
                    } else if (rotationIconId != null && pressed.size < 2) {
                        // Finger lifted during rotation — finalize
                        break
                    }
                    // Single finger, no rotation started — don't consume, let icon handle
                }

                // Finalize: snap rotation and persist
                val finalIconId = rotationIconId
                if (finalIconId != null) {
                    val dragState = iconStates.getDragState(finalIconId)
                    if (dragState != null && dragState.value != null) {
                        val ds = dragState.value!!
                        val snappedRot = round(ds.rotationDeg / thresholds.rotationStepDeg) *
                            thresholds.rotationStepDeg
                        val finalXNorm = (ds.xPx / maxX).toDouble().coerceIn(0.0, 1.0)
                        val finalYNorm = (ds.yPx / maxY).toDouble().coerceIn(0.0, 1.0)

                        // Keep visual state at snapped value to avoid flicker
                        dragState.value = ds.copy(rotationDeg = snappedRot)

                        onUpdateIcon(finalIconId, finalXNorm, finalYNorm, snappedRot)
                    }
                }
            } finally {
                if (locked) gestureLock.release(tag)
            }
        }
    }
}
