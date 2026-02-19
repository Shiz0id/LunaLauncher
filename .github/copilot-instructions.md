# Copilot Instructions — LunaSysMan

## Project Overview

This workspace is an **Android launcher port of the webOS Luna System Manager**. It contains:

- **`launcher-android/`** — The active Android app (100% Kotlin, Jetpack Compose). This is where nearly all development happens.
- **`luna-sysmgr/`, `luna-appmanager/`, `luna-webappmanager/`, `luna-sysmgr-common/`, `luna-universalsearchmgr/`** — C++ legacy reference code (Qt/LS2-based webOS daemons). Read-only reference for porting behavior; do not modify unless explicitly asked.

The core design principle: webOS concepts (LaunchPoint, Just Type, application management) are reimplemented as native Android/Kotlin, not wrapped via JNI. The `LaunchPoint` interface is the central abstraction — it's runtime-agnostic, with `ANDROID_APP` and `WEBOS_APP` types so the UI never needs to know the app origin.

## Module Architecture (`launcher-android/`)

```
app          → Entry point, DI container, LauncherActivity, LauncherViewModel
core-model   → Domain models + business logic (ZERO Android UI deps)
data         → Room DB (11 migrations), DAOs, repositories, DataStore prefs
apps-android → Android PackageManager scanner → LaunchPoint producer
ui-home      → Home screen, gestures, dock, edit mode, deck widgets
ui-search    → Just Type search overlay
ui-appmenu   → Long-press context menu (bottom sheet)
```

**Dependency rule**: `core-model` has no UI deps. UI modules depend on `core-model` but **never on each other**. Only `:app` wires everything together. Respect this layering — don't add cross-UI-module imports.

## Critical Conventions

- **Kotlin only** — no Java files, no XML layouts, no `LiveData`, no RxJava
- **Jetpack Compose only** — all UI is Compose; use `collectAsStateWithLifecycle()` to observe state
- **StateFlow everywhere** — private `MutableStateFlow` (`_name`) with public `.asStateFlow()` (`name`); use `SharingStarted.WhileSubscribed(5000)` for derived flows
- **Manual DI** via `LauncherContainer` — no Hilt/Dagger; this is intentional. All singletons created in `LauncherContainer.create(context)`
- **Room migrations are mandatory** — the DB is at version 11 with 10 explicit migrations. Never bump the version without writing a `MIGRATION_X_Y` companion object in `LauncherDatabase.kt`
- **Notification data is never persisted** — privacy-first, in-memory only (`ConcurrentHashMap` in `NotificationIndexer`), 4-day retention
- **Gesture system** — one `pointerInput` block per surface, no `pointerInteropFilter`. State machine: `Idle → Pressed → LongPressArmed → {Dragging|Rotating|Swiping}`. Thresholds centralized in `GestureThresholds.kt`
- **LaunchPoint ID format**: `android:<package>/<activity>` (stable key across DB, sync, intents). Icon key: `android:<package>/<activity>@<versionCode>` (cache-busting). Do not change these formats.

## Build & Run

```bash
cd launcher-android
# Generate wrapper if missing (one-time): gradle wrapper --gradle-version 8.7
./gradlew clean build        # Full build
./gradlew installDebug       # Install to connected device/emulator
```

Requires: Android SDK API 34, JDK 17. Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00.

## Key Files to Read First

| Purpose | File |
|---------|------|
| Entry point & UI tree | `app/.../LauncherActivity.kt` |
| Central state holder | `app/.../LauncherViewModel.kt` |
| DI wiring | `app/.../LauncherContainer.kt` |
| Core abstraction | `core-model/.../model/LaunchPoint.kt` |
| Search engine | `core-model/.../justtype/engine/JustTypeEngine.kt` |
| DB + all migrations | `data/.../LauncherDatabase.kt` |
| Home screen + gestures | `ui-home/.../HomeScreen.kt`, `ui-home/.../HomeGestures.kt` |
| Gesture reference | `launcher-android/docs/Gestures.md` |

## Naming Conventions

- StateFlow backing: `_privateFlow` / `publicFlow`
- Entity classes: `*Entity.kt`; DAOs: `*Dao.kt`
- Dispatchers: `Dispatchers.IO` for disk/DB, `Dispatchers.Default` for computation
- Immutable updates: prefer `data class` + `copy()`

## What NOT to Do

- Do not add Hilt/Dagger — manual DI is a deliberate choice
- Do not introduce LiveData, RxJava, or XML layouts
- Do not skip Room migrations or use `fallbackToDestructiveMigration()`
- Do not persist notification content to disk
- Do not stack multiple `pointerInput` blocks on the same surface
- Do not add dependencies between UI modules (`ui-home`, `ui-search`, `ui-appmenu`)
- Do not modify C++ legacy code unless explicitly asked — it's reference material

## Legacy C++ Reference

The C++ repos (`luna-appmanager`, `luna-sysmgr`, etc.) use CMake + Qt + luna-service2 (LS2 IPC bus). Key mapping: `ApplicationManager.cpp` → split into `AndroidAppScanner` + `LaunchPointRepository` + `LauncherViewModel`; `UniversalSearchService.cpp` → `JustTypeEngine` with pluggable providers. See `plan-lunaSysmanAndroidPort.md` for the full porting strategy.
