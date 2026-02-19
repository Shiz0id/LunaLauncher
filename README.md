# Luna Launcher for Android

A modern Android home screen launcher inspired by webOS design principles — gesture-driven, absolute-positioned icons, universal search (Just Type), and glassmorphic theming.

[![Status](https://img.shields.io/badge/status-alpha-orange)]()
[![Min SDK](https://img.shields.io/badge/minSdk-30-blue)]()
[![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)]()
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.24-purple)]()
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06-green)]()

---

### Code of Conduct


This is an **AI-assisted project** — Before contributing or commenting:

**Expected behavior:**
- Be respectful, constructive, and assume good intent on the part of Contributers
- Provide actionable, specific feedback  in Issues
- Respect contributors' time and time zones
- AI contributions are welcomed and are expected to be attributed correctly.

**Unacceptable behavior:**
- Harassment, discrimination, or personal attacks
- Dismissing contributions solely because AI was involved
- Demanding immediate responses or free labor
- Submitting AI-generated content disclosure

**Enforcement:** Violations may result in comment removal, temporary bans, or permanent exclusion at maintainer discretion.



---

### Quick Links

| Documentation | Community |
|---------------|-----------|
| [Getting Started](#getting-started) | [Issues](../../issues) |
| [Architecture](#architecture) | [Discussions](../../discussions) |
| [Gestures Reference](docs/Gestures.md) | [Contributing](#contributing) |
| [LaunchPoint Abstraction](docs/LaunchPoint.md) | [CLAUDE.md](CLAUDE.md) — AI agent guide |
| [Project Roadmap](../plan-lunaSysmanAndroidPort.md) | |

---

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Development Guide](#development-guide)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgments](#acknowledgments)

---

## Features

### Home Screen

- **Absolute-Positioned Icons** — Free-form icon placement with pixel-perfect positioning using normalized coordinates (0.0–1.0 on both axes) for seamless scaling across devices
- **Icon Rotation** — Multi-touch rotation with 5° snap increments for visual customization
- **Edit Mode** — Long-press to enter edit mode; drag icons (1 finger) or rotate (2 fingers); all changes persist immediately

### Just Type (Universal Search)

- **Sectioned Results** — Results grouped by category: Apps, Actions, Notifications, Search Templates
- **Pluggable Providers** — Extensible architecture supporting:
  - Apps (native Android + webOS compatibility layer)
  - Notifications (search via NotificationListenerService)
  - Quick Actions ("New Email", "New Event", etc.)
  - Web Search Templates (configurable search engines)
- **Smart Ranking** — Token prefix matching with recency bonuses, pinned app priority, and intelligent substring fallback
- **Activation** — Swipe down from home screen or tap search bar

### Notification Integration

- **Live Search** — Search and interact with active notifications directly from Just Type
- **Quick Actions** — Reply, mark as read, dismiss, and execute custom actions without leaving the launcher
- **Privacy-First** — All notification data remains in-memory; never persisted to disk
- **Automatic Cleanup** — 4-day retention with automatic expiration

### Long-Press Context Menu

- **Application Menu** — Long-press any home icon to access app-specific actions
- **Bottom Sheet UI** — Smooth Material Design bottom sheet with app info and quick actions

### Theme System

- **Modern Design** — Native token-based theming with 40+ design tokens
- **Glass Materials:**
  - SMOKY_GLASS — dark, sophisticated glassmorphic UI
  - CRYSTAL_GLASS — clear, frosted luminous glass
- **16 Color Palettes** — From cool blues (AERO, SKY) to warm tones (FIRE, ORANGE) to neutrals (SMOKE, TAUPE)

### Widget Deck *(Early Stage)*

- **Customizable Widget Panels** — Add, arrange, and remove widget panels from the home screen
- **Flexible Layout** — Drag-and-drop widget positioning with automatic layout reflow
- **Widget Integration** — Support for standard Android App Widgets and custom Luna widgets
- **Persistent State** — Widget configuration persists across launcher sessions

> **Note:** Widget Deck is in active development. Core functionality works but UI/UX may change.

### Multi-Runtime Support

- **Native Android Apps** — Full integration with Android PackageManager
- **PWA Capture** — Browser-based PWA installation planned (Chromium)
- **webOS App Compatibility** — Infrastructure for running legacy webOS applications via WebView (long-term goal)

---

## webOS Compatibility Vision

Luna Launcher is designed as a **WebApp/PWA-first launcher** with long-term webOS app compatibility as a guiding principle.

**Why webOS?** The original Palm/HP webOS was ahead of its time — a web-native OS where apps were built with HTML, CSS, and JavaScript. Luna Launcher honors that vision by treating web apps as first-class citizens alongside native Android apps.

**The `LaunchPoint` abstraction** makes this possible. All launchable entities (Android apps, PWAs, webOS apps, contacts, actions) share one interface. The UI never knows the runtime origin — it just launches `LaunchPoint` objects. This means:

- Adding PWA support requires only a new scanner, not UI changes
- webOS apps can be launched via WebView with the same icon/gesture experience
- Future runtimes (Electron apps, Linux apps via containers) slot in cleanly

**Current status:**
| Runtime | Status |
|---------|--------|
| Native Android | [Y] Fully supported |
| PWA (Chromium) | [TODO] Planned |
| webOS apps | [Future] Infrastructure pre-prod plannning stage, WebView runtime TBD |

The C++ legacy codebases in this repo (`luna-sysmgr`, `luna-appmanager`) serve as reference for faithful behavior porting — not as code to run directly. All copyrights belong therin to the original authors and owners. No copyright infringement is intended and, to the best of my knowledge this represents a case of fair use. 

---

## Getting Started

### Prerequisites

- **JDK 17+**
- **Android SDK** API 34+ 
- **Gradle 8.7+** 
- **Device/emulator** API 30+

### Build & Run

```bash
# Clone and enter project
git clone https://github.com/Shiz0id01/luna-launcher-android
cd launcher-android

# Generate wrapper if missing (one-time)
gradle wrapper --gradle-version 8.7

# Build and install
./gradlew clean build
./gradlew installDebug

# Launch
adb shell am start -n com.lunasysman.launcher/.LauncherActivity
```

### Dependency Versions

All versions pinned in `gradle/libs.versions.toml`:

| Component | Version |
|-----------|---------|
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| Compose BOM | 2024.06.00 |
| Activity | 1.8.1 |
| Lifecycle | 2.7.0 |

To update: edit `gradle/libs.versions.toml` → run `./gradlew dependencies --scan` → update consuming `build.gradle.kts` if needed.

---

## Project Structure

```
launcher-android/
├── app/                 # Entry point, DI container, LauncherActivity/ViewModel
├── core-model/          # Domain logic (ZERO Android UI deps)
│   ├── model/           #   LaunchPoint, HomeIconPlacement, etc.
│   ├── justtype/        #   Search engine, providers, notification indexing
│   └── theme/           #   Design tokens
├── data/                # Room DB (10 migrations), DAOs, repositories, DataStore
├── apps-android/        # PackageManager → LaunchPoint scanner
├── ui-home/             # Home screen, gestures, All Apps, theme composables
├── ui-search/           # Legacy search overlay (to be removed)
├── ui-appmenu/          # Long-press bottom sheet
└── docs/                # Architecture specs (Gestures.md, LaunchPoint.md, etc.)
```

### Key Files

| Purpose | Location |
|---------|----------|
| App entry & UI tree | `app/.../LauncherActivity.kt` |
| Central state | `app/.../LauncherViewModel.kt` |
| DI container | `app/.../LauncherContainer.kt` |
| Core abstraction | `core-model/.../model/LaunchPoint.kt` |
| Search engine | `core-model/.../justtype/engine/JustTypeEngine.kt` |
| Database | `data/.../db/LauncherDatabase.kt` |
| Home screen | `ui-home/.../HomeScreen.kt` |
| Gesture handling | `ui-home/.../HomeGestures.kt` |

---

## Architecture

### Layer Diagram

```
┌──────────────────────────────────┐
│   UI Layer (Jetpack Compose)     │
│  (ui-home, ui-search, ui-appmenu)│
└──────────────┬───────────────────┘
               │
┌──────────────▼───────────────────┐
│   Persistence & Repository       │
│        (data, apps-android)      │
└──────────────┬───────────────────┘
               │
┌──────────────▼───────────────────┐
│  Core Model & Business Logic     │
│         (core-model)             │
└──────────────────────────────────┘
```

### Key Principles

- **Unidirectional Data Flow** — ViewModels expose immutable `StateFlow`; UI observes and dispatches events
- **No Cross-UI Dependencies** — UI modules never import each other; only `:app` coordinates
- **Manual DI** — `LauncherContainer` holds all singletons; no Hilt/Dagger by design
- **Room Migrations Mandatory** — Never use `fallbackToDestructiveMigration()`

<details>
<summary><strong>State Management Pattern</strong></summary>

```kotlin
// ViewModel pattern
private val _searchQuery = MutableStateFlow("")
val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

// Derived flows
val searchResults = combine(searchQuery, launchPoints) { query, apps ->
    // derive results
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

Compose observes via:
```kotlin
val query by vm.searchQuery.collectAsStateWithLifecycle()
```

</details>

<details>
<summary><strong>Gesture State Machine</strong></summary>

```
Idle
  ├─ single touch → Pressed
  │   ├─ move (> slop) → Swiping → vertical opens search/apps
  │   └─ hold (> timeout) → LongPressArmed → Menu
  └─ two-finger (edit mode) → Rotating → icon rotation
```

See [docs/Gestures.md](docs/Gestures.md) for full specification.

</details>

<details>
<summary><strong>Database Entities</strong></summary>

- `HomeIconEntity` — absolute icon placements with rotation
- `LaunchPointEntity` — indexed app registry
- `JustTypeProviderEntity` — search provider preferences
- Notifications — **in-memory only** (privacy)

</details>

---

## Development Guide

### Coding Standards

| Rule | Details |
|------|---------|
| **Kotlin only** | No Java, no XML layouts (except manifest) |
| **Compose only** | No `View` classes |
| **StateFlow** | Private `_name` → public `.asStateFlow()` |
| **Manual DI** | All singletons in `LauncherContainer.create(context)` |
| **Migrations** | Every schema change needs `MIGRATION_X_Y` |
| **Single pointerInput** | One gesture handler per surface |

### Module Rules

**MUST:**
- UI modules depend on `core-model`
- `core-model` has zero Android UI deps
- `:app` coordinates everything

**MUST NOT:**
- UI modules depend on each other
- `core-model` imports `androidx.compose.*`
- Skip Room migrations

### LaunchPoint Abstraction

All launchable entities share one interface — UI never knows the runtime origin:

```kotlin
data class LaunchPoint(
    val id: String,              // "android:<pkg>/<activity>" or "webos:<id>"
    val title: String,
    val type: LaunchPointType,   // ANDROID_APP, WEBOS_APP, CONTACT, ACTION_ITEM
    val appPackage: String?,
    val activityName: String?,
    val iconUrl: String?,        // Key: android:<pkg>/<activity>@<versionCode>
    val metadata: Map<String, String>,
)
```

This enables future PWA and webOS app support without UI changes. See [docs/LaunchPoint.md](docs/LaunchPoint.md).

### Home Icon Coordinates

Normalized floating-point for device-independent layout:

```kotlin
data class HomeIconPlacement(
    val xNorm: Float,          // 0.0–1.0 horizontal
    val yNorm: Float,          // 0.0–1.0 vertical
    val rotationDeg: Float,    // 0–360°, snapped to 5°
    val zIndex: Int,
    val updatedAtEpochMs: Long,
)
```

### Theme System

Two-axis theming: **glass style** × **color palette**.

| Glass Style | Character |
|-------------|-----------|
| SMOKY_GLASS | Dark, sophisticated glassmorphism |
| CRYSTAL_GLASS | Clear, frosted Glass in a familiar 7 look |

16 color palettes: AERO, SKY, TWILIGHT, SEA, SMOKE, PRO_BLACK, LIME, LEAF, FIRE, ORANGE, RUBY, FUCHSIA, BLUSH, VIOLET, LAVENDER, TAUPE, CHOCOLATE.

<details>
<summary><strong>Glass Layer Stack</strong></summary>

1. Base Tint — semi-transparent color
2. Noise Texture — procedural grain (1024×1024 bitmap, stable seed)
3. Specular Edge — top-edge highlight
4. Ambient Glow — radial fill light
5. Luminance Gradient — optional depth gradient
6. Inner Rim — 1px glass-rim definition
7. Stroke/Border — surface edges

</details>

<details>
<summary><strong>Theme Usage</strong></summary>

```kotlin
@Composable
fun LunaLauncherTheme(
    style: LauncherThemeStyle,     // SMOKY_GLASS or CRYSTAL_GLASS
    colorTheme: LauncherColorTheme,
    homeTintStrength: Float,       // 0..1
    content: @Composable () -> Unit,
)

// Access tokens anywhere:
val bgColor = LauncherTheme.colors.dockBackground
```

</details>

### Just Type Search

Pluggable provider architecture with multi-factor ranking:

| Factor | Points |
|--------|--------|
| Token prefix match | 10 |
| Pinned by user | 20 |
| Recently launched | 5 |
| Substring fallback | 3 |

<details>
<summary><strong>Provider Interface</strong></summary>

```kotlin
interface JustTypeProvider {
    suspend fun search(query: String): List<JustTypeItemUi>
}

sealed class JustTypeItemUi {
    data class LaunchPointItem(...) : JustTypeItemUi()
    data class NotificationItem(...) : JustTypeItemUi()
    data class ActionItem(...) : JustTypeItemUi()
    data class SearchTemplateItem(...) : JustTypeItemUi()
}
```

</details>

### Notification System

Privacy-first: all data in-memory only, 4-day auto-expiration.

```
Android notification → LunaNotificationListenerService
    → NotificationIndexer (in-memory StateFlow)
    → NotificationsProvider → Just Type results
    → User action → NotificationActionExecutor
```

### Testing

```bash
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew :core-model:test       # Single module
```

---

## Contributing

### Before You Start

1. Read [CLAUDE.md](CLAUDE.md) and [docs/](docs/)
2. Understand module boundaries
3. Check [project roadmap](../plan-lunaSysmanAndroidPort.md)

### Process

```bash
git checkout -b feature/my-feature
# Make changes following coding standards
./gradlew test
./gradlew installDebug
# Submit PR with clear description
```

---

## License

**MIT License** — See [LICENSE](LICENSE)

---

## Acknowledgments

### Inspiration

Luna Launcher is inspired by **webOS Luna System Manager**, a pioneering mobile operating system architecture developed by Palm/HP. Special thanks to the Palm and HP WebOS teams.

### Community & Contributors

Luna Launcher benefits from:

- **webOS open-source community** — Reference implementations and design principles
- **Android ecosystem** — Best practices and library innovations
- **Contributors** — Bug reports, feature requests, and pull requests (see [CONTRIBUTORS.md](CONTRIBUTORS.md))

### Legacy Reference

The C++ legacy codebases (`luna-sysmgr`, `luna-appmanager`, `luna-universalsearchmgr`) are included as **read-only reference**. They demonstrate the original webOS architecture and are used to ensure faithful porting of behavior.

---

**Built with ❤️ by the Luna Launcher team**

