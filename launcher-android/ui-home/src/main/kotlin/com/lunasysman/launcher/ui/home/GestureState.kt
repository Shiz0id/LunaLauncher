package com.lunasysman.launcher.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset

/**
 * Unified gesture state machine for the Home surface.
 *
 * This models the lifecycle of touch interactions to ensure predictable priority:
 * - Only one gesture can be "active" at a time
 * - Explicit transitions prevent conflicting interpretations
 * - Child gestures can claim ownership to block surface-level gestures
 */
@Stable
sealed interface HomeGestureState {
    /** No touch is occurring. */
    data object Idle : HomeGestureState

    /** Finger is down, waiting to determine gesture type. */
    data class Pressed(
        val position: Offset,
        val timestampMs: Long,
    ) : HomeGestureState

    /** Long-press timer is armed; will fire edit mode if not cancelled. */
    data class LongPressArmed(
        val position: Offset,
        val startTimestampMs: Long,
    ) : HomeGestureState

    /** A vertical swipe is in progress (up or down). */
    data class Swiping(
        val startPosition: Offset,
        val currentPosition: Offset,
        val accumulated: Float,
    ) : HomeGestureState

    /** Edit mode is active; child icons/widgets own gestures. */
    data object EditModeActive : HomeGestureState

    /** A child (icon, widget) has claimed the gesture. Surface gestures are blocked. */
    data class ChildOwned(
        val ownerId: String,
        val gestureType: ChildGestureType,
    ) : HomeGestureState
}

/**
 * Types of gestures that child elements can claim.
 */
enum class ChildGestureType {
    /** Single-finger drag to move an icon. */
    IconDrag,

    /** Two-finger rotation on an icon. */
    IconRotate,

    /** Tap or long-press on an icon (non-edit mode). */
    IconTap,
}

/**
 * Manages gesture state transitions for the Home surface.
 *
 * Call [transition] to move between states. Invalid transitions are logged
 * (when GestureDebug is enabled) but allowed to prevent crashes.
 */
class HomeGestureOwner {
    private var _state: HomeGestureState = HomeGestureState.Idle
    val state: HomeGestureState get() = _state

    /**
     * Attempts to transition to a new state.
     *
     * @param newState The target state.
     * @param reason Optional reason for logging.
     * @return true if the transition was valid, false otherwise.
     */
    fun transition(newState: HomeGestureState, reason: String = ""): Boolean {
        val oldState = _state
        val valid = isValidTransition(oldState, newState)

        if (!valid) {
            GestureDebug.log("HomeSurface", "Invalid transition: ${oldState::class.simpleName} -> ${newState::class.simpleName}")
        }

        _state = newState
        GestureDebug.logStateTransition(
            surface = "HomeSurface",
            from = oldState::class.simpleName ?: "Unknown",
            to = newState::class.simpleName ?: "Unknown",
            reason = reason,
        )

        return valid
    }

    /**
     * Resets to Idle state.
     */
    fun reset(reason: String = "reset") {
        transition(HomeGestureState.Idle, reason)
    }

    /**
     * Checks if a child can claim the gesture.
     * Only valid when in Pressed, LongPressArmed, or EditModeActive states.
     */
    fun canChildClaim(): Boolean = when (_state) {
        is HomeGestureState.Pressed,
        is HomeGestureState.LongPressArmed,
        is HomeGestureState.EditModeActive -> true
        else -> false
    }

    /**
     * Checks if surface gestures (swipe, long-press) are allowed.
     * Blocked when a child owns the gesture or edit mode is active.
     */
    fun canSurfaceGesture(): Boolean = when (_state) {
        is HomeGestureState.Idle,
        is HomeGestureState.Pressed,
        is HomeGestureState.LongPressArmed,
        is HomeGestureState.Swiping -> true
        else -> false
    }

    /**
     * Whether the surface is in edit mode.
     */
    fun isEditMode(): Boolean = _state == HomeGestureState.EditModeActive ||
        (_state is HomeGestureState.ChildOwned)

    private fun isValidTransition(from: HomeGestureState, to: HomeGestureState): Boolean {
        // Allow any transition to Idle (reset)
        if (to is HomeGestureState.Idle) return true

        return when (from) {
            is HomeGestureState.Idle -> to is HomeGestureState.Pressed || to is HomeGestureState.EditModeActive
            is HomeGestureState.Pressed -> to is HomeGestureState.LongPressArmed ||
                to is HomeGestureState.Swiping ||
                to is HomeGestureState.ChildOwned ||
                to is HomeGestureState.Idle

            is HomeGestureState.LongPressArmed -> to is HomeGestureState.EditModeActive ||
                to is HomeGestureState.Swiping ||
                to is HomeGestureState.ChildOwned ||
                to is HomeGestureState.Idle

            is HomeGestureState.Swiping -> to is HomeGestureState.Idle
            is HomeGestureState.EditModeActive -> to is HomeGestureState.ChildOwned || to is HomeGestureState.Idle
            is HomeGestureState.ChildOwned -> to is HomeGestureState.EditModeActive || to is HomeGestureState.Idle
        }
    }
}

/**
 * Result of processing a touch event on the Home surface.
 */
sealed interface HomeSurfaceGestureResult {
    /** No action needed. */
    data object None : HomeSurfaceGestureResult

    /** Open search (swipe down). */
    data object OpenSearch : HomeSurfaceGestureResult

    /** Open All Apps (swipe up). */
    data object OpenAllApps : HomeSurfaceGestureResult

    /** Enter edit mode (long-press on background). */
    data object EnterEditMode : HomeSurfaceGestureResult

    /** A child element should handle the gesture. */
    data class DelegateToChild(val childId: String) : HomeSurfaceGestureResult
}
