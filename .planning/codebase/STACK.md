# Technology Stack

**Analysis Date:** 2026-02-20

## Languages

**Primary:**
- Kotlin 2.0.21 - 100% of source code (no Java)
- XML - Android manifest and resource files

**Secondary:**
- Gradle Kotlin DSL (build configuration)

## Runtime

**Environment:**
- Android 11+ (minSdk 30) through Android 15 (targetSdk 35)
- Android Runtime (ART)

**Build System:**
- Gradle 8.7
- Android Gradle Plugin (AGP) 8.13.2

**Package Manager:**
- Gradle with Version Catalog (gradle/libs.versions.toml)
- Lock file: Generated lockfile in `.gradle/` directory

## Frameworks

**Core UI:**
- Jetpack Compose (2024.12.01 BOM) - All UI, no XML layouts
- Compose Material 3 - Design system and theme
- Material Icons Extended - Icon library

**State Management & Lifecycle:**
- Jetpack Lifecycle (2.8.7) - ViewModel, Compose integration
- Kotlin Coroutines (1.9.0) - Async/reactive programming
- StateFlow - All UI state management (zero LiveData)

**Database & Persistence:**
- Room 2.6.1 - SQLite ORM with 11 migrations
- Android DataStore (1.1.1) - Preferences and app settings

**System Integration:**
- Android Core KTX (1.15.0) - Android API extensions
- Activity Compose (1.9.3) - Activity/Compose integration

## Key Dependencies

**Critical:**
- `androidx.compose:compose-bom:2024.12.01` - Unified Compose versioning
- `androidx.room:room-runtime:2.6.1` - Persistent storage
- `androidx.datastore:datastore-preferences:1.1.1` - User preferences
- `androidx.lifecycle:lifecycle-runtime-compose:2.8.7` - Lifecycle awareness in Compose
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` - Coroutine dispatcher

**Build/Compilation:**
- `org.jetbrains.kotlin.plugin.compose:2.0.21` - Compose compiler plugin
- `androidx.room:room-compiler:2.6.1` - Room code generation
- Kotlin KAPT (Kotlin Annotation Processing Tool) - Annotation processing

## Configuration

**Environment:**
- No external API keys or cloud services required
- All configuration via DataStore preferences and Room database
- Gradle properties in `gradle.properties`: parallel builds, caching enabled

**Build Features:**
- Compose enabled (buildFeatures.compose = true)
- BuildConfig generation enabled
- Non-transitive R class (faster compilation)

**Gradle Settings:**
- JVM args: -Xmx2g (2GB heap)
- Parallel builds: enabled
- Build cache: enabled
- Kotlin code style: official

## Platform Requirements

**Development:**
- JDK 17+
- Android SDK API 35 (latest)
- Gradle 8.7 wrapper
- Kotlin 2.0.21

**Production (Runtime):**
- Android device/emulator API 30+ (Android 11+)
- ~15MB APK size (estimated, TBD)
- No network connectivity required for core features
- Permission: BIND_NOTIFICATION_LISTENER_SERVICE for notification integration

## Module Structure & Dependencies

```
app/
├── Dependencies: core-model, data, apps-android, ui-home, ui-search, ui-appmenu
├── Role: Main wiring, LauncherActivity, LauncherApplication, DI container

core-model/
├── Dependencies: androidx.core:core-ktx only
├── Role: Domain models, business logic, search engine, notification model

data/
├── Dependencies: core-model
├── Role: Room database, DAOs, repositories, DataStore preferences

apps-android/
├── Dependencies: core-model
├── Role: Android PackageManager scanner, app enumeration

ui-home/
├── Dependencies: core-model
├── Role: Home screen UI, gestures, search overlay, dock

ui-search/
├── Dependencies: core-model
├── Role: Unused SearchOverlay (legacy)

ui-appmenu/
├── Dependencies: core-model
├── Role: Long-press context menu
```

---

*Stack analysis: 2026-02-20*
