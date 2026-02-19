# Launcher Gestures (Android)

This document lists the launcher's gestures as implemented today, and calls out where gesture conflicts/interference can happen.

## Architecture Overview

The gesture system has been refactored to use a unified approach:

### Key Files

- **GestureThresholds.kt**: Centralized constants for touch slop, long-press timeout, swipe thresholds, rotation step, and rotation capture radius.
- **GestureState.kt**: State machine definitions for Home surface gesture handling.
- **IconGesture.kt**: Per-icon single-finger drag handler (`Modifier.iconDrag()`).
- **CanvasRotationGesture.kt**: Surface-level two-finger rotation handler (`Modifier.editModeCanvasGestures()`) and `EditModeIconStates` registry.
- **PointerCompat.kt**: Utility modifiers including `combinedClickableCompat` and `unifiedGesture`.

### Debug Mode

Set `GestureDebug.enabled = true` to log all gesture state transitions to Logcat with tag "GestureDebug".

## Global / system

- **Back**
  - **All Apps open** (and search/menu not open): closes All Apps.
  - **Search open**: dismisses search.
  - **Drag in progress** (All Apps → Home/Dock): cancels the drag.
  - Owner: `launcher-android/app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt`

## Home surface

Source: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/HomeScreen.kt`

### Normal mode (not editing)

- **Swipe down** (vertical drag threshold): opens search.
- **Swipe up** (vertical drag threshold): opens All Apps.
- **Tap Home icon**: launches the app.
- **Long-press Home icon**: opens the app menu sheet.
- **Long-press background**: enters edit mode.

Implementation notes:
- All gestures use a single `pointerInput { awaitEachGesture { ... } }` pipeline.
- Swipe gestures are disabled while `searchOpen == true` or `editMode == true`.
- Long-press timeout and swipe threshold are sourced from `GestureThresholds`.
- The gesture loop manually tracks time for long-press and movement for swipe detection.

### Edit mode

- **Close edit mode**: tap `CLOSE` on the edit bar.
- **1-finger drag on an icon**: moves the icon (absolute positioning, overlap allowed).
  - Handled per-icon via `Modifier.iconDrag()` in `IconGesture.kt`.
  - If a second finger arrives during drag, the icon handler releases the `GestureLock` and delegates to the surface rotation handler.
- **2-finger rotate**: rotates the nearest icon to the two-finger centroid.
  - Handled at the **HomeCanvas surface level** via `Modifier.editModeCanvasGestures()` in `CanvasRotationGesture.kt`.
  - Uses `PointerEventPass.Initial` so it sees events before per-icon handlers.
  - Finds the nearest icon within `rotationCaptureRadiusPx` (120dp) of the centroid.
  - Rotation is **live** while fingers are down.
  - Rotation is **snapped on release** to `rotationStepDeg` (currently `5°`) and then persisted.
  - Users do NOT need to touch the icon directly — two fingers anywhere near it will work.

Cooperation protocol (drag → rotation handoff):
1. First finger on icon → icon handler acquires `GestureLock`, starts dragging.
2. Second finger down → surface handler (Initial pass) sees 2 pointers, tries lock → fails.
3. Icon handler (Main pass) sees 2 pointers, releases lock, exits.
4. Next pointer event → surface handler retries lock → succeeds, starts rotation.
5. Icon's `dragState` is preserved across the handoff (no position loss).

Persistence path (rotate/move):
- UI gesture → `onUpdateHomeIcon(...)` (HomeScreen)
- `LauncherActivity` forwards to `LauncherViewModel.updateHomeIconPosition(...)`
- Repository/DAO writes `home_icon_positions.rotationDeg`

## Dock

Source: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/HomeScreen.kt`

- **Tap dock icon**: launches the app.
- **Long-press dock icon**: opens the app menu sheet.
- **Drop target** (from All Apps drag): dropping within dock bounds adds the app to dock.

## All Apps

Sources:
- UI: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/AllAppsScreen.kt`
- Coordinator/drag preview: `launcher-android/app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt`
- Design doc: `launcher-android/docs/AllAppsDrag.md`

- **Open All Apps**:
  - Home swipe up OR the chevron handle button.
- **Tabs**: Apps / Favorites / Settings.
- **Tap tile**: launches the app.
- **Long-press tile**:
  - If you move: starts drag-and-drop (keeps streaming pointer positions).
  - If you release without moving: opens the app menu sheet.
- **Drag-and-drop** (All Apps → Home/Dock):
  - Drag preview follows finger.
  - Drop inside Home bounds: places the app on Home.
  - Drop inside Dock bounds: adds the app to dock.
- **Swipe down at top**: dismisses All Apps (threshold from `GestureThresholds.allAppsSwipeThresholdPx`).

## Search

Sources:
- Home entrypoints: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/HomeScreen.kt`
- Overlay UI: `launcher-android/ui-search/src/main/kotlin/com/lunasysman/launcher/ui/search/SearchOverlay.kt`
- Coordinator: `launcher-android/app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt`

- **Open search**:
  - Home swipe down OR tap the Home search bar.
- **Type**: filters results (apps + any additional sections from `JustTypeUiState`).
- **Tap outside results panel**: dismisses search.
- **Back**: dismisses search.

## Widgets (Home)

Source: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/HomeScreen.kt`

- **Long-press widget**: selects the widget (shows selection outline).
  - Implemented using pure Compose gesture (`awaitLongPressOrCancellation`).
- **Resize handles** (edit interactions):
  - Horizontal handle drag: resizes widget width in grid cells.
  - Vertical handle drag: resizes widget height in grid cells.
- **Remove widget**: via the widget UI controls in edit mode (if present for that widget).

## Centralized Thresholds

All gesture thresholds are defined in `GestureThresholds.kt`:

| Threshold | Value | Usage |
|-----------|-------|-------|
| `touchSlopPx` | System default | Distance before drag is recognized |
| `longPressTimeoutMs` | max(system, 650ms) | Time before long-press fires |
| `homeSwipeThresholdPx` | 32.dp | Home surface swipe up/down |
| `allAppsSwipeThresholdPx` | 52.dp | All Apps swipe-to-dismiss |
| `rotationStepDeg` | 5° | Icon rotation snap step |
| `rotationCaptureRadiusPx` | 120.dp | Max distance from two-finger centroid to icon center for rotation targeting |

## Common interference patterns (what to look for)

- **Duplicate icons with the same `lp.id`** can cause Compose key collisions and flicker/oscillation. Home now dedupes by `updatedAtEpochMs`, but upstream should ideally not emit duplicates.
- **Overlays with high `zIndex`** can intercept touches if they become hit-testable (even when "visually transparent").
- **Multiple vertical drags** competing (Home swipe vs icon drag vs widget resize) are especially likely to conflict unless a single owner/state machine arbitrates.

## Refactor Status

The following refactors have been completed:

1. ✅ **GestureDebug flag**: Added to `GestureThresholds.kt` with logging for state transitions.
2. ✅ **Centralized thresholds**: All thresholds in `GestureThresholds.kt`, consumed via `rememberGestureThresholds()`.
3. ✅ **Home surface gestures unified**: Single `pointerInput` pipeline handles swipe + long-press.
4. ✅ **Widget long-press converted**: Removed `pointerInteropFilter` + `Handler`, now uses `awaitLongPressOrCancellation`.
5. ✅ **All Apps uses centralized thresholds**: Swipe dismiss threshold sourced from `GestureThresholds`.
6. ✅ **Removed mixed MotionEvent + Compose stacks**: No more `pointerInteropFilter` on Home root or widgets.

### State Machine (GestureState.kt)

The Home surface gesture state machine models:

```
Idle → Pressed → LongPressArmed → {Dragging|Rotating|Resizing|EditModeActive} → Released/Cancelled
                     ↓
                  Swiping → Released/Cancelled
```

- `HomeGestureOwner` manages transitions and validates state changes.
- Child elements can claim gestures when in `Pressed`, `LongPressArmed`, or `EditModeActive` states.
- Surface gestures (swipe, background long-press) are blocked when a child owns the gesture.
