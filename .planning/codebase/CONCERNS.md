# Codebase Concerns

**Analysis Date:** 2026-02-20

## Tech Debt

**Wide Exception Catches:**
- Issue: Multiple locations catch generic `Exception` instead of specific exception types, masking root causes and making debugging harder
- Files: `D:\Luna Launcher\launcher-android\apps-android\src\main\kotlin\com\lunasysman\launcher\apps\android\AndroidAppScanner.kt` (lines 34, 75, 83), `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\IconRepository.kt` (line 67), `D:\Luna Launcher\launcher-android\core-model\src\main\kotlin\com\lunasysman\launcher\core\justtype\notifications\LunaNotificationListenerService.kt` (lines 59, 139, 185), `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\WidgetsPickerActivity.kt` (lines 227, 275, 772, 812, 837), `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\deck\DeckWidgetHost.kt` (multiple locations)
- Impact: Errors silently logged with generic handlers may hide PackageManager crashes, widget binding failures, or filesystem issues that could be addressed with specific recovery
- Fix approach: Replace blanket `Exception` catches with specific exception types (`PackageManager.NameNotFoundException`, `SecurityException`, `IllegalArgumentException`, `PendingIntent.CanceledException`). Add fallback for unexpected cases

**Unused UI Module (`ui-search`):**
- Issue: Module contains `SearchOverlay.kt` (692 lines) but is not imported or used anywhere in active codebase
- Files: `D:\Luna Launcher\launcher-android\ui-search\src\main\kotlin\com\lunasysman\launcher\ui\search\SearchOverlay.kt`
- Impact: Dead code increases maintenance burden; search functionality is integrated into `ui-home/JustType.kt` instead (801 lines, which is also large and tightly coupled)
- Fix approach: Remove `ui-search` module from build and git. Extract search composition into reusable components from `JustType.kt` for future modularity. Consider splitting `JustType.kt` into smaller focused files (SearchBar, ResultsSection, ProviderConfig UI)

**Missing TODO Documentation:**
- Issue: Reference in CLAUDE.md to `docs/HOME_ABSOLUTE_LAYOUT_TODO.md` but file does not exist
- Files: `D:\Luna Launcher\launcher-android\CLAUDE.md` (line 195)
- Impact: Developers looking for absolute layout future work have no guidance
- Fix approach: Either create the referenced document with concrete layout improvement plans or remove the reference and document in a different location (e.g., GitHub Issues)

**Suppressed Deprecations:**
- Issue: Multiple uses of `@Suppress("DEPRECATION")` for `PackageManager.queryIntentActivities()` and `PackageManager.getPackageInfo()`
- Files: `D:\Luna Launcher\launcher-android\apps-android\src\main\kotlin\com\lunasysman\launcher\apps\android\AndroidAppScanner.kt` (lines 22, 32, 62)
- Impact: Android 14+ (API 34) deprecations signal future removal; app will break when methods are removed
- Fix approach: Migrate to `PackageManager.queryIntentActivitiesCompat()` and use `getLongVersionCode()` helper when available; maintain compatibility shim for older APIs

## Known Bugs

**Widget Bitmap Cache Memory Pressure:**
- Symptoms: On devices with low memory, widget previews may not load; bitmap cache doesn't aggressively clear
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\deck\DeckBitmapCache.kt` (lines 44)
- Trigger: Add 10+ widgets to deck and scroll rapidly through widget picker
- Workaround: Close and reopen widget picker; memory clears when leaving activity

**Widget Host Exception Swallowing:**
- Symptoms: Widget binding failures silently fail without user feedback or logging context
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\deck\DeckWidgetHost.kt` (lines 37, 50, 62, 74, 94, 97, 127, 154, 164, 166)
- Trigger: Install widget from restricted package (Google Workspace); try to bind
- Workaround: None; widget silently doesn't appear; check logcat for brief exception

## Security Considerations

**Notification Data Persistence (No Risk - Design Choice):**
- Current approach: Notification data never persists to disk; stored only in-memory via `ConcurrentHashMap`
- Design rationale: Privacy-first; notifications cleaned up after 4 days automatically
- Current mitigation: `NotificationIndexer` uses in-memory-only storage; `FIRST_BOOT_COMPLETE.md` documents this
- Note: This is a deliberate security posture, not a vulnerability. Do not persist notification data to disk

**Package Manager Query Intent Resolution:**
- Risk: Malformed or malicious app packages could cause `resolveIntentOrThrow()` to throw but exceptions are caught broadly
- Files: `D:\Luna Launcher\launcher-android\apps-android\src\main\kotlin\com\lunasysman\launcher\apps\android\AndroidAppScanner.kt` (lines 59-68)
- Current mitigation: Broad try-catch in `loadIconBitmapOrNull()` prevents crashes
- Recommendations: Log specific exception type for malformed package names; consider rate-limiting retries for repeatedly failing packages

**Widget Configuration Flows:**
- Risk: Widget configure activities may request dangerous permissions or redirect to malicious endpoints
- Current mitigation: Standard Android AppWidget API; system permission prompts shown to user
- Recommendations: Log all widget bindings with package name and provider; consider whitelist for restricted packages

## Performance Bottlenecks

**Large Compose Files:**
- Problem: Single mega-files without clear layering cause recomposition storms and poor IDE performance
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\LauncherActivity.kt` (797 lines), `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin\com\lunasysman\launcher\ui\home\HomeScreen.kt` (769 lines), `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin\com\lunasysman\launcher\ui\home\JustType.kt` (801 lines), `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\WidgetsPickerActivity.kt` (840 lines)
- Cause: Each file contains multiple Composables, state holders, and gesture handlers without proper extraction
- Improvement path: Break into focused files: `LauncherActivity.kt` → `LauncherActivity.kt` + `LauncherRoot.kt` + `LauncherPermissions.kt`; `HomeScreen.kt` → `HomeScreen.kt` + `HomeGestureLayer.kt` + `HomeIconGrid.kt`; `JustType.kt` → `JustTypePanel.kt` + `JustTypeSearchBar.kt` + `JustTypeResultsList.kt`. Target: max 300 lines per file.

**Icon Prefetch Serialization:**
- Problem: `IconRepository.prefetch()` loads icons one-at-a-time with 8ms delay between requests, serializing disk I/O
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\IconRepository.kt` (lines 62-72)
- Cause: Conservative batching to prevent I/O storms; however, 36-icon batches mentioned in CLAUDE.md are not implemented
- Improvement path: Batch prefetch into 4-6 concurrent Dispatchers.IO tasks instead of sequential; measure system I/O pressure

**Package Scanner Full Re-scan on App Install:**
- Problem: Every package change (`PackageChangeHandler.signalChanged()`) triggers full `scanLaunchableActivities()` rescan instead of delta
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\PackageChangeHandler.kt` (line 23), `D:\Luna Launcher\launcher-android\apps-android\src\main\kotlin\com\lunasysman\launcher\apps\android\AndroidAppScanner.kt` (line 20)
- Cause: Simple/safe implementation; Android provides package action (INSTALL, UNINSTALL, UPDATE) but scanner ignores it
- Improvement path: Pass intent action to scanner; return only changed package delta instead of full list; use `PackageChangeReceiver` action to construct delta

**Notification Indexer Linear Scan:**
- Problem: `NotificationIndexer.markDismissedByPackage()` iterates all notifications in-memory to find matches
- Files: `D:\Luna Launcher\launcher-android\core-model\src\main\kotlin\com\lunasysman\launcher\core\justtype\notifications\NotificationIndexer.kt` (lines 94-100+)
- Cause: O(n) scan; fine for <5000 notifications but degrades with heavy notification volume
- Improvement path: Maintain secondary index `Map<String, List<String>>` mapping packageName → notificationKeys for O(1) lookups

## Fragile Areas

**Manual Dependency Injection Container:**
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\LauncherContainer.kt`
- Why fragile: Monolithic 90-line container creates all dependencies at once; any factory failure crashes app; no lazy initialization or fallback. Tight coupling to specific repository signatures
- Safe modification: When adding new dependencies, create them inside try-catch blocks (see `justTypeRegistry.initialize()` pattern at line 58-63); consider extracting factories for large sub-systems (Deck, JustType registry); test cold-start paths on low-memory devices
- Test coverage: No unit tests for container creation; integration test needed for full stack initialization

**Widget Host Binding State Machine:**
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\deck\DeckWidgetHost.kt`
- Why fragile: Pending state tracked with nullable variables (`pendingAppWidgetId`, `pendingProviderInfo` in `WidgetsPickerActivity`); no formal state machine; multiple activity result handlers must coordinate
- Safe modification: Before changing widget result flows, audit all catch blocks for exception handling; test cancellation paths (user dismisses configure dialog); verify cleanup in `onDestroy()` isn't needed (check for memory leaks)
- Test coverage: Manual testing only; no automated tests for widget permission flow or configure timeout

**HomeScreen Gesture State Machine:**
- Files: `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin\com\lunasysman\launcher\ui\home\GestureState.kt` (175 lines), `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin\com\lunasysman\launcher\ui\home\HomeGestures.kt` (135 lines)
- Why fragile: Complex state transitions (Idle → Pressed → LongPressArmed → Dragging/Rotating/Swiping); thresholds centralized in `GestureThresholds.kt` but state logic spread across multiple files; gesture debug mode helps but isn't part of test suite
- Safe modification: All gesture changes must be tested with `GestureDebug.enabled = true` and validated against the state diagram in `docs/Gestures.md`; any threshold change may cause edge cases (e.g., swipe vs drag ambiguity)
- Test coverage: No automated gesture tests; rely on manual device testing

**Room Database Migrations:**
- Files: `D:\Luna Launcher\launcher-android\data\src\main\kotlin\com\lunasysman\launcher\data\LauncherDatabase.kt` (303 lines, 10 migrations listed)
- Why fragile: Schema evolved from grid → absolute positioning → deck widgets; each migration is a manual SQL rewrite; no rollback path; old migration code still in binary
- Safe modification: Database version is 11; adding a new entity requires MIGRATION_11_12 before bumping version. Always test migration on real data (backup test DB before upgrade). Never skip a version or drop old migrations
- Test coverage: No automated migration tests; manual test on historical database backups

## Scaling Limits

**Icon Cache Disk Limits:**
- Current capacity: 64 MB max disk, 1200 max files, LRU memory cache up to 64 MB
- Limit: Devices with 10,000+ apps will evict icons frequently; disk I/O on every app list scroll
- Scaling path: Implement smarter eviction based on app launch frequency, not just LRU; use `Dispatchers.IO` batch operations; consider WebP compression (20-30% savings); monitor memory on low-end devices

**Notification Retention:**
- Current capacity: In-memory only, 4-day retention, hard limit of ~5000 notifications before eviction pressure
- Limit: Users with high notification volume (>50/day for 4 days = 200 notifications) may hit memory pressure; linear scan on dismiss-by-package scales poorly
- Scaling path: Add secondary indexing for packageName lookups; consider optional disk retention with encrypted local storage; implement periodic cleanup sweep

**Home Icon Placement Database:**
- Current capacity: Absolute positioning (normalized 0.0-1.0 coordinates) supports unlimited placement per screen; HomeIconEntity persisted to Room
- Limit: Home screen with >500 icons becomes difficult to navigate and render; gesture system may stall with high pointer event volume
- Scaling path: Implement home screen pagination or scrolling; consider spatial indexing for gesture hit-test optimization; test with realistic high-density home screens

**Widget Deck Bitmap Caching:**
- Current capacity: `DeckBitmapCache` is unbounded; no max size or TTL
- Limit: 20+ widgets will consume 100+ MB of memory (100 KB preview each); device may OOM
- Scaling path: Implement bounded LRU cache with max 50 MB; implement TTL-based eviction (clear after 30 min); prefetch only visible widget previews during scroll

## Dependencies at Risk

**Android Deprecation Warnings (API 34 Target):**
- Risk: `PackageManager.queryIntentActivities()`, `PackageManager.getPackageInfo()` both deprecated in API 34; will be removed in future Android versions
- Current impact: App compiles with `@Suppress` annotations; works on Android 15 but future API levels may fail
- Files: `D:\Luna Launcher\launcher-android\apps-android\src\main\kotlin\com\lunasysman\launcher\apps\android\AndroidAppScanner.kt`
- Migration plan: Migrate to `PackageManager.queryIntentActivitiesCompat()` (AndroidX compatibility library); use reflection or version checks for `getLongVersionCode()` fallback

**Jetpack Compose Version Lock:**
- Risk: Project pins Compose BOM 2024.12.01; future versions may deprecate Layout APIs (`BoxWithConstraints`, implicit padding handling)
- Current impact: No immediate risk; but skipping major Compose updates increases technical debt
- Migration plan: Schedule quarterly Compose updates; test with new Material 3 tokens; review deprecation warnings in each release notes

**Kotlin 2.0.21:**
- Risk: Older Kotlin version; 2.1+ may introduce compiler warnings or future feature requirements
- Current impact: No immediate risk; project should stay within 2.0-2.1 range
- Migration plan: Upgrade to Kotlin 2.1.x in next major cycle; test incremental compilation performance

## Missing Critical Features

**Widget Deck UI Incomplete:**
- Problem: Widget Deck DB schema added (entities in LauncherDatabase.kt); database persists state; but UI for adding/removing widgets is only partially complete
- Blocks: Users cannot configure custom widget panels; no visual feedback on widget add/remove flow
- Severity: Medium; feature flagged as "Early Stage" in docs but database is production-ready

**Full Settings Screen Not Implemented:**
- Problem: JustTypeSettingsActivity exists (386 lines) but launcher has no general settings screen for theme, gesture thresholds, notification retention, etc.
- Blocks: Advanced users cannot customize behavior
- Severity: Low; basic launcher usable without settings

**PWA (Progressive Web App) Support Planned but Not Started:**
- Problem: Docs reference PWA support as "TODO" in roadmap; LaunchPoint abstraction supports it but no PWA scanner implemented
- Blocks: Web-based apps cannot be added to launcher
- Severity: Low; roadmap item, not critical for MVP

## Test Coverage Gaps

**No Unit Tests for Core Gesture State Machine:**
- What's not tested: `GestureState.kt` and state transitions (Idle → Pressed → LongPressArmed → Dragging); threshold validation; edge cases like rapid multi-touch
- Files: `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin\com\lunasysman\launcher\ui\home\GestureState.kt`, `D:\Luna Launcher\launcher-android\ui-home\src\main\kotlin\com\lunasysman\launcher\ui\home\HomeGestures.kt`
- Risk: Gesture system is core to UX; bugs cause swipe/drag misdetection and poor user experience
- Priority: **High** — Add state machine unit tests with MotionEvent sequences

**No Database Migration Tests:**
- What's not tested: Migration path from old schema versions (v1-v10 to v11); data integrity during upgrades
- Files: `D:\Luna Launcher\launcher-android\data\src\main\kotlin\com\lunasysman\launcher\data\LauncherDatabase.kt`
- Risk: Users upgrading will silently lose data if a migration fails; no validation that migrated data is correct
- Priority: **High** — Add Room migration unit tests with historical database snapshots

**No Widget Binding Integration Tests:**
- What's not tested: Widget permission flow (BIND_APPWIDGET); widget configure activity result handling; cancellation paths
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\WidgetsPickerActivity.kt`, `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\deck\DeckWidgetHost.kt`
- Risk: Widget system fails silently; users see blank widgets with no feedback
- Priority: **Medium** — Add espresso tests for widget picker flow and permission dialogs

**No NotificationIndexer Tests:**
- What's not tested: In-memory index behavior under load; package-based filtering; retention expiration
- Files: `D:\Luna Launcher\launcher-android\core-model\src\main\kotlin\com\lunasysman\launcher\core\justtype\notifications\NotificationIndexer.kt`
- Risk: Notification search may return stale or incorrect results; memory leaks if cleanup fails
- Priority: **Medium** — Add unit tests with mock NotificationActionSurfaces and time manipulation

**No Icon Repository Cache Eviction Tests:**
- What's not tested: Disk eviction when cache exceeds 64 MB or 1200 files; concurrent access; cache coherency
- Files: `D:\Luna Launcher\launcher-android\app\src\main\kotlin\com\lunasysman\launcher\IconRepository.kt`
- Risk: Disk cache may grow unbounded on repeated icon loads; LRU cache may evict in-use icons
- Priority: **Medium** — Add unit tests for `evictDiskIfNeeded()` with controlled file/size scenarios

---

*Concerns audit: 2026-02-20*
