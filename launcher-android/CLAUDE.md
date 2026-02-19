# CLAUDE.md — AI Agent Guide for Luna Launcher (Android)

This file is the primary onboarding document for AI coding agents working on this codebase.
Read this first. Refer to `docs/` for deep-dives on specific subsystems.

---

## Project Summary

**Luna Launcher** is a modern Android home screen launcher inspired by webOS/Luna design principles. It replaces the stock Android launcher with a simplified, elegant experience featuring absolute-positioned home icons, a "Just Type" universal search overlay, notification integration, and a widget deck system.

- **Language**: 100% Kotlin
- **UI**: Jetpack Compose (no XML layouts)
- **Architecture**: MVVM + Repository pattern, StateFlow-based reactive UI
- **Min SDK**: 30 (Android 11) / **Target SDK**: 35 (Android 15)
- **Build**: Gradle 8.7, AGP 8.7.0, Kotlin 2.0.21, Compose BOM 2024.12.01

---

## Module Map

```
launcher-android/
├── app/            Main wiring — LauncherActivity, LauncherViewModel, DI container
├── core-model/     Domain models + business logic (NO Compose dependency)
├── data/           Room database, DAOs, repositories, DataStore prefs
├── apps-android/   Android PackageManager scanner → LaunchPoint producer
├── ui-home/        Home screen, All Apps drawer, gestures, theme, dock
├── ui-search/      Just Type search overlay UI
├── ui-appmenu/     Long-press context menu (bottom sheet)
└── docs/           Architecture docs (gestures, drag-drop, data model)
```

### Dependency flow (top → bottom)

```
        app
      / | \ \
ui-home ui-search ui-appmenu
      \ | /
       data          apps-android
        \           /
         core-model
```

**Rule**: `core-model` has zero Android UI dependencies. UI modules depend on `core-model` but never on each other. Only `:app` wires everything together.

---

## Key Files to Read First

| File | Why |
|------|-----|
| `app/.../LauncherActivity.kt` | Entry point. Composes the entire UI tree via `LauncherRoot()` |
| `app/.../LauncherViewModel.kt` | Central state holder — apps, search, home icons, dock |
| `app/.../LauncherContainer.kt` | Manual DI container (no Hilt/Dagger) |
| `core-model/.../model/LaunchPoint.kt` | Core abstraction — everything launchable is a LaunchPoint |
| `core-model/.../justtype/engine/JustTypeEngine.kt` | Search engine — builds ranked results from providers |
| `data/.../LauncherDatabase.kt` | Room DB with 11 migrations — read before adding schema changes |
| `data/.../LaunchPointRepository.kt` | CRUD + Flow-based observation for launch points |
| `ui-home/.../HomeScreen.kt` | Home surface with absolute icon positioning + gestures |
| `ui-home/.../HomeGestures.kt` | Unified gesture pipeline (state machine) |

---

## Architecture Patterns

### State management
- All UI state exposed as `StateFlow` (never LiveData)
- Private `MutableStateFlow` with public `.asStateFlow()` exposure
- `SharingStarted.WhileSubscribed(5000)` for memory efficiency
- Compose observes via `collectAsStateWithLifecycle()`

### Dependency injection
- **Manual service-locator** via `LauncherContainer.create(context)`
- No Hilt, no Dagger — intentionally simple
- All repositories and scanners are created once in the container
- ViewModels receive dependencies through constructor (via container)

### Database
- Room with Kotlin Flow return types (no RxJava)
- **11 migrations** — always add a new migration, never bump version without one
- Migrations live in `LauncherDatabase.kt` as companion `MIGRATION_X_Y` objects
- Schema evolved: simple grid → absolute positioning → deck widgets

### Gesture system
- Single unified `pointerInput` per surface (no mixed MotionEvent stacks)
- State machine in `GestureState.kt`: `Idle → Pressed → LongPressArmed → {Dragging|Rotating|Swiping}`
- All thresholds centralized in `GestureThresholds.kt`
- Debug mode: set `GestureDebug.enabled = true` for Logcat logging
- See `docs/Gestures.md` for the full reference

### Search (Just Type)
- `JustTypeEngine.buildState()` assembles results from pluggable providers
- Providers: `AppsProvider`, `NotificationsProvider`, `ActionsProvider`, `SearchTemplatesProvider`
- Category filters: `@Apps`, `@Notifications`, `@Actions` narrow results
- Scoring: token prefix match + recency bonus + pin bonus (20 pts) + substring fallback
- Provider configs persisted in Room via `JustTypeProviderEntity`

#### Search UI Architecture
The search UI is **integrated into the home screen**, NOT in a separate overlay module:
- **Active UI**: `JustType.kt` in `ui-home` — search panel below top search bar
- **Styling**: Theme-aware via `LauncherTheme` tokens, supports SMOKY_GLASS and CRYSTAL_GLASS
- **Results**: Card-based layout with sections (Apps, Notifications, Actions)
- **State**: Managed by `LauncherViewModel.setSearchQuery()` → `JustTypeEngine.buildState()`

**Note on `ui-search` module**: Contains `SearchOverlay.kt` (full-screen dialog approach) but is **currently unused**—no imports or references in active code. This may be legacy code from an earlier design. Do not use this module for new search functionality; all search work should be in `ui-home/JustType.kt`.

---

## Notification System

Luna integrates deeply with Android notifications for search and quick actions.

- **Service**: `LunaNotificationListenerService` (extends `NotificationListenerService`)
- **Index**: `NotificationIndexer` — thread-safe in-memory `ConcurrentHashMap`
- **Privacy**: All data in-memory only, never persisted to disk, 4-day retention
- **Actions**: Supports reply, mark-as-read, dismiss via `NotificationActionExecutor`
- **Permission**: One-time first-boot dialog (`NotificationPermissionDialog.kt`)
- **Detailed docs**: `NOTIFICATIONS_INTEGRATION.md`, `FIRST_BOOT_COMPLETE.md`

---

## Home Screen Layout

Home icons use **absolute normalized coordinates**, not a grid:

```kotlin
data class HomeIconPlacement(
    val xNorm: Float,    // 0.0..1.0 (left..right)
    val yNorm: Float,    // 0.0..1.0 (top..bottom)
    val rotationDeg: Float,  // degrees, snapped to 5°
    val zIndex: Int,
    val updatedAtEpochMs: Long  // conflict resolution
)
```

Persisted via `HomeIconEntity` / `HomeIconPositionsDao`. Edit mode enables drag (1 finger) and rotation (2 fingers).

---

## Build & Run

```bash
# Generate Gradle wrapper if missing (one-time)
gradle wrapper --gradle-version 8.7

# Build
./gradlew clean build

# Install debug APK
./gradlew installDebug
```

**Requirements**: Android SDK with API 34, JDK 17

**Gradle properties** (`gradle.properties`):
- `org.gradle.parallel=true`
- `org.gradle.caching=true`
- `android.nonTransitiveRClass=true`

---

## Conventions to Follow

### Naming
- `_privateFlow` / `publicFlow` for StateFlow backing fields
- `LaunchPoint` IDs: `android:<package>/<activity>` (stable, canonical)
- Icon keys: `android:<package>/<activity>@<versionCode>` (cache-busting)
- Entity classes: `*Entity.kt`, DAOs: `*Dao.kt`

### Code style
- Prefer `data class` + `copy()` for immutable state updates
- Use `Dispatchers.IO` for database/disk work, `Dispatchers.Default` for computation
- Compose: one `pointerInput` block per gesture surface (no stacking)
- No `pointerInteropFilter` — all gestures are pure Compose

### What NOT to do
- Do **not** add Hilt/Dagger — the manual DI is intentional
- Do **not** use LiveData — the project standardized on StateFlow
- Do **not** add XML layouts — everything is Compose
- Do **not** change `LaunchPoint.id` format — it's a stable key across DB, sync, and intents
- Do **not** skip migrations — always write `MIGRATION_X_Y` when changing Room schema
- Do **not** persist notification data to disk — privacy-first design

---

## Existing Documentation

| Document | Location | Covers |
|----------|----------|--------|
| Gesture system | `docs/Gestures.md` | All gestures, state machine, thresholds, conflicts |
| LaunchPoint contract | `docs/LaunchPoint.md` | ID format, type stability, icon keys |
| Drag-and-drop | `docs/AllAppsDrag.md` | All Apps → Home/Dock drag implementation |
| Absolute layout TODO | `docs/HOME_ABSOLUTE_LAYOUT_TODO.md` | Future work for home layout |
| Notification integration | `NOTIFICATIONS_INTEGRATION.md` | Full notification system architecture |
| Permission flow | `NOTIFICATION_PERMISSION_FLOW.md` | First-boot permission UX |
| First boot complete | `FIRST_BOOT_COMPLETE.md` | Onboarding flow summary |
| Compilation fixes | `COMPILATION_FIXES.md` | Past build issues and their resolutions |

---

## Feature Status

### Complete
- Home screen with absolute positioning + rotation
- All Apps drawer with tabs (Apps / Favorites / Settings)
- Drag-and-drop from All Apps to Home and Dock
- Just Type search (apps + notifications + actions + templates)
- Notification listener integration with action support
- First-boot notification permission onboarding
- Multiple Material 3 theme variants
- Unified gesture system with state machine
- Icon repository with async loading and prefetch (36-icon batches)
- Package change receiver (install/uninstall/update events)

### In Progress
- Widget Deck (DB schema added, UI partially complete)
- Just Type provider settings activity

### Future
- Full settings screen for launcher preferences
- WebOS app integration (architecture already supports it via LaunchPoint abstraction)
- Smart ranking based on usage patterns
- Notification history / optional persistence

---

## Common Tasks for AI Agents

### Adding a new search provider
1. Create a class implementing the provider pattern in `core-model/.../justtype/providers/`
2. Register it in `JustTypeProviderConfig.kt`
3. Add a `JustTypeProviderEntity` default in `JustTypeRegistryInitializer.kt`
4. Wire it into `JustTypeEngine.buildState()`

### Adding a new Room entity
1. Create `*Entity.kt` + `*Dao.kt` in `:data`
2. Add the entity to `@Database(entities = [...])` in `LauncherDatabase.kt`
3. Bump the database version
4. Write a `MIGRATION_X_Y` in `LauncherDatabase.kt` companion
5. Add the migration to `.addMigrations(...)` in the builder

### Adding a new UI screen
1. Create a Composable in the appropriate `ui-*` module
2. Wire it through `LauncherActivity.kt` → `LauncherRoot()`
3. Add necessary state to `LauncherViewModel` if needed
4. Follow existing patterns: StateFlow, no XML, no LiveData

### Debugging gestures
1. Set `GestureDebug.enabled = true` in `GestureThresholds.kt`
2. Filter Logcat by tag `GestureDebug`
3. Review state transitions against the state machine in `docs/Gestures.md`
