# Codebase Structure

**Analysis Date:** 2026-02-20

## Directory Layout

```
Luna Launcher/
├── launcher-android/                     # Main Android project (Gradle multi-module)
│   ├── app/                              # Main app module: wiring, activities, ViewModels
│   ├── core-model/                       # Domain models & business logic (no Android UI deps)
│   ├── data/                             # Room database, entities, DAOs, repositories
│   ├── apps-android/                     # Android-specific app scanning
│   ├── ui-home/                          # Home screen, dock, All Apps, search, gestures
│   ├── ui-search/                        # Legacy/unused search overlay module
│   ├── ui-appmenu/                       # Long-press context menu (bottom sheet)
│   ├── docs/                             # Architecture documentation
│   ├── gradle/                           # Gradle wrapper files
│   ├── build.gradle.kts                  # Root build config
│   ├── settings.gradle.kts               # Module declaration
│   ├── gradle.properties                 # Gradle properties (parallel, caching)
│   ├── gradlew / gradlew.bat             # Gradle wrapper scripts
│   ├── local.properties                  # SDK path (local, not committed)
│   ├── CLAUDE.md                         # AI agent onboarding guide
│   └── [other config files]
├── Legacy References/                    # Old/archived code (not actively used)
├── screenshots/                          # Feature screenshots
├── README.md                             # Project overview
├── LICENSE                               # Open source license
└── .planning/
    └── codebase/                         # Generated analysis documents (ARCHITECTURE.md, etc.)
```

## Directory Purposes

**app/**
- Purpose: Main application wiring, activities, ViewModels, dependency injection container
- Contains:
  - `LauncherActivity.kt` — Activity entry point + root Composable
  - `LauncherViewModel.kt` — Central state holder (apps, dock, home icons, search)
  - `LauncherContainer.kt` — Manual DI service locator
  - `LauncherApplication.kt` — Application class
  - `PackageChangeReceiver.kt` / `PackageChangeHandler.kt` — Monitor app installs/uninstalls
  - `IconRepository.kt` — Icon loading and caching with prefetch batching
  - `LauncherEvent.kt` — Event bus for cross-ViewModel communication
  - `deck/` subdir: `DeckViewModel.kt`, `DeckWidgetHost.kt`, `DeckBitmapCache.kt`
- Dependencies: Depends on all other modules
- Modules: `build.gradle.kts` configures main app with Compose, Room, Lifecycle dependencies

**core-model/**
- Purpose: Domain models and pure business logic (no Android UI framework)
- Contains:
  - `model/` — `LaunchPoint.kt` (interface), `LaunchPointType.kt`, `LaunchPointAction.kt`, `HomeIconPlacement.kt`, theme models
  - `justtype/` — Search system:
    - `engine/JustTypeEngine.kt` — Stateless search ranking and result assembly
    - `providers/` — `AppsProvider.kt`, `NotificationsProvider.kt`, `ActionsProvider.kt`, `SearchTemplatesProvider.kt`
    - `model/` — `JustTypeUiState.kt`, `JustTypeItemUi.kt`, `JustTypeCategory.kt`, `JustTypeNotificationsOptions.kt`
    - `notifications/` — `NotificationIndexer.kt`, `NotificationActionExecutor.kt`, `NotificationPermissionHelper.kt`
    - `registry/` — `JustTypeProviderConfig.kt` (provider metadata)
- Dependencies: None (zero Android UI deps intentional)
- Modules: `build.gradle.kts` configures pure Kotlin library

**data/**
- Purpose: Database, DAOs, repositories, persistence
- Contains:
  - `LaunchPointEntity.kt` / `LaunchPointDao.kt` — Main app entity
  - `DockEntryEntity.kt` / `DockDao.kt` — Dock slots
  - `HomeSlotEntity.kt` / `HomeSlotsDao.kt` — (legacy grid slots, mostly unused)
  - `HomeIconEntity.kt` / `HomeIconPositionsDao.kt` — Absolute-positioned home icons
  - `DeckCardEntity.kt` / `DeckDao.kt` — Widget deck cards
  - `DeckWidgetEntity.kt` — Widgets on deck cards
  - `JustTypeProviderEntity.kt` / `JustTypeProviderDao.kt` — Search provider config
  - `LaunchPointRepository.kt` — Main CRUD interface
  - `DeckRepository.kt` — Deck-specific CRUD
  - `JustTypeRegistry.kt` — Provider config + preferences wrapper
  - `JustTypePrefs.kt` — SharedPreferences for search defaults
  - `JustTypeRegistryInitializer.kt` — Default provider setup
  - `LaunchPointRecord.kt` — View/record combining entity with computed fields
  - `LauncherDatabase.kt` — Room database definition + 11 migrations
- Dependencies: `core-model` only
- Modules: `build.gradle.kts` configures Room, Flow, Kotlin

**apps-android/**
- Purpose: Android-specific package scanning and LaunchPoint production
- Contains:
  - `AndroidAppScanner.kt` — Queries PackageManager, produces list of `LaunchPoint`
  - App list filtering, icon key generation, package name parsing
- Dependencies: `core-model`, Android framework
- Modules: `build.gradle.kts`

**ui-home/**
- Purpose: Home screen surface, dock, All Apps drawer, search UI, gestures, theme
- Contains:
  - `HomeScreen.kt` — Main home surface (absolute-positioned icons + dock + search bar)
  - `AllAppsScreen.kt` — All Apps drawer with tabbed interface (Apps/Favorites/Settings)
  - `JustType.kt` — Search results panel (integrated into home, NOT using `ui-search`)
  - `HomeGestures.kt` — Gesture handling and state transitions
  - `GestureState.kt` — Gesture state machine definition
  - `GestureThresholds.kt` — Centralized gesture parameters (long-press timeout, swipe distance, etc.)
  - `IconGesture.kt` — Single-icon drag and tap handling
  - `CanvasRotationGesture.kt` — Two-finger rotation on icons
  - `LaunchPointTile.kt` — Reusable icon + label tile component
  - `WidgetDeckOverlay.kt` — Full-screen widget deck UI
  - `NotificationPermissionDialog.kt` — First-boot onboarding
  - `theme/` subdir:
    - `LauncherTheme.kt` — Material 3 theme with style variants (SMOKY_GLASS, CRYSTAL_GLASS) and color themes
    - `GlassSurface.kt` — Frosted glass effect (blur + gradient) for search panel
  - `PointerCompat.kt` — Utility for touch event handling compatibility
- Dependencies: `core-model`, `data`, `app` (IconRepository), Android framework, Jetpack Compose
- Modules: `build.gradle.kts` configures Compose, Material 3, Lifecycle, core-ktx

**ui-appmenu/**
- Purpose: Long-press context menu for launch points
- Contains:
  - `LaunchPointMenuSheet.kt` — Bottom sheet with actions (pin, hide, uninstall, app info, etc.)
- Dependencies: `core-model`, Jetpack Compose
- Modules: `build.gradle.kts`

**ui-search/** (DEPRECATED/UNUSED)
- Purpose: Full-screen dialog-based search (legacy approach)
- Contains: `SearchOverlay.kt`
- Status: **Not imported or referenced in active code**
- Notes: All search work is in `ui-home/JustType.kt`; do not use this module for new features

**docs/**
- Purpose: Architecture and design documentation
- Contains:
  - `Gestures.md` — Complete gesture system reference (state machine, thresholds, conflicts)
  - `LaunchPoint.md` — LaunchPoint contract and ID format specification
  - `AllAppsDrag.md` — Drag-and-drop implementation details
  - `HOME_ABSOLUTE_LAYOUT_TODO.md` — Future layout work
  - `NOTIFICATIONS_INTEGRATION.md` — Notification indexer architecture
  - `NOTIFICATION_PERMISSION_FLOW.md` — First-boot permission UX
  - `FIRST_BOOT_COMPLETE.md` — Onboarding flow summary
  - `COMPILATION_FIXES.md` — Past build issues and resolutions

---

## Key File Locations

**Entry Points:**
- `app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt` — Activity entry point
- `app/src/main/kotlin/com/lunasysman/launcher/LauncherApplication.kt` — Application class
- `app/src/main/AndroidManifest.xml` — Manifest (if exists; Android plugin generates much)

**Configuration:**
- `app/build.gradle.kts` — App module build config (dependencies, compileSdk, targetSdk, etc.)
- `core-model/build.gradle.kts` — Core model module config
- `data/build.gradle.kts` — Data module config
- `build.gradle.kts` — Root build config (plugin versions, shared repos)
- `settings.gradle.kts` — Module includes (defines multi-module structure)
- `gradle.properties` — Global properties (org.gradle.parallel=true, etc.)
- `gradle/` — Gradle wrapper version definitions

**Core Logic:**
- `core-model/src/main/kotlin/com/lunasysman/launcher/core/model/LaunchPoint.kt` — Core abstraction
- `core-model/src/main/kotlin/com/lunasysman/launcher/core/justtype/engine/JustTypeEngine.kt` — Search ranking
- `data/src/main/kotlin/com/lunasysman/launcher/data/LaunchPointRepository.kt` — Main data API
- `data/src/main/kotlin/com/lunasysman/launcher/data/LauncherDatabase.kt` — Database + migrations
- `app/src/main/kotlin/com/lunasysman/launcher/LauncherViewModel.kt` — Central state
- `app/src/main/kotlin/com/lunasysman/launcher/LauncherContainer.kt` — DI container

**Testing:**
- No test directories found in exploration; tests likely in `src/test/` and `src/androidTest/` per module
- Run: `./gradlew test` (unit) and `./gradlew connectedAndroidTest` (instrumented)

**Theme & Styling:**
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/theme/LauncherTheme.kt` — Material 3 theme definition
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/theme/GlassSurface.kt` — Frosted glass component

**Gesture System:**
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/GestureState.kt` — State machine
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/GestureThresholds.kt` — Parameters
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/HomeGestures.kt` — Gesture input handling
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/IconGesture.kt` — Icon-specific gestures

**Notification System:**
- `core-model/src/main/kotlin/com/lunasysman/launcher/core/justtype/notifications/NotificationIndexer.kt` — In-memory index
- `app/src/main/kotlin/com/lunasysman/launcher/notifications/LunaNotificationListenerService.kt` — Listener (if exists)

---

## Naming Conventions

**Files:**
- `*Activity.kt` — Android Activity classes (e.g., `LauncherActivity.kt`)
- `*ViewModel.kt` — MVVM ViewModel classes (e.g., `LauncherViewModel.kt`)
- `*Repository.kt` — Data access layer (e.g., `LaunchPointRepository.kt`)
- `*Entity.kt` — Room database entities (e.g., `LaunchPointEntity.kt`)
- `*Dao.kt` — Room Data Access Objects (e.g., `LaunchPointDao.kt`)
- `*Provider.kt` — Search provider implementations (e.g., `AppsProvider.kt`)
- `*Service.kt` — Android services (e.g., `LunaNotificationListenerService.kt`)
- `*Receiver.kt` — Android broadcast receivers (e.g., `PackageChangeReceiver.kt`)
- `*Engine.kt` — Core algorithms (e.g., `JustTypeEngine.kt`)
- `*Scanner.kt` — Source scanners (e.g., `AndroidAppScanner.kt`)

**Directories:**
- `src/main/kotlin/com/lunasysman/launcher/` — Main source
- `src/main/kotlin/com/lunasysman/launcher/core/` — `core-model` content
- `src/main/kotlin/com/lunasysman/launcher/data/` — `data` module content
- `src/main/kotlin/com/lunasysman/launcher/ui/home/` — `ui-home` content
- `src/main/kotlin/com/lunasysman/launcher/ui/appmenu/` — `ui-appmenu` content
- `src/main/kotlin/com/lunasysman/launcher/apps/android/` — `apps-android` content

**Compose Functions:**
- PascalCase for Composable functions (e.g., `HomeScreen()`, `AllAppsScreen()`)
- Lowercase for private/internal functions
- `on*` suffix for event callbacks (e.g., `onItemClick`, `onSearchQueryChange`)

**Variables:**
- `_privateFlow` / `publicFlow` for StateFlow backing fields (convention: private mutable, public immutable)
- camelCase for properties and variables
- UPPER_SNAKE_CASE for constants
- `it` for single-parameter lambdas

---

## Where to Add New Code

**New Feature (e.g., Widget Deck Enhancements):**
- Domain models → `core-model/src/main/kotlin/com/lunasysman/launcher/core/model/`
- Database entities/DAOs → `data/src/main/kotlin/com/lunasysman/launcher/data/`
- Repository methods → `data/src/main/kotlin/com/lunasysman/launcher/data/LaunchPointRepository.kt` (or new `*Repository.kt`)
- ViewModel logic → `app/src/main/kotlin/com/lunasysman/launcher/DeckViewModel.kt` (existing)
- UI → `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/WidgetDeckOverlay.kt` (existing) or new file in same dir

**New Search Provider:**
1. Create `core-model/src/main/kotlin/com/lunasysman/launcher/core/justtype/providers/YourProvider.kt`
2. Implement search logic (function `itemsFor(query, ...): List<JustTypeItemUi>`)
3. Register in `core-model/src/main/kotlin/com/lunasysman/launcher/core/justtype/registry/JustTypeProviderConfig.kt`
4. Add default instance in `data/src/main/kotlin/com/lunasysman/launcher/data/JustTypeRegistryInitializer.kt`
5. Wire into `JustTypeEngine.buildState()` in `core-model/src/main/kotlin/com/lunasysman/launcher/core/justtype/engine/JustTypeEngine.kt`

**New Component/Module:**
1. Create module directory at `launcher-android/ui-mynewmodule/`
2. Create `ui-mynewmodule/build.gradle.kts` (inherit from root plugin)
3. Create main Composable in `ui-mynewmodule/src/main/kotlin/com/lunasysman/launcher/ui/mynewmodule/MyScreen.kt`
4. Add to `settings.gradle.kts`: `include(":ui-mynewmodule")`
5. Add dependency in `app/build.gradle.kts`: `implementation(project(":ui-mynewmodule"))`
6. Wire into `LauncherRoot()` in `app/src/main/kotlin/com/lunasysman/launcher/LauncherActivity.kt`

**Utilities & Helpers:**
- Shared helpers for core logic → `core-model/src/main/kotlin/com/lunasysman/launcher/core/` (new file)
- Shared UI utilities (Compose extensions) → `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/` (new file, but prefer shared in `core-model` if no Compose dependency)
- Android-specific utilities → `apps-android/src/main/kotlin/com/lunasysman/launcher/apps/android/`

**Database Schema Change:**
1. Create new Entity class in `data/src/main/kotlin/com/lunasysman/launcher/data/*Entity.kt`
2. Create DAO interface in `data/src/main/kotlin/com/lunasysman/launcher/data/*Dao.kt`
3. Bump `LauncherDatabase.VERSION` in `data/src/main/kotlin/com/lunasysman/launcher/data/LauncherDatabase.kt`
4. Add `@Database(entities = [..., YourEntity::class])` annotation
5. Write `MIGRATION_X_Y` as companion object in `LauncherDatabase.kt`
6. Register migration in builder: `.addMigrations(MIGRATION_X_Y, ...)`
7. Never skip migrations or bump version without migration

---

## Special Directories

**build/ & .gradle/**
- Purpose: Generated build artifacts and Gradle cache
- Generated: Yes
- Committed: No (in `.gitignore`)

**.idea/**
- Purpose: Android Studio project metadata
- Generated: Yes
- Committed: No

**gradle/**
- Purpose: Gradle wrapper JAR and version files
- Generated: No (wrapper files committed for reproducible builds)
- Committed: Yes (except `gradle/wrapper/gradle-wrapper.jar` sometimes)

**docs/**
- Purpose: Architecture reference documentation
- Generated: No (manually curated)
- Committed: Yes

**launcher-android/.claude/**
- Purpose: AI agent context (if using Claude for code generation)
- Generated: Possibly
- Committed: Depends on .gitignore

---

*Structure analysis: 2026-02-20*
