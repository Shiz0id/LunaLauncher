# Architecture

**Analysis Date:** 2026-02-20

## Pattern Overview

**Overall:** MVVM + Repository Pattern with modular UI architecture

Luna Launcher follows a reactive, flow-based architecture with clear separation between domain models, data/persistence, and UI layers. All state is exposed via `StateFlow` and observed through Compose's `collectAsStateWithLifecycle()`. The architecture is designed for:

- Single responsibility across modules
- Zero circular dependencies
- Absolute positioning (not grid-based) for home screen layout
- Pluggable search provider system (Just Type engine)
- Notification integration as a first-class search source

**Key Characteristics:**

- **No DI framework**: Manual service-locator via `LauncherContainer.create(context)` — intentionally simple, no Hilt/Dagger
- **100% Kotlin + Jetpack Compose**: No XML layouts, no LiveData
- **Room database with Flow**: Reactive queries, 11 migrations (never skip migration on schema change)
- **Modular UI**: Each UI concern (home, search, app menu) in its own module with strict dependency rules
- **State management centralized**: All app state flows through `LauncherViewModel` and specialized ViewModels (e.g., `DeckViewModel`)

---

## Layers

**Core Model Layer (`core-model`):**
- Purpose: Domain models and business logic with zero Android UI dependencies
- Location: `D:\Luna Launcher\launcher-android\core-model\src\main\kotlin`
- Contains:
  - `LaunchPoint` interface and `LaunchPointType` enum
  - `HomeIconPlacement` (absolute positioning model)
  - `JustTypeEngine` (search ranking and result assembly)
  - Search providers and notification integration
  - Theme and UI state models (`LauncherThemeStyle`, `LauncherColorTheme`)
- Depends on: Nothing (no Android framework, no Compose)
- Used by: All other modules

**Data Layer (`data`):**
- Purpose: Room database, DAOs, repositories, and persistence logic
- Location: `D:\Luna Launcher\launcher-android\data\src\main\kotlin`
- Contains:
  - Room entities: `LaunchPointEntity`, `DockEntryEntity`, `HomeSlotEntity`, `HomeIconEntity`, `DeckCardEntity`, `DeckWidgetEntity`, `JustTypeProviderEntity`
  - DAOs: `LaunchPointDao`, `DockDao`, `HomeSlotsDao`, `HomeIconPositionsDao`, `JustTypeProviderDao`, `DeckDao`
  - `LaunchPointRepository` — main CRUD + Flow observation interface
  - `DeckRepository` — widget deck card management
  - `JustTypeRegistry` — provider configuration persistence
  - Database: `LauncherDatabase` with 11 migrations
- Depends on: `core-model`
- Used by: `app`, repositories observing flows in ViewModels

**Android Services Layer (`apps-android`):**
- Purpose: Android-specific app scanning and LaunchPoint production
- Location: `D:\Luna Launcher\launcher-android\apps-android\src\main\kotlin`
- Contains:
  - `AndroidAppScanner` — queries PackageManager, produces `LaunchPoint` list
  - Icon key generation and package name parsing
  - Package change events (install/uninstall/update detection)
- Depends on: `core-model`, Android framework
- Used by: `app` (via `LauncherContainer`), `LauncherViewModel`

**Main App Module (`app`):**
- Purpose: Activity entry point, ViewModel orchestration, manual DI container
- Location: `D:\Luna Launcher\launcher-android\app\src\main\kotlin`
- Contains:
  - `LauncherActivity` — Sets up Compose, calls `LauncherRoot()`
  - `LauncherContainer` — Creates and wires all services and repositories
  - `LauncherApplication` — Application class with notification indexer
  - `LauncherViewModel` — Central state holder for home, dock, search, favorites
  - `PackageChangeReceiver` / `PackageChangeHandler` — Reactive to app install/uninstall/update
  - `IconRepository` — Icon loading with prefetch batching (36-icon chunks)
  - `DeckViewModel` — Manages widget deck state (cards, pages, widgets)
  - Supporting services: `DeckWidgetHost`, `DeckBitmapCache`
- Depends on: All other modules
- Used by: Nothing else (top-level wiring)

**UI: Home Screen (`ui-home`):**
- Purpose: Home surface, dock, All Apps drawer, Just Type search, widget deck overlay
- Location: `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin`
- Contains:
  - `HomeScreen.kt` — Main home surface with absolute icon positioning + dock
  - `AllAppsScreen.kt` — All Apps drawer (tabs: Apps, Favorites, Settings)
  - `JustType.kt` — Search UI integrated into home (NOT using `ui-search` module)
  - `HomeGestures.kt` + `GestureState.kt` — Unified gesture state machine
  - `GestureThresholds.kt` — Centralized thresholds for all gestures
  - Icon/widget gesture handlers: `IconGesture.kt`, `CanvasRotationGesture.kt`
  - `WidgetDeckOverlay.kt` — Full-screen deck UI with search support
  - Theme: `LauncherTheme.kt`, `GlassSurface.kt` (blur, gradient, Material 3 tokens)
  - `NotificationPermissionDialog.kt` — First-boot onboarding
- Depends on: `core-model`, `data`, `app` (for `IconRepository`), Android framework, Jetpack Compose
- Used by: `LauncherActivity` calls `HomeScreen()` and `WidgetDeckOverlay()` from `LauncherRoot()`

**UI: App Menu (`ui-appmenu`):**
- Purpose: Long-press context menu (bottom sheet)
- Location: `D:\Luna Launcher\launcher-android\ui-appmenu\src\main\kotlin`
- Contains: `LaunchPointMenuSheet.kt` — Bottom sheet with actions (pin, hide, uninstall, etc.)
- Depends on: `core-model`, Jetpack Compose
- Used by: `LauncherActivity` in `LauncherRoot()`

**UI: Search (Legacy/Unused - `ui-search`):**
- Purpose: Full-screen dialog-based search (deprecated design)
- Location: `D:\Luna Launcher\launcher-android\ui-search\src\main\kotlin`
- Contains: `SearchOverlay.kt`
- **STATUS**: Unused — all active search is in `ui-home/JustType.kt`
- Notes: Do not use this module for new search work; integrate search into `ui-home`

---

## Data Flow

### App Launch & Initialization

1. `LauncherApplication.onCreate()` creates `NotificationIndexer` (in-memory notification tracking)
2. `LauncherActivity.onCreate()` calls `setContent()` with `LauncherRoot()` composable
3. `LauncherRoot()`:
   - Creates `LauncherViewModel` and `DeckViewModel` via factory functions receiving `LauncherContainer`
   - `LauncherViewModel` calls `repository.observeVisibleLaunchPoints()` → `StateFlow<List<LaunchPointRecord>>`
   - Subscribes to multiple flows: `appsItems`, `dockItems`, `homeIcons`, `searchQuery`, `justTypeState`
   - Renders `HomeScreen()` with all callbacks wired back to ViewModel methods
4. `LauncherViewModel.refreshInstalledApps()` triggers `scanner.scan()` → `repository.syncAndroidApps()`

**Flow Diagram:**

```
User Touch
    ↓
HomeScreen.pointerInput { HomeGestures.kt }
    ↓
GestureState machine determines gesture type
    ↓
If icon: IconGesture.kt claims ownership
    ↓
If home surface: trigger search/edit/swipe callbacks
    ↓
Callbacks invoke LauncherViewModel methods
    ↓
ViewModel updates repository state (Room)
    ↓
Repository flows emit new lists
    ↓
StateFlow updates propagate to Compose
    ↓
Composables observe via collectAsStateWithLifecycle()
    ↓
UI recomposes with new state
```

### Search (Just Type) Flow

1. User types in search field → `onSearchQueryChange` callback
2. `LauncherViewModel.setSearchQuery(query)` updates `_searchQuery` StateFlow
3. Search query triggers `JustTypeEngine.buildState()` calculation:
   - Parses `@Category` filter (e.g., "@Apps chrome")
   - Runs `AppsProvider.itemsFor()` on visible apps
   - Runs `NotificationsProvider.itemsFor()` from `NotificationIndexer`
   - Runs `SearchTemplatesProvider.itemsFor()` for action suggestions
   - Runs `ActionsProvider.itemsFor()` for system actions
   - Combines and sorts results by provider config + recency
4. Result emitted as `justTypeState: StateFlow<JustTypeUiState>`
5. `HomeScreen()` observes `justTypeState` and `JustType.kt` renders results

**Notification Search Integration:**

- `NotificationIndexer` (in-memory, thread-safe) maintains concurrent map of notifications
- `LunaNotificationListenerService` (extends `NotificationListenerService`) posts/removes notifications to indexer
- Notifications never persisted to disk (privacy-first design)
- Max 4-day retention in memory
- Supports action execution (reply, mark-as-read, dismiss)

### Home Icon Placement Flow

1. User drags icon in edit mode or from All Apps → `onUpdateHomeIcon(id, xNorm, yNorm, rot)`
2. `LauncherViewModel.updateHomeIconPosition()` calls `repository.updateHomeIconPosition()`
3. Repository upserts `HomeIconEntity` with:
   - `xNorm`, `yNorm` (normalized 0.0..1.0 coordinates)
   - `rotationDeg` (snapped to 5° increments)
   - `zIndex` (preserved if exists, else maxZ+1)
   - `updatedAtEpochMs` (conflict resolution timestamp)
4. `HomeIconPositionsDao` emits new list via Flow
5. `LauncherViewModel.homeIcons` StateFlow updates
6. `HomeScreen()` repositions icons via `offset()` modifier with absolute layout

---

## Key Abstractions

**LaunchPoint Interface:**
- Purpose: Runtime-agnostic description of any launchable item
- Location: `core-model/src/main/kotlin/com/lunasysman/launcher/core/model/LaunchPoint.kt`
- Contract:
  - `id: String` — Stable, immutable canonical ID (format: `android:<pkg>/<activity>@<versionCode>` or custom)
  - `type: LaunchPointType` — ANDROID_APP or WEBOS_APP
  - `title`, `iconKey`, `pinned`, `hidden`, `lastLaunchedAtEpochMs`
- Pattern: Immutable data class with mutable state in database
- Why: Allows future integration of non-Android sources (webOS apps, web shortcuts) without UI changes

**JustTypeEngine:**
- Purpose: Stateless search result assembly and ranking
- Location: `core-model/src/main/kotlin/com/lunasysman/launcher/core/justtype/engine/JustTypeEngine.kt`
- Responsibility: `buildState(query, allApps, ...) → JustTypeUiState`
  - Parses category filters (`@Apps`, `@Notifications`, `@Actions`)
  - Iterates enabled providers in order
  - Ranks results by: token prefix match → recency bonus → pin bonus (20 pts) → substring fallback
  - Sections results by category
- Why: Pure function (no side effects) enables testing and provider swapping

**HomeGestureState Machine:**
- Purpose: Unified gesture priority and state coordination
- Location: `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/GestureState.kt`
- States:
  - `Idle` — No touch
  - `Pressed(position, time)` — Finger down, determining gesture type
  - `LongPressArmed(position, time)` — Timer set, waiting for long-press or drag
  - `Swiping(start, current, accumulated)` — Vertical swipe in progress
  - `EditModeActive` — Home icons owned by user, rotation/drag enabled
  - `ChildOwned(ownerId, gestureType)` — Icon or widget claims gesture
- Valid transitions enforced by `HomeGestureOwner.isValidTransition()`
- Why: Prevents conflicting gesture interpretations (e.g., swipe-up-to-all-apps while dragging an icon)

**Repository Pattern (LaunchPointRepository):**
- Purpose: Single source of truth for launch point data
- Location: `data/src/main/kotlin/com/lunasysman/launcher/data/LaunchPointRepository.kt`
- API:
  - `observeVisibleLaunchPoints()` — Flow of all visible apps (filtered by hidden/disabled)
  - `observeDockEntries()` — Flow of 5 dock items with positions
  - `observeHomeIconPositions()` — Flow of absolute-positioned home icons
  - `syncAndroidApps(List<LaunchPoint>)` — Upserts scanned apps, deletes uninstalled
  - `markLaunched(id, epochMs)` — Records launch timestamp for ranking
  - `pin(id, pinned)` — Marks as favorite
  - `hide(id, hidden)` — Marks as hidden
- Why: All UI reads/writes go through this interface; enables testing and future persistence strategies

---

## Entry Points

**LauncherActivity:**
- Location: `app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt`
- Triggers: System launcher selection or app icon tap
- Responsibilities:
  - Inherits from `ComponentActivity`
  - Retrieves `LauncherContainer` from `LauncherApplication.container`
  - Calls `setContent()` with `LauncherRoot()` composable
  - Handles safe Intent launching with error toasts
  - Sets window to draw behind system bars (wallpaper shows under status bar)

**LauncherApplication:**
- Location: `app/src/main/kotlin/com/lunasysman/launcher/LauncherApplication.kt`
- Triggers: Process start
- Responsibilities:
  - Creates `LauncherContainer` as singleton
  - Creates `NotificationIndexer` singleton
  - Initializes Package change receiver

**LauncherRoot Composable:**
- Location: `app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt` (nested in LauncherActivity file)
- Triggers: Activity.onCreate() → setContent()
- Responsibilities:
  - Wires ViewModels via factory functions
  - Observes all state flows
  - Manages dialog visibility (notification permission, reply dialog)
  - Coordinates search state between home and deck
  - Orchestrates drag-and-drop (All Apps → Home/Dock)
  - Renders main UI tree: `HomeScreen()`, `WidgetDeckOverlay()`, `AllAppsScreen()`, `LaunchPointMenuSheet()`

---

## Error Handling

**Strategy:** Defensive cascading with user feedback

**Patterns:**

1. **App Launch Failures:**
   - Try-catch in `LauncherActivity.setContent()` handler
   - `ActivityNotFoundException` → log + toast "Unable to launch app"
   - `SecurityException` → log + toast "Unable to launch app"
   - Generic `Exception` → log + toast "Unable to launch app"

2. **Unresolvable LaunchPoints:**
   - `AndroidLaunchException.Unresolvable` caught in `launchById()`
   - Automatically hides the app: `vm.hide(id, true)`
   - Toast: "App unavailable"

3. **Database Operations:**
   - Room queries wrapped in `try-catch` at DAO level
   - `withTransaction` ensures consistency on multi-step operations
   - Migration failures caught in `LauncherContainer.create()` — keeps launcher usable

4. **Notification Indexer Initialization:**
   - Wrapped in `try-catch` in `LauncherContainer.create()`
   - If init fails, `NotificationIndexer()` still created (safe fallback)
   - Prevents launcher crash due to notification setup issues

5. **Permission Checks:**
   - `NotificationPermissionHelper.isNotificationAccessGranted(context)` checks before querying
   - First-boot dialog prompts user to enable (stored in SharedPreferences)
   - Missing permissions degrade gracefully (search still works without notifications)

---

## Cross-Cutting Concerns

**Logging:**
- Pattern: `Log.e("LunaLauncher", message, exception)` for errors
- Gesture debug: `GestureDebug.enabled` flag, filtered by tag `GestureDebug` in Logcat

**Validation:**
- Home icon coordinates clamped: `xNorm.coerceIn(0.0, 1.0)`, `yNorm.coerceIn(0.0, 1.0)`
- Dock item limit enforced: max 5 items (UI toast: "Dock is full (5 apps)")
- Search query length: no explicit limit, but UI debounces at `500ms`
- Icon rotation snapped to 5° increments via UI constraint

**Authentication:**
- Notification listener permission: requested via `NotificationListenerService` platform mechanism
- Contacts permission: requested via `ActivityResultContracts.RequestPermission()` for search integration
- No other authentication (launcher runs with app context permissions)

**Caching & Prefetching:**
- Icon bitmap cache: `IconRepository.prefetch(keys, maxCount=36)` prefetches in batches
- Deck widget bitmap cache: `DeckBitmapCache` stores snapshots of rendered cards
- No in-memory app list cache (always observes repository Flow)

**Threading:**
- Database IO: `Dispatchers.IO` for Room queries
- Computation: `Dispatchers.Default` for search ranking and scanning
- Main: `Dispatchers.Main` for Compose state updates (automatic via `collectAsStateWithLifecycle()`)
- App-wide scope: `CoroutineScope(SupervisorJob() + Dispatchers.Default)` in `LauncherContainer`

---

*Architecture analysis: 2026-02-20*
