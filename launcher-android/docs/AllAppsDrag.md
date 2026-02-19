# All Apps → Drag & Drop Workflow

This document describes how the current All Apps drag workflow works and why it’s implemented this way.

## Goals
- Long-press + drag an app from All Apps
- Drop onto Home/Dock to place
- Avoid gesture conflicts (grid scroll, swipe gestures, menu long-press)
- Keep the launcher stable across recompositions

## Surfaces involved
- **All Apps grid**: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/AllAppsScreen.kt`
- **Home surface + dock**: `launcher-android/ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/HomeScreen.kt`
- **Coordinator / state owner**: `launcher-android/app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt`

## User flow
1. User opens All Apps.
2. User long-presses an app icon.
3. The launcher enters “dragging” state and shows a floating preview under the finger.
4. User drags over Home or Dock.
5. On drop:
   - If dropped within **Dock** bounds: the app is added to the dock list.
   - If dropped within the **Home grid** bounds: the app is placed into the nearest slot (7×9).
   - Otherwise: drag is cancelled and nothing changes.

## Gesture model (why it works)
All Apps uses a **single, unified pointer pipeline** on each tile (no separate `clickable` + `onLongPress` competing).

### Tile gesture rules
- **Tap** (down → up without long-press): launches the app.
- **Long-press**:
  - If the user moves: starts a drag and streams pointer positions.
  - If the user releases without moving: opens the long-press menu.

This is implemented with:
- `awaitEachGesture`
- `awaitFirstDown`
- `awaitLongPressOrCancellation`
- `drag(...)` to stream movement

and `consumeAllChanges()` to prevent other recognizers from stealing/cancelling the stream.

### Why swipe-down search is disabled in All Apps
All Apps used to have a surface-level vertical drag detector. That conflicts with icon dragging (also vertical).
We removed it, and All Apps will get a dedicated top search bar later instead.

## Coordinator state (LauncherActivity)
`LauncherActivity` owns the drag state:
- `dragPayload: LaunchPoint?`
- `dragPosition: Offset?`

Derived:
- `dragging = dragPayload != null && dragPosition != null`

While `dragging`:
- All Apps is kept composed but faded (low alpha) to avoid node teardown/cancellation.
- A floating icon preview is rendered at `dragPosition`.
- `Back` cancels the drag.

## Drop targets
Two drop targets are currently supported:
- **Home grid bounds** (7×9 slots)
- **Dock bounds** (the dock bar area)

Bounds are tracked via `onGloballyPositioned` and `boundsInRoot()` and checked on drag end.

## Current limitations
- Drop currently places apps into the dock / home grid; it does not yet support:
  - Dragging already-placed apps to reorder
  - Multiple dock pages
  - Dragging from Home/Dock back to All Apps (remove)
- Drop uses bounding-rect hit-testing; no snap/slot placement yet.
