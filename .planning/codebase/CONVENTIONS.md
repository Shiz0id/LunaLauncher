# Coding Conventions

**Analysis Date:** 2026-02-20

## Naming Patterns

**Files:**
- Entities: `*Entity.kt` (e.g., `LaunchPointEntity.kt`, `DeckCardEntity.kt`)
- DAOs: `*Dao.kt` (e.g., `LaunchPointDao.kt`, `DockDao.kt`)
- Repositories: `*Repository.kt` (e.g., `LaunchPointRepository.kt`, `DeckRepository.kt`)
- ViewModels: `*ViewModel.kt` (e.g., `LauncherViewModel.kt`, `DeckViewModel.kt`)
- Composables: PascalCase + `.kt` (e.g., `HomeScreen.kt`, `LaunchPointMenuSheet.kt`)
- Activities: `*Activity.kt` (e.g., `LauncherActivity.kt`, `JustTypeSettingsActivity.kt`)
- Providers/Managers: `*Provider.kt`, `*Manager.kt`, `*Repository.kt`

**Functions:**
- camelCase, prefixed with action verb: `buildState()`, `queryContacts()`, `observeVisible()`, `syncAndroidApps()`
- Composable functions: PascalCase (e.g., `HomeScreen()`, `LaunchPointIcon()`)
- Private helper functions: camelCase with `private` modifier
- Extension functions: lowercase verb (e.g., `toRecord()`, `toPlacement()`, `toDebugString()`)

**Variables:**
- camelCase for local variables and properties: `searchQuery`, `launchPointId`, `xNorm`
- StateFlow backing fields: `_privateName` pattern (private `MutableStateFlow`), then expose public `.asStateFlow()` or via `val public: StateFlow<T>`
- Data class fields: camelCase
- Boolean fields: prefixed with `is` or `has` (e.g., `pinned`, `hidden`, `hasPhone`, `isNotificationAccessGranted`)

**Types:**
- Enums: PascalCase (e.g., `LaunchPointType`, `MenuSource`, `JustTypeCategory`, `LauncherColorTheme`)
- Data classes: PascalCase with `data class` keyword (e.g., `LaunchPointEntity`, `HomeIconPlacement`, `DeckCard`)
- Sealed classes: PascalCase (e.g., `LauncherEvent`, `JustTypeItemUi`)
- Interfaces: PascalCase (e.g., `LaunchPoint`)

**Constants:**
- UPPER_SNAKE_CASE for true constants
- Example: `FLAG_BLUR_BEHIND`, `EXTRA_APP_WIDGET_ID`
- Database migration constants: `MIGRATION_X_Y` (e.g., `MIGRATION_10_11`)

**LaunchPoint IDs (special format):**
- Android apps: `android:{packageName}/{activityName}` (stable identifier)
- Icon keys: `android:{packageName}/{activityName}@{versionCode}` (cache-busting format)
- Preference keys: simple strings without special characters (e.g., `"theme_style"`, `"color_theme"`)

## Code Style

**Formatting:**
- No explicit formatter (not using Ktlint, Spotless, or Prettier)
- Manual formatting convention: 4-space indentation
- Line lengths: typically keep under 120 characters
- Imports: grouped with blank lines separating Android, androidx, kotlinx, and project imports
- No trailing commas in Kotlin lists/maps (follows Kotlin stdlib convention)

**Linting:**
- No explicit linter configured (no `.eslintrc`, `.prettierrc`, or similar)
- Kotlin compiler warnings respected, code is written to minimize them
- Best practices followed for Kotlin idioms and Android lifecycle awareness

**Kotlin Idioms:**
- Use `data class` for immutable value objects (e.g., `LaunchPointEntity`, `DeckCard`, `HomeIconPlacement`)
- Use `copy()` for immutable state updates (e.g., `entity.copy(pinned = true)`)
- Null coalescing with `?:` operator (e.g., `entity?.id ?: "unknown"`)
- Use `?.let { }` for null-safe optional chaining
- Use `?.let` and `.orEmpty()` for collections
- String interpolation: `"Value: ${variable}"` (not template strings)

## Import Organization

**Order:**
1. Android framework imports (`android.*`)
2. AndroidX imports (`androidx.*`)
3. Kotlin stdlib and coroutines (`kotlinx.coroutines.*`, `kotlin.*`)
4. Project imports (`com.lunasysman.launcher.*`)
5. Blank line between groups

**Path Aliases:**
- No package aliases (no `import X as Y`)
- Fully qualified class names used throughout (improves searchability)
- Exception: `import androidx.compose.material3.Text` — long names imported directly to avoid repetition in Compose lambdas

**Imports Example:**
```kotlin
import android.content.Intent
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.lunasysman.launcher.core.model.LaunchPoint
```

## Error Handling

**Patterns:**
- Exception-specific catch blocks before generic `Exception` catch
- Type-based dispatch: `if (e is SpecificException)` for conditional handling
- Graceful degradation: keep launcher usable even if subsystems fail
- Silent failures for optional/non-critical features (logged but not thrown)

**Examples:**
```kotlin
// From LauncherActivity.kt: Activity launching
try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Log.e("LunaLauncher", "Activity not found: ...", e)
    Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
    false
} catch (e: SecurityException) {
    Log.e("LunaLauncher", "Security exception: ...", e)
    Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
    false
} catch (e: Exception) {
    Log.e("LunaLauncher", "Failed to launch intent: ...", e)
    Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show()
    false
}

// From LauncherContainer.kt: Silent failure for optional initialization
appScope.launch {
    try {
        justTypeRegistry.initialize()
    } catch (_: Exception) {
        // Keep launcher usable even if registry init fails.
    }
}
```

**Result Types:**
- Sealed classes for typed results (e.g., `NotificationActionExecutor.ExecutionResult`)
  - `is SpecificResult` pattern used in when expressions
- `runCatching { }.getOrDefault(fallback)` for safe conversions
- Optional returns (`T?`) for operations that may fail

## Logging

**Framework:** `android.util.Log`

**Patterns:**
- Tag convention: `"LunaLauncher"` (app-wide consistent tag)
- `Log.e("LunaLauncher", message, exception)` for errors with full context
- `Log.d()` rarely used (not in main flow, reserved for debugging)
- Gesture debugging: `GestureDebug.log(tag, message)` for detailed gesture tracing (disabled by default)

**Example from LauncherActivity.kt:**
```kotlin
Log.e("LunaLauncher", "Activity not found: ${intent.toDebugString()}", e)
Log.e("LunaLauncher", "intentFor failed for id=$id", e)
```

## Comments

**When to Comment:**
- Public API contracts: function parameters, return values, side effects
- Non-obvious business logic: special formatting rules, conflict resolution strategies
- Architecture decisions: why a pattern was chosen over alternatives
- TODO/FIXME: NO TODO or FIXME comments found in codebase (design is stable)

**JSDoc/KDoc:**
- KDoc format used for public classes, interfaces, and functions
- Example from `LaunchPoint.kt`:
```kotlin
/**
 * A runtime-agnostic description of something the user can launch.
 *
 * Public contract:
 * - Stable, immutable [id]
 * - Immutable [type]
 * - Mutable user state: [pinned], [hidden], [lastLaunchedAtEpochMs]
 *
 * UI modules must only depend on this interface and actions; platform-specific details live elsewhere.
 */
interface LaunchPoint {
```

- Parameters documented with `@param` and `@return` tags when non-obvious
- Example from `HomeGestures.kt`:
```kotlin
/**
 * Unified gesture modifier for the Home surface.
 *
 * Handles three primary gestures using a single awaitEachGesture pipeline:
 * - Swipe up: Opens All Apps
 * - Swipe down: Opens Search
 * - Long-press on background: Enters Edit Mode
 *
 * @param gestureLock Centralized lock to coordinate with icon and widget gestures
 * @param thresholds Gesture threshold configuration (touch slop, long-press timeout, swipe distance)
 * @param enabled Whether gestures are enabled (typically disabled when search/edit mode is active)
 * @param onSwipeUp Callback invoked when user swipes up beyond threshold
 */
internal fun Modifier.homeSurfaceGestures(...)
```

## Function Design

**Size:** Functions kept concise (typically under 50 lines)
- Complex logic broken into helper functions or extension functions
- Large state aggregations (like `LauncherViewModel`) use composition with private helper functions

**Parameters:**
- Constructor parameters preferred over setters (immutability)
- Named parameters used for clarity in call sites
- Trailing lambda syntax for single callback parameters
- Multiple parameters grouped logically (e.g., positioning params: `xNorm, yNorm, rotationDeg`)

**Return Values:**
- `Flow<T>` for observable state (from repositories/DAOs)
- `StateFlow<T>` for UI state (from ViewModels)
- `suspend` functions for coroutine-based async operations
- Nullable return (`T?`) for optional results
- Collections default to immutable: `List<T>`, `Set<T>` (not `ArrayList`, `HashSet`)

**Example from LauncherViewModel.kt:**
```kotlin
// Observe pattern: Flow return for persistence layer
fun observeVisibleLaunchPoints(): Flow<List<LaunchPointRecord>>

// StateFlow for UI state
val appsItems: StateFlow<List<LaunchPoint>> =
    allItems
        .map { list -> list.sortedWith(...) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

// Suspend function for async operation
suspend fun queryContacts(query: String): List<JustTypeItemUi.DbRowItem>

// Nullable for optional resolution
fun intentFor(id: String): Intent?
```

## Module Design

**Exports:**
- Public types defined in top-level package (e.g., `com.lunasysman.launcher.core.model.LaunchPoint`)
- Internal implementation details marked `internal` (not public)
- No barrel files (no `index.kt` or `__init__.kt` re-exports)

**Barrel Files:**
- Not used in this codebase
- Each file imports directly from its source location

**Module Boundaries:**
- `:core-model`: Domain models and business logic only (zero Android UI dependencies)
- `:data`: Room database, DAOs, repositories (Android framework only, no Compose)
- `:app`: Activity, ViewModel, manual DI container
- `:ui-*`: Composable UI (home, search, appmenu) — depend on core-model and data, never on each other
- `:apps-android`: Android PackageManager scanner (produces LaunchPoints)

**Cross-module Dependencies:**
- `core-model` ← no dependencies
- `data` ← `core-model`
- `app` ← all modules (wiring)
- `ui-home`, `ui-search`, `ui-appmenu` ← `core-model`, `data` (no cross-UI dependencies)
- `apps-android` ← `core-model`

## State Management

**StateFlow Pattern (MANDATORY):**
- All UI state exposed as `StateFlow<T>` (never `LiveData`)
- Private `MutableStateFlow` with public exposure via property
- Example from `LauncherViewModel.kt`:
```kotlin
private val _searchQuery = MutableStateFlow("")
val searchQuery: StateFlow<String> = _searchQuery

fun setSearchQuery(value: String) {
    _searchQuery.value = value
}
```

**Sharing Strategy:**
- `SharingStarted.WhileSubscribed(5000)` for memory efficiency (timeout 5 seconds)
- All derived StateFlows use this pattern
- Example:
```kotlin
val appsItems: StateFlow<List<LaunchPoint>> =
    allItems
        .map { list -> list.sortedWith(...) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

**Observation in Compose:**
- `collectAsStateWithLifecycle()` (not `collectAsState()`) for lifecycle awareness
- Example from `LauncherActivity.kt`:
```kotlin
val appsItems by vm.appsItems.collectAsStateWithLifecycle()
val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
```

## Dispatcher Usage

**Coroutine Dispatchers:**
- `Dispatchers.Default`: CPU-bound computation (sorting, scoring, search)
- `Dispatchers.IO`: Database and file I/O operations
- `Dispatchers.Main`: UI updates (implicit in Compose)

**Examples:**
```kotlin
// From LauncherViewModel: scoring on background thread
val justTypeState: StateFlow<JustTypeUiState> =
    combine(...) { ... }
        .flowOn(Dispatchers.Default) // scoring + sorting off the main thread
        .stateIn(...)

// From LauncherViewModel: contacts query on IO thread
suspend fun queryContacts(query: String): List<JustTypeItemUi.DbRowItem> =
    withContext(Dispatchers.IO) {
        // Database query
    }
```

## Database and Room

**Entity Design:**
- Data classes with `@PrimaryKey` and `@Entity` annotations
- No nullable primary keys
- Indices defined at entity level for frequently queried columns
- Example from `LaunchPointEntity.kt`:
```kotlin
@Entity(
    tableName = "launch_points",
    indices = [
        Index(value = ["hidden", "pinned", "lastLaunchedAtEpochMs"]),
        Index(value = ["type", "hidden"]),
        Index(value = ["sortKey"]),
    ],
)
data class LaunchPointEntity(
    @PrimaryKey val id: String,
    val type: String,
    ...
)
```

**DAO Pattern:**
- Suspend functions for single operations
- Flow return types for observable queries (no RxJava)
- Named parameters in queries for clarity
- Example from `LaunchPointDao.kt` pattern:
```kotlin
suspend fun getByIds(ids: List<String>): List<LaunchPointEntity>
fun observeVisible(): Flow<List<LaunchPointEntity>>
```

**Migrations:**
- Named constants: `MIGRATION_X_Y` where X and Y are version numbers
- Always add a new migration when changing schema, never bump version without one
- Migrations live in `LauncherDatabase.kt` companion object
- Example from `LauncherDatabase.kt`:
```kotlin
private val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Migration SQL
        }
    }
```

**Transactions:**
- Use `withTransaction { }` for multi-step operations with rollback safety
- Example from `LaunchPointRepository.kt`:
```kotlin
suspend fun updateHomeIconPosition(...) {
    database.withTransaction {
        val all = homeIconPositionsDao.getAll()
        // Multiple operations with transactional guarantee
    }
}
```

## Compose Conventions

**No XML Layouts:**
- All UI is Jetpack Compose
- No android.widget or android.view layouts

**Single Gesture Pipeline:**
- One `pointerInput` block per gesture surface (no stacking `pointerInput` blocks)
- Centralized gesture lock (`GestureLock`) for coordination
- Threshold configuration in `GestureThresholds` object
- No `pointerInteropFilter` (all gestures are pure Compose)

**Composable Function Structure:**
```kotlin
@Composable
fun SurfaceComponent(
    state: StateFlow<State>,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by state.collectAsStateWithLifecycle()
    // UI layout
}
```

## Version Conventions

**Numeric Versioning:**
- Integer `version` field in config objects (e.g., `JustTypeProviderConfig`)
- Semantic versioning in app manifest (versionCode, versionName)
- Current: versionCode=1, versionName="0.0.1"

---

*Convention analysis: 2026-02-20*
