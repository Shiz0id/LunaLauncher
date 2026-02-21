# External Integrations

**Analysis Date:** 2026-02-20

## APIs & External Services

**Android Framework APIs:**
- PackageManager - App discovery and icon loading
  - SDK/Client: Android Framework
  - Used in: `com.lunasysman.launcher.apps.android.AndroidAppScanner`
  - Methods: queryIntentActivities(), resolveActivity(), getPackageInfo()

**No third-party API integrations** - Project is offline-first

## Data Storage

**Databases:**
- SQLite via Room (2.6.1)
  - Location: `launcher.db` in app's private database directory
  - Client: Room ORM
  - Managed by: `com.lunasysman.launcher.data.LauncherDatabase`
  - Version: 11 (with 11 migrations: MIGRATION_1_2 through MIGRATION_10_11)

**Database Entities:**
- `LaunchPointEntity` - App and action launch points
- `DockEntryEntity` - Dock positioning (row, launchPointId)
- `HomeSlotEntity` - Legacy grid-based home placement
- `HomeIconEntity` - Absolute-positioned home icons (xNorm, yNorm, rotation)
- `JustTypeProviderEntity` - Search provider configuration
- `DeckCardEntity` - Widget deck cards
- `DeckWidgetEntity` - Individual widgets in deck cards

**File Storage:**
- Local filesystem only - icon cache in app cache directory
- `IconRepository` caches app icons in-memory and on disk
- Icon loading via `AndroidAppScanner.loadIconBitmapOrNull()`

**Caching:**
- In-memory: Icon batches (36-icon prefetch strategy)
- In-memory: Notification index (4-day retention, then flushed)
- Disk: Icon bitmaps in app's cache directory
- No persistent caching of notifications (privacy-first design)

## Authentication & Identity

**Auth Provider:**
- None for external services
- Android system permissions handled natively
- First-boot notification permission dialog in `NotificationPermissionDialog.kt`

**Permissions Granted at Install:**
- `android.permission.READ_CONTACTS` - Contact integration (future)
- `android.permission.READ_WALLPAPER_INTERNAL` - Wallpaper access
- `android.permission.READ_EXTERNAL_STORAGE` - Files API (maxSdkVersion 32)
- `android.permission.READ_MEDIA_IMAGES` - Image access (Android 13+)
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` - Notifications (user-enabled)

## Monitoring & Observability

**Error Tracking:**
- None (no remote error reporting)
- Local logging via Android Log (TAG: "LunaLauncher", "GestureDebug")

**Logs:**
- Logcat output via `android.util.Log`
- Debug tag: `GestureDebug` for gesture system tracing
- Enabled via: `GestureDebug.enabled = true` in `GestureThresholds.kt`

## CI/CD & Deployment

**Hosting:**
- No remote hosting (standalone Android app)
- Distribution: APK/AAB via Google Play or sideload

**CI Pipeline:**
- None detected (GitHub Actions config may exist in `.github/` but no CI workflow files in src)

## Environment Configuration

**Required env vars:**
- None - fully self-contained

**Configuration files:**
- `gradle.properties` - Build settings (JVM memory, parallel builds, caching)
- `gradle/libs.versions.toml` - Dependency version catalog
- `android/manifest/AndroidManifest.xml` - Permissions, intent filters, services

**Secrets location:**
- No secrets in codebase
- No `.env` files required

## Notifications & System Integration

**Android Notifications (Inbound):**
- `LunaNotificationListenerService` extends `NotificationListenerService`
- Location: `com.lunasysman.launcher.core.justtype.notifications.LunaNotificationListenerService`
- Requires user to enable in Settings > Apps > Special app access > Notification access
- Feeds `NotificationIndexer` for search results

**Notification Actions:**
- `NotificationActionExecutor` - Execute notification actions (reply, dismiss, mark-as-read)
- Via: `com.lunasysman.launcher.core.justtype.notifications.NotificationActionExecutor`
- Uses Android's `RemoteInput` and `PendingIntent` capability tokens
- Actions are ephemeral (disappear when notification dismissed)

**Package Change Broadcasts:**
- `PackageChangeReceiver` - Listens for app install/uninstall/update
- Intent filters:
  - `android.intent.action.PACKAGE_ADDED`
  - `android.intent.action.PACKAGE_REMOVED`
  - `android.intent.action.PACKAGE_CHANGED`
  - `android.intent.action.PACKAGE_REPLACED`
- Updates `LaunchPointRepository` with new/removed apps

## System Services

**Widget Host:**
- `DeckWidgetHost` - Manages third-party app widgets in the deck
- Location: `com.lunasysman.launcher.deck.DeckWidgetHost`
- Hosts widgets via Android's `AppWidgetManager` and `AppWidgetHost` APIs

**App Widget Picker:**
- Activity: `WidgetsPickerActivity`
- Allows users to select widgets to add to the widget deck

## Webhooks & Callbacks

**Incoming:**
- Package change broadcasts from Android system
- Notification events from `NotificationListenerService`
- Widget update callbacks from `AppWidgetManager`

**Outgoing:**
- App launches via Intent (standard Android launching)
- Notification actions via PendingIntent
- Widget callbacks to third-party apps

## Search Providers

**Built-in Providers (Pluggable):**
- `AppsProvider` - Searches installed apps
- `NotificationsProvider` - Searches active notifications via `NotificationIndexer`
- `ActionsProvider` - Quick action commands
- `SearchTemplatesProvider` - Web search templates
- Location: `com.lunasysman.launcher.core.justtype.providers.*`
- Config: `com.lunasysman.launcher.core.justtype.registry.JustTypeProviderConfig`

**Provider Registry:**
- Database: `JustTypeProviderEntity` (enabled, orderIndex, version)
- Persistence: Room via `JustTypeProviderDao`
- Initialization: `JustTypeRegistryInitializer.kt`

## Data Flow Summary

```
Android PackageManager
  ↓
AndroidAppScanner → LaunchPoints
  ↓
LaunchPointRepository → Room Database
  ↓
LauncherViewModel → UI State (StateFlow)

NotificationListenerService
  ↓
NotificationIndexer (in-memory, 4-day TTL)
  ↓
NotificationsProvider → JustTypeEngine → Search Results

PackageChangeReceiver
  ↓
PackageChangeHandler
  ↓
LaunchPointRepository (sync)
```

---

*Integration audit: 2026-02-20*
