package com.lunasysman.launcher.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException

/**
 * Compatibility wrapper for combined click and long-press.
 * Uses the standard Compose combinedClickable under the hood.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.combinedClickableCompat(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier =
    combinedClickable(
        onClick = onClick,
        onLongClick = onLongPress,
    )

/**
 * A unified gesture handler that properly distinguishes between tap, long-press,
 * and drag initiation. This is the recommended pattern for interactive elements
 * that need to support all three gestures.
 *
 * @param key A key to trigger recomposition when this value changes.
 * @param onTap Called when a tap (short press without movement) is detected.
 * @param onLongPress Called when a long-press is detected without subsequent drag.
 * @param onDragStart Called when a drag starts after a long-press.
 * @param onDrag Called for each drag movement.
 * @param onDragEnd Called when the drag ends.
 * @param onDragCancel Called when the drag is cancelled.
 */
internal fun Modifier.unifiedGesture(
    key: Any?,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (change: PointerInputChange) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        GestureDebug.log("UnifiedGesture", "Down at ${down.position}")

        val longPressResult = awaitLongPressOrCancellation(down.id)
        if (longPressResult == null) {
            // Long-press was cancelled (finger lifted or moved too much)
            val up = waitForUpOrCancellation()
            if (up != null) {
                GestureDebug.log("UnifiedGesture", "Tap detected")
                onTap()
                up.consumeAllChanges()
            }
            return@awaitEachGesture
        }

        // Long-press detected
        GestureDebug.log("UnifiedGesture", "Long-press detected")
        var dragStarted = false

        try {
            drag(longPressResult.id) { change ->
                change.consumeAllChanges()
                if (!dragStarted) {
                    dragStarted = true
                    GestureDebug.log("UnifiedGesture", "Drag started")
                    onDragStart()
                }
                onDrag(change)
            }

            if (dragStarted) {
                GestureDebug.log("UnifiedGesture", "Drag ended")
                onDragEnd()
            } else {
                onLongPress()
            }
        } catch (_: CancellationException) {
            GestureDebug.log("UnifiedGesture", "Drag cancelled")
            onDragCancel()
        }
    }
}
