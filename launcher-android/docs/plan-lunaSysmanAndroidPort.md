# Plan: Android Port of Luna System Manager as Launcher Service

**Overview**: Port Luna System Manager to Android as a launcher service (home screen) while maintaining WebOS app support through a compatibility layer. This requires replacing platform-specific layers (Luna Service Bus, Nyx hardware abstraction, Qt) while preserving core application management logic. The effort spans 5 key dimensions: service architecture, IPC/communication, hardware abstraction, web app runtime, and system integration.

---

## Component-Specific Porting Analysis

### luna-webappmanager
**Current Role**: Manages web application instances; spawns separate processes for each web app; handles WebKit2/QtWebEngine rendering and lifecycle.

**Android Port Strategy**:
- **Approach**: Merge functionality into main launcher service (no separate webappmanager process needed)
- **WebView per App**: Each WebOS app gets its own WebView instance managed by the launcher service
- **Process Isolation**: Android naturally isolates apps; can run web apps in separate `Service` processes if needed, but WebView in main launcher simplifies IPC
- **JavaScript Bridge**: Critical component—implement `WebChromeClient` and `WebViewClient` subclasses; create JavaScript interface for webOS API access
- **Lifecycle Management**: Map webOS app lifecycle (create, foreground, background, close) to Android WebView lifecycle
- **Critical Files to Adapt**:
  - `WebAppManager.cpp` → Merge into `WebAppRuntime.java` (Android service/manager)
  - `WebApplication.cpp` → `WebAppInstance.java` (WebView wrapper)
  - `WebAppMgrProxy.cpp` → Binder client code (already handled in IPC layer)

**Key Challenges**:
- WebView performance vs. native apps
- Cookie/storage isolation per web app
- Handling multiple concurrent web app instances with shared memory constraints
- Gesture and input event mapping from Android to web app expectations

**Effort**: **XXL** (tied to runtime strategy choice)

---

### luna-appmanager
**Current Role**: Application discovery, installation/removal, launch point management, MIME type routing, app capability management.

**Android Port Strategy**:
- **Minimal Changes**: Port existing app discovery and management logic with minimal modifications; treat Android package system as just another app storage backend
- **Preserve Core Logic**: Keep ApplicationManager and LaunchPointManager state machines, enumeration algorithms, and capability tracking largely intact
- **App Storage Abstraction**: Add thin abstraction layer for app filesystem paths—webOS apps stored in dedicated Android app directory (e.g., `/data/data/com.example.webosapps/apps/`), mirroring webOS `/media/developer/apps/` structure
- **No PackageManager Dependency**: Don't integrate with Android PackageManager; treat webOS apps as self-contained bundles independent of Android's app lifecycle
- **App Metadata**: Continue parsing `appinfo.json` directly from webOS app bundles (unchanged from webOS port)
- **Launch Point Mapping**: Keep existing launch point registry; pass to launcher via Binder service interface as-is
- **Installation/Removal**: Implement simple file-based install/uninstall (copy/delete app bundle directory); no APK/package manager involvement
- **Capability System**: Reuse existing capability checking logic without mapping to Android permissions
- **Launch Parameter Passing**: Keep existing JSON-based parameter handling; convert to Intent extras only at launcher boundary
- **Critical Files to Adapt**:
  - `ApplicationManager.cpp` → `ApplicationManagerService.java` (logic largely unchanged)
  - `LaunchPointManager.cpp` → `LaunchPointRegistry.java` (minimal changes)
  - App discovery logic → File scanning of webOS app directory (analogous to existing webOS paths)
  - Capability checks → Direct reuse of existing validation

**Key Challenges**:
- Managing WebOS app storage on Android filesystem
- Handling native Android apps if launcher must support them (separate discovery path vs. webOS apps)
- Supporting both native Android apps and webOS apps via PackageManager integration
- Mapping Android app metadata to internal launch point representation
- Handling separate discovery paths for native Android vs. webOS apps

**Effort**: **M** (straightforward porting, preserve existing implementation)

**Key Challenges**:
- Permission model mismatch (webOS capabilities vs. Android permissions)
- Handling apps with no `appinfo.json` (web apps only, or converted apps)
- Launch parameter marshaling between webOS apps and native Android apps

**Effort**: **XL** (moderate complexity, good code reuse of state machine logic)

---

### luna-universalsearchmgr ("Just Type")
**Current Role**: Provides cross-application search/quick navigation; orchestrates search provider APIs; displays search UI.

**Android Port Strategy**:
- **Platform Integration**: Use Android `SearchManager` and `SearchView` as base
- **Search Provider Model**: Adapt webOS search plugin architecture to Android `SearchProvider` ContentProvider
- **Search Results Display**: Implement custom search result UI (can reuse app grid patterns from launcher)
- **Service Calls**: Replace webOS Luna Bus search API calls with Intent broadcasts or direct service calls
- **Persistence**: Move preference storage from Luna database to `SharedPreferences`
- **Critical Files to Adapt**:
  - `UniversalSearchService.cpp` → `UniversalSearchService.java` (Binder service)
  - `SearchServiceManager.cpp` → Search provider orchestration logic
  - `OpenSearchHandler.cpp` → URL-based search provider adapter
  - Search preferences → Android SharedPreferences

**Consideration**: Universal search may be lower priority for MVP; can be implemented in Phase 2 or 3.

**Effort**: **L-M** (lower priority, reasonable pattern mapping to Android APIs)

---

### luna-sysmgr-common (Shared Library)
**Current Role**: Provides common functionality (settings, localization, logging, device info, preferences, hardware abstraction via Nyx).

**Android Port Strategy**:
- **Modular Replacement**: Don't port as single library; replace components individually:
  - **Settings** → Android `SharedPreferences` or `DataStore`
  - **Localization** → Android `Locale` and `Resources` (or reuse if using Qt)
  - **Logging** → Android `Log` class or Timber/Logback
  - **Device Info** → `Build` class and `DeviceInfo` system service
  - **Hardware Abstraction (Nyx)** → Android `SensorManager`, `PowerManager`, `VibrationManager`
  - **Preferences** → Android preference framework
  - **Utilities** (JSON, timers, mutex) → Android equivalents or inline

**Key Decision**: Decide between:
- **Option A**: Rewrite in Java/Kotlin using Android APIs (cleaner, better integration)
- **Option B**: Keep as JNI C++ library, wrap Android APIs via JNI (maintains code similarity, adds complexity)

**Recommendation**: Option A for most components, Option B only for performance-critical sections (e.g., JSON parsing using existing pbnjson library).

**Effort**: **M-L** (straightforward mapping, no new logic needed)

---

## Further Considerations

### WebOS App Runtime Strategy
**Three Implementation Options:**
- **Option A (Simplest)**: WebView-only approach—host web apps in WebView instances with limited native feature access
- **Option B (Recommended)**: Hybrid with Android Bridge—WebView + JavaScript interface for critical system API calls
- **Option C (Highest Fidelity)**: Convert WebOS apps to native Android apps—maximum performance but highest engineering effort

**Recommendation**: Start with Option A (WebView) as MVP, consider Option B for broader WebOS app ecosystem support.

### System Service Scope
- **Core Services** (MVP): app launch, preference management, basic notifications
- **Extended Services** (Phase 2): universal search, device-specific behaviors, advanced system UI integration
- Phased approach recommended: launch with core launcher + app management, expand in subsequent phases

### Testing & Compatibility
- Plan for testing WebOS apps on Android runtime
- Establish compatibility matrix for supported `appinfo.json` features
- Define fallback behavior for unsupported webOS APIs
- Consider using existing test apps from webOS ecosystem
- Create regression test suite for ported app management logic

### Critical Architecture 

#### IPC Transport
- **Binder (Native Android)**: Best performance and Android integration, but complex debugging
-**MVP+**you could extract and adapt the business logic from how services currently use LS2 (call patterns, message formats, subscriptions) into Binder 

#### Web App Runtime Foundation
- **Qt WebEngine**: Maintains Qt architecture consistency, heavier footprint on Android

#### UI Framework
- **Qt for Android**: Minimal code changes, potential performance penalty, larger APK size (~150MB+)
- **Hybrid (Jetpack Compose + Qt)**: Complex integration, potential for best of both worlds

### Dependency Replacement Strategy

| webOS/Linux Component | Android Equivalent | Notes |
|---|---|---|
| Luna Service 2 | Binder/Intent/gRPC | Core IPC layer |
| Qt5 GUI | Android Jetpack Compose or native Views | UI rendering |
| Nyx Hardware HAL | Android SensorManager, PowerManager, VibrationEffect | Hardware access |
| GLib event loop | Android Handler/Looper or Coroutines | Async primitives |
| WebKit2 | Android WebView | Web app rendering |
| SQLite3 | SQLite3 (unchanged) or Room | Data persistence |
| OpenSSL | Android Security Framework or BoringSSL | Cryptography |
| systemd/DBus | Android Service/Intent Framework | System integration |
| PmLogLib | Android Log (Timber/Logback) | Logging |

### Estimated Effort Breakdown (Relative T-Shirt Sizing)

| Task | Effort | Risk | Dependencies |
|---|---|---|---|
| IPC Layer (Binder abstraction) | **XXL** | **HIGH** | Architecture decision |
| ApplicationManager adaptation | **XL** | **MEDIUM** | IPC Layer complete |
| WebOS app runtime (WebView bridge) | **XXL** | **HIGH** | IPC Layer + JavaScript binding strategy |
| Hardware abstraction (Nyx→Android HAL) | **L** | **MEDIUM** | Android API availability by device |
| UI/Launcher implementation | **XXL** | **HIGH** | IPC + design spec needed |
| Build system migration | **M** | **LOW** | NDK/Gradle experience |
| Service stubs (core webOS APIs) | **L** | **MEDIUM** | API scope definition |
| Testing infrastructure | **XL** | **MEDIUM** | Throughout project |

---

## Phase Breakdown (Recommended)

### Phase 1: Foundation & MVP (8-12 weeks)

**Goal**: Establish core infrastructure and prove viability of web app execution on Android with basic app management.

#### 1.1 Android Binder IPC Layer
- **Task**: Create AIDL service interfaces for core communication
- **Deliverables**:
  - `IApplicationManager.aidl` - app discovery, launch, query operations
  - `ISystemManager.aidl` - display control, system state queries
  - `ApplicationManagerService.java` - service implementation with Binder stub
  - `SystemManagerService.java` - system control service
- **Technical Details**:
  - Implement `Service` base classes extending `android.app.Service`
  - Use `LocalBroadcastManager` for app state subscriptions (replacing Luna Bus subscriptions)
  - Create `ServiceConnection` classes for Binder binding
  - Implement connection pooling for multiple clients
- **Testing**: Unit tests for service startup, method invocation, callback delivery
- **Effort**: 2-3 weeks

#### 1.2 Application Manager Core
- **Task**: Port ApplicationManager state machine and adapt to Android PackageManager
- **Deliverables**:
  - `ApplicationManagerImpl.java` - core app discovery and launch logic
  - `AppMetadata.java` - parser for `appinfo.json` (fallback to manifest)
  - `LaunchPointRegistry.java` - maintain launch point state
  - `PackageManagerListener.java` - hook into PACKAGE_ADDED/PACKAGE_REMOVED broadcasts
- **Technical Details**:
  - Parse `appinfo.json` from app resources (assets or extracted)
  - Map app metadata: `id`, `title`, `icon`, `requiredMemory`, `launchPoints[]`
  - Implement app discovery via `PackageManager.getInstalledApplications()`
  - Filter for WebOS-compatible apps (presence of `appinfo.json`)
  - Register broadcast receiver for package changes
  - Store app state in in-memory cache + SQLite backup
- **Key Decision**: Store web app metadata in SQLite with schema for fast queries
- **Testing**: Test app discovery with multiple test apps, verify metadata parsing, broadcast handling
- **Effort**: 2-3 weeks

#### 1.3 Web App Runtime (WebView-based)
- **Task**: Build WebView infrastructure for hosting web apps with IPC bridge
- **Deliverables**:
  - `WebAppRuntime.java` - singleton managing all web app instances
  - `WebAppInstance.java` - wrapper around WebView instance
  - `WebAppJavaScriptInterface.java` - JavaScript→Java bridge for system API access
  - `WebViewClientImpl.java` - custom WebViewClient for lifecycle handling
  - `WebChromeClientImpl.java` - custom WebChromeClient for dialogs/permissions
- **Technical Details**:
  - One WebView per running web app (or reuse if single-app focus for MVP)
  - `addJavascriptInterface()` to expose Java object to JavaScript
  - Basic API stubs in JavaScript interface: `launchApp()`, `registerService()`, `call()`, `closeApp()`
  - Storage isolation: Use WebView `setWebViewDatabase()` for per-app cookies/storage
  - Session management: Map app instance ID to WebView + lifecycle state
- **Limitations for MVP**: Limited hardware API access, no real service subscriptions, simplified multi-app handling
- **Testing**: Load sample web app, verify JS bridge method calls work, test app lifecycle
- **Effort**: 3-4 weeks

#### 1.4 Basic Launcher UI
- **Task**: Minimal functional home screen and app launcher
- **Deliverables**:
  - `LauncherActivity.java` - main home screen
  - `AppGridAdapter.java` - RecyclerView adapter for app grid
  - `AppGridFragment.java` - app grid UI fragment
  - `SystemStatusBar.java` - minimal status bar
  - Layout XML files for responsive design
- **Technical Details**:
  - Home screen layout: GridLayout with app icons (2-3 columns for phone, adaptive for tablet)
  - App selection launches via `ApplicationManagerService.launchApp()`
  - Status bar shows: time, battery percentage, signal strength, notification count
  - Long-press gesture: stub for app options (future feature)
  - Support portrait/landscape orientations
- **Scope for MVP**: Simple grid, no customization, basic interactions
- **Testing**: Verify app taps trigger launch, status bar updates, UI responsive on device
- **Effort**: 2-3 weeks

#### 1.5 Basic System Integration
- **Task**: Hook launcher into Android system lifecycle and boot
- **Deliverables**:
  - `LauncherBootReceiver.java` - receive BOOT_COMPLETED and set as default launcher
  - `BootService.java` - initialize ApplicationManagerService on boot
  - AndroidManifest.xml modifications - declare launcher role, permissions
- **Technical Details**:
  - Register to receive `android.intent.action.BOOT_COMPLETED`
  - Set launcher as default via `setComponentEnabledSetting()`
  - Start ApplicationManagerService at boot
  - Request minimum permissions: `QUERY_ALL_PACKAGES`, `ACCESS_FINE_LOCATION` (stubs)
- **Testing**: Boot device, verify launcher is default, services start
- **Effort**: 1 week

#### Phase 1 Success Criteria
- [ ] ApplicationManagerService running and discoverable
- [ ] App discovery works—can list installed compatible apps
- [ ] Web app loads in WebView and displays
- [ ] JavaScript bridge allows basic method calls from web app to service
- [ ] Launcher UI shows app grid and launches apps (native and web apps)
- [ ] No crashes during normal usage (app launch, back navigation)
- [ ] Startup time: < 5 seconds launcher ready, < 3 seconds web app load
- [ ] Memory: launcher service < 100MB

#### Phase 1 Deliverable
**Usable MVP Launcher**: Basic Android launcher with app grid, ability to launch native and web apps, and JavaScript bridge for web app communication with Android system services.

---

### Phase 2: Stability & Extended Compatibility (10-14 weeks)

**Goal**: Stabilize web app runtime, implement critical hardware integration, expand UI, establish compatibility test suite.

#### 2.1 Web App Compatibility Layer (JavaScript API Expansion)
- **Task**: Implement real implementations (not stubs) for common webOS APIs
- **Deliverables**:
  - `PalmServiceBridge.java` - replaces Luna Bus calls with Android equivalents
  - Real implementations for notifications, preferences, display control, service discovery
  - `ServiceTranslationLayer.java` - maps webOS service names to Android APIs
- **Technical Details**:
  - **Notifications**: `NotificationManager` + `NotificationCompat` (web app calls → Android notification)
  - **Preferences**: Android `SharedPreferences` per app (web app calls → `SharedPreferences.getSharedPreferences(appId)`)
  - **Display**: `PowerManager` + `WindowManager` (web app display control calls → Android system settings)
  - **Service Calls**: Async bridges for app-to-app communication with response handling
- **Error Handling**: Return standardized error responses for unsupported APIs
- **Effort**: 3-4 weeks

#### 2.2 Hardware Abstraction Layer (HAL)
- **Task**: Implement sensor, power, and device property access
- **Deliverables**:
  - `DeviceHardwareManager.java` - singleton managing hardware resources
  - `SensorHandler.java` - accelerometer, proximity, light sensor access
  - `PowerManagerHandler.java` - display, vibration, sleep control
  - `DevicePropertyProvider.java` - device info API
- **Technical Details**:
  - **Sensors**: Register with `SensorManager` for accelerometer, proximity; expose via JavaScript callbacks
  - **Power Control**: Display brightness via `Settings.System.SCREEN_BRIGHTNESS`, vibration via `VibrationEffect`
  - **Device Properties**: Manufacturer, model, Android version, screen size, DPI, RAM, storage
- **Constraints**: Some features require system app permissions or special handling
- **Testing**: Manual testing on device, verify sensor data and vibration patterns
- **Effort**: 2-3 weeks

#### 2.3 Enhanced Launcher UI
- **Task**: Expand launcher with app drawer, app shortcuts, notification integration
- **Deliverables**:
  - `AppDrawerFragment.java` - scrollable app drawer (all-apps list)
  - `QuickAccessBar.java` - frequently-used apps or favorites
  - `ShortcutManager` integration - app-specific shortcuts
  - `NotificationListFragment.java` - notification center
- **Technical Details**:
  - App drawer: swipe-up or button press to reveal alphabetical app list
  - Quick access bar: top row with frequently launched apps
  - Tap-hold on app: show context menu with options
  - Notification center: swipe-down to view active notifications
  - Smooth transitions with shared element animations
  - Support portrait/landscape and tablet layouts
- **Testing**: Verify responsive UI, smooth scrolling, tap responsiveness
- **Effort**: 2-3 weeks

#### 2.4 Multi-window & App Management
- **Task**: Enable running multiple apps, app switching
- **Deliverables**:
  - `AppWindowManager.java` - manage multiple app instances
  - `AppSwitcher.java` - recents/task switcher UI
  - Support split-screen on tablets, app switching on phones
- **Technical Details**:
  - On phones: swipe or button to switch between recent apps (maintain app state)
  - On tablets: drag app into split-screen layout or use multi-window API
  - Maintain app window state: position, size, z-order
  - Implement app pause/resume lifecycle
- **Scope for Phase 2**: Basic support; advanced gestures in Phase 3
- **Testing**: Launch multiple apps, switch between them, verify state preservation
- **Effort**: 2-3 weeks

#### 2.5 Web App Compatibility Test Suite
- **Task**: Build automated test infrastructure for web app compatibility
- **Deliverables**:
  - `WebAppCompatibilityTest.java` - JUnit test suite
  - Test web apps covering basic load, JavaScript bridge, service API calls, multi-window
  - Compatibility matrix documentation
- **Technical Details**:
  - Use Android Espresso framework for UI testing
  - Create mock test web apps with known feature sets
  - Automated testing: app load time, memory usage, crash detection
  - Generate compatibility report
- **Documentation**: Maintain list of tested apps and known incompatibilities
- **Effort**: 2-3 weeks

#### 2.6 Crash Handling & Error Recovery
- **Task**: Graceful error handling and app recovery
- **Deliverables**:
  - `CrashHandler.java` - uncaught exception handler
  - `AppRecovery.java` - automatic app restart logic
  - Error UI: crash dialog with restart button
- **Technical Details**:
  - Catch uncaught exceptions in web app (JavaScript errors + WebView crashes)
  - Log crashes to local database with timestamp, stack trace, app ID
  - Offer user option to restart or return to home
  - Auto-restart limit (3 attempts) → disable problematic app
- **Testing**: Force app crashes, verify recovery mechanism
- **Effort**: 1-2 weeks

#### Phase 2 Success Criteria
- [ ] Web apps can access common system services (notifications, preferences, display)
- [ ] Sensor data accessible to web apps (accelerometer, proximity)
- [ ] Multiple apps running simultaneously (app switching)
- [ ] Notification center displays app notifications
- [ ] App drawer shows all installed apps alphabetically
- [ ] Launcher responsive on test device (60 FPS scrolling)
- [ ] Crash recovery: app crash doesn't crash launcher
- [ ] Compatibility test suite: at least 5 test apps passing
- [ ] Memory usage stable (no major leaks after 1 hour)

#### Phase 2 Deliverable
**Functional Launcher with Web App Runtime**: Complete Android launcher with app discovery, multi-app execution, expanded system API access, and formal compatibility testing.

---

### Phase 3: Production Hardening & Feature Expansion (12-16 weeks)

**Goal**: Optimize performance, add advanced features, ensure production readiness, expand service compatibility.

#### 3.1 Performance Optimization
- **Task**: Profile, identify bottlenecks, optimize critical paths
- **Deliverables**:
  - Performance benchmarks and targets met
  - Reduced startup time, improved memory efficiency
- **Technical Details**:
  - **Profiling**: Use Android Profiler (CPU, memory, network, power)
    - App launch targets: < 2 seconds native, < 3 seconds web
    - Memory targets: launcher < 150MB, web app < 80MB
    - Rendering targets: 60 FPS smooth interactions
  - **Optimization**: Cache app list, lazy-load icons, preload WebView context, optimize layouts, reduce memory allocation
  - **Battery**: Minimize wake locks, batch sensor updates, tune refresh rates
- **Testing**: Continuous performance metrics; regression detection
- **Effort**: 3-4 weeks

#### 3.2 Advanced Service Translation Layer
- **Task**: Expand service translations for more webOS APIs
- **Deliverables**:
  - Service stubs for calendar, contacts, location, accounts, email
  - Compatibility matrix: translated services vs. unsupported ones
- **Technical Details**:
  - Use ContentProvider queries for calendar/contacts
  - Use LocationManager, AccountManager for others
  - Permission handling: request user permission before accessing
  - Error response for unavailable services
- **Testing**: Verify app can read/write to various system services
- **Effort**: 2-3 weeks

#### 3.3 Universal Search Integration (Just Type)
- **Task**: Implement webOS-style "Just Type" search using Android SearchManager
- **Deliverables**:
  - `UniversalSearchService.java` - Binder service for search
  - Search UI integration in launcher (search bar, results overlay)
- **Technical Details**:
  - Search bar in launcher header
  - Results from installed apps (by name), web search (optional), app content
  - Use Android SearchManager for system integration
- **Scope**: Basic app search MVP
- **Testing**: Search various apps, verify results accurate and fast
- **Effort**: 2-3 weeks

#### 3.4 Lock Screen & Security
- **Task**: Implement secure lock screen and permission checking
- **Deliverables**:
  - `LockScreenActivity.java` - custom lock screen (PIN, pattern, biometric)
  - `CapabilityChecker.java` - check app permissions before granting API access
  - `SecurePreferences.java` - encrypted storage for sensitive data
- **Technical Details**:
  - Lock screen: PIN/pattern input, biometric support (BiometricPrompt)
  - App capability enforcement: check permissions before exposing API
  - Store permissions in encrypted SharedPreferences (EncryptedSharedPreferences)
  - Implement user grant/deny for app capabilities
- **Testing**: Verify lock screen, permission prompts, encrypted storage
- **Effort**: 2-3 weeks

#### 3.5 System UI Integration
- **Task**: Deep integration with Android SystemUI
- **Deliverables**:
  - Custom status bar with launcher-specific information
  - Quick settings tiles for common actions
  - Integration with system notifications
- **Technical Details**:
  - Publish custom status bar design (time, battery, signal, app icons)
  - Expose quick settings tiles for frequently used functions
  - Subscribe to system broadcasts for state changes
  - Handle system gestures (status bar swipe-down, edge gestures)
- **Scope**: MVP level; advanced customization optional
- **Testing**: Verify status bar updates, quick settings respond
- **Effort**: 1-2 weeks

#### 3.6 Documentation & Developer Resources
- **Task**: Comprehensive documentation for web app developers
- **Deliverables**:
  - **Developer Guide**: How to port webOS apps to Android
  - **API Reference**: Complete list of supported webOS APIs
  - **Sample Apps**: 2-3 example web apps demonstrating patterns
  - **FAQ**: Common issues and troubleshooting
  - **Compatibility Matrix**: Detailed feature support by app
- **Technical Details**:
  - Document each service bridge with examples
  - Explain Android-specific limitations
  - Provide code snippets for common tasks
  - Maintain repository README with architecture and build instructions
- **Effort**: 2 weeks

#### 3.7 Advanced Testing & QA
- **Task**: Comprehensive testing suite and quality assurance
- **Deliverables**:
  - Automated test suite covering edge cases
  - Manual testing checklist for QA
  - Performance and battery test results
  - Compatibility report
- **Technical Details**:
  - Unit tests: service initialization, app discovery, parsing
  - Integration tests: app launch, service calls, multi-app scenarios
  - UI tests: launcher interaction, app switching, gestures
  - System tests: boot cycle, recovery, long-duration stability
  - Performance tests: startup, memory, rendering
- **Effort**: 3-4 weeks

#### Phase 3 Success Criteria
- [ ] App startup time: < 2 seconds native, < 3 seconds web apps
- [ ] Launcher memory: < 150MB steady state
- [ ] Web app memory: < 80MB per instance
- [ ] 60 FPS smooth scrolling and animations
- [ ] No memory leaks after 2 hours of continuous use
- [ ] 20+ webOS apps tested and documented for compatibility
- [ ] Battery impact: < 10% increase over baseline
- [ ] Lock screen and permission system working
- [ ] All documented service APIs functional
- [ ] Comprehensive developer guide and API reference published

#### Phase 3 Deliverable
**Production-Ready Launcher**: Optimized, feature-complete Android launcher with web app runtime, comprehensive system integration, and extensive web app compatibility. Ready for distribution.

---

### Cross-Phase Requirements

#### Continuous Integration & Deployment
- **Git Repository**: Organized structure with app, libraries, tests, docs, samples
- **Build**: Gradle with automated builds on commit
- **Testing**: Run unit/integration tests on each commit
- **Releases**: Tag versions, generate release notes

#### Documentation Throughout
- Maintain README.md with project overview, build instructions, architecture
- Document architectural decisions (ADRs) for major choices
- Keep API compatibility matrix updated
- Inline code comments for non-obvious logic

#### Metrics & Monitoring
- Track app launch times, memory usage, crashes, permissions
- Enable opt-in telemetry for production data
- Establish performance baselines early

---

### Phase Timeline Summary

| Phase | Duration | Key Outcome |
|-------|----------|-------------|
| Phase 1 | 8-12 weeks | MVP launcher with basic web app support |
| Phase 2 | 10-14 weeks | Expanded API compatibility, multi-app support, testing |
| Phase 3 | 12-16 weeks | Production hardening, advanced features, broad compatibility |
| **Total** | **30-42 weeks** (**~7-10 months**) | **Production-ready launcher** |

*Note: Durations are estimates for solo development. Actual timeline depends on complexity encountered, testing iterations, and scope refinements.*

---

## Key Answers to High-Level Design Questions

### 1. Target Android Version
**Decision**: API 30+ (Android 11+)
**Implications**:
- Supports modern devices (2020 onwards)
- Access to modern APIs: `scoped storage`, `permission auto-reset`, improved power management
- Excludes legacy devices but focuses on stable performance on modern flagships
- Can use newer Jetpack libraries and androidx components

### 2. Supported Device Types
**Decision**: Both Phone and Tablets
**Implications**:
- UI must be responsive and adaptive (phone portrait/landscape, tablet modes)
- App drawer and launcher grid need flexible layouts
- Consider split-screen and multi-window support for tablets
- Test on both form factors during development

### 3. WebOS App Scope
**Decision**: Web-only for now (exclude native webOS services)
**Implications**:
- Focus on Enyo/HTML5 web apps only
- Simplifies MVP scope—no need to port service infrastructure
- Web apps get WebView runtime with JavaScript bridge
- Native Android apps continue to work normally
- Future migration: can expand to service apps in Phase 2+

### 4. Performance Targets
**Decision**: Stable performance on modern flagships
**Implications**:
- Target devices with 6GB+ RAM and modern processors
- Acceptable startup time: < 2 seconds for app launch
- Smooth 60 FPS in UI interactions and app drawer scrolling
- Memory footprint: launcher service < 150MB, per-web-app instance < 80MB
- Battery impact: negligible over baseline system
- Can optimize for flagship specs rather than mid-range

### 5. Security Model
**Decision**: Permissions compatibility layer service
**Implications**:
- Create abstraction service that bridges webOS app capability requirements to Android permission requests
- Map webOS `appinfo.json` capabilities to Android permission groups (camera, location, contacts, etc.)
- Implement runtime permission flow: prompt user when web app attempts to access protected resource
- Store permission grants per app in SharedPreferences
- Consider: deny-by-default for sensitive resources (reduce surface attack area)

### 6. Maintenance Model
**Decision**: Solo developer
**Implications**:
- Prioritize simplicity over flexibility in architecture
- Binder IPC preferred over gRPC (less dependencies, tighter integration with Android ecosystem)
- Avoid over-engineering with abstraction layers—focus on pragmatic solutions
- Use well-established libraries and patterns (Jetpack, Room, WorkManager) for stability
- Build with thorough documentation and inline code comments for future maintainability
- Keep scope tightly controlled—focus on core launcher + web app runtime in MVP

### 7. Third-party webOS Services
**Decision**: Translations layer for services to standard API calls
**Implications**:
- Intercept web app service calls (e.g., `palm://com.palm.calendar/`) via JavaScript bridge
- Translate to Android standard APIs where available:
  - `com.palm.calendar` → Android Calendar provider (ContentProvider)
  - `com.palm.contacts` → Android Contacts provider
  - `com.palm.notification` → Android NotificationManager
  - `com.palm.location` → Android LocationManager
  - `com.palm.telephony` → Android TelephonyManager
- For unsupported services: return graceful error or stub responses
- This approach avoids needing to port entire webOS service architecture
- Can document translatable services vs. unsupported ones in compatibility matrix
