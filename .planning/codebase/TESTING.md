# Testing Patterns

**Analysis Date:** 2026-02-20

## Test Framework

**Runner:**
- Not detected — No test framework configured
- No test dependencies found in `build.gradle.kts` files
- No test source directories found (`src/test`, `src/androidTest`)

**Assertion Library:**
- Not applicable

**Test Configuration:**
- Not detected

**Run Commands:**
- Not applicable (no tests configured)

## Test Coverage Status

**Overall Coverage:** Not enforced
- No test files found in the entire codebase
- No testing infrastructure configured
- Design focuses on manual testing and runtime correctness

**Testing Approach:** Runtime verification through:
- Android emulator/device testing (manual)
- Code review for architectural correctness
- Type system (Kotlin) for compile-time safety
- CLAUDE.md onboarding for AI agents to avoid introducing bugs

## Test File Organization

**Location:** Not applicable — no test files exist
- Future pattern should follow Android convention:
  - Unit tests: `src/test/kotlin/com/lunasysman/launcher/...`
  - Instrumented tests: `src/androidTest/kotlin/com/lunasysman/launcher/...`

**Naming Convention:** When tests are added, follow pattern:
- Class being tested: `UserService.kt` → Test class: `UserServiceTest.kt`
- Suspended function tests: `should...When...` (e.g., `shouldThrowWhenQueryEmpty`)
- Function under test: private helper → tested indirectly through public API

## Code Design for Testability

While no tests exist, the codebase is structured to support testability:

**Dependency Injection:**
- Manual service-locator via `LauncherContainer.create(context)` (facilitates test mocking)
- Constructor injection in ViewModels and repositories
- Example from `LauncherViewModel`:
```kotlin
class LauncherViewModel(
    private val repository: LaunchPointRepository,
    private val scanner: AndroidAppScanner,
    private val appContext: Context,
    private val justTypeRegistry: JustTypeRegistry,
    private val notificationIndexer: NotificationIndexer,
) : ViewModel() {
```

**Testable Architecture:**
- Business logic separated from UI (core-model has no Compose dependencies)
- Repository pattern isolates data access (can be mocked)
- Sealed classes and interfaces enable type-safe test doubles
- Example: `LaunchPoint` interface allows test implementations

**Pure Functions:**
- Stateless helper functions that can be unit tested in isolation
- Example from `JustTypeEngine.kt`:
```kotlin
private fun extractPackageName(launchPointId: String): String? {
    val base = launchPointId.substringBefore("@")
    if (!base.startsWith("android:")) return null
    val remainder = base.removePrefix("android:")
    val pkg = remainder.substringBefore("/").trim()
    return pkg.ifEmpty { null }
}
```

## Testing Best Practices (for Future Implementation)

### Unit Testing Pattern

For data layer (repositories, repositories):
```kotlin
// Pattern to follow when tests are added
class LaunchPointRepositoryTest {
    private lateinit var dao: LaunchPointDao
    private lateinit var repository: LaunchPointRepository

    @Before
    fun setup() {
        dao = mockk()  // or actual Room test database
        repository = LaunchPointRepository(
            database = mockk(),
            dao = dao,
            dockDao = mockk(),
            homeSlotsDao = mockk(),
            homeIconPositionsDao = mockk(),
        )
    }

    @Test
    fun shouldReturnVisibleAppsOnly() {
        // Arrange
        val visible = listOf(app1, app2)
        coEvery { dao.observeVisible() } returns flowOf(visible)

        // Act
        val result = repository.observeVisibleLaunchPoints()

        // Assert
        result.test {
            assertThat(awaitItem()).isEqualTo(visible.map { it.toRecord() })
        }
    }
}
```

### Integration Testing Pattern

For ViewModels with multiple dependencies:
```kotlin
// Pattern to follow when tests are added
class LauncherViewModelTest {
    private lateinit var viewModel: LauncherViewModel
    private val repository = mockk<LaunchPointRepository>()
    private val scanner = mockk<AndroidAppScanner>()
    private val appContext = mockk<Context>()

    @Before
    fun setup() {
        viewModel = LauncherViewModel(
            repository = repository,
            scanner = scanner,
            appContext = appContext,
            justTypeRegistry = mockk(),
            notificationIndexer = mockk(),
        )
    }

    @Test
    fun shouldRankAppsWithRecencyBonus() = runTest {
        // Arrange: set up recent launch data
        val apps = listOf(recentApp, oldApp)
        val query = "test"

        // Act
        viewModel.setSearchQuery(query)
        val state = viewModel.justTypeState.value

        // Assert
        assertThat(state.sections).isNotEmpty()
        // recentApp should appear before oldApp due to recency bonus
    }
}
```

### Room Database Test Pattern

When database tests are added:
```kotlin
// Pattern to follow (using Room in-memory database)
@RunWith(AndroidJUnit4::class)
class LauncherDatabaseTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: LauncherDatabase
    private lateinit var dao: LaunchPointDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LauncherDatabase::class.java
        ).build()
        dao = database.launchPointDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun shouldPersistAndRetrieveApp() = runTest {
        // Arrange
        val app = LaunchPointEntity(...)

        // Act
        dao.upsert(app)
        val retrieved = dao.getById(app.id)

        // Assert
        assertThat(retrieved).isEqualTo(app)
    }
}
```

## Current Testing Approach

**Manual Verification:**
- Features tested by running on Android device/emulator
- Gesture interactions validated through manual testing (complex gesture state machine)
- Icon loading and caching verified visually
- Notification integration tested against real notification sources

**Code Review:**
- Architectural correctness reviewed against CLAUDE.md specifications
- Type safety enforced by Kotlin compiler
- Flow correctness validated through code inspection (StateFlow usage patterns)

**Documentation-Driven Testing:**
- CLAUDE.md provides detailed specifications for all major components
- `docs/Gestures.md` explains expected gesture behavior (state machine)
- Comments in code document non-obvious logic (e.g., conflict resolution in HomeIconEntity positioning)

## Areas Most Likely to Benefit from Testing

**High Priority:**
1. **JustTypeEngine.buildState()** (`core-model/.../justtype/engine/JustTypeEngine.kt`)
   - Complex scoring and ranking logic
   - Multiple provider integration points
   - Query tokenization and category filtering

2. **LaunchPointRepository sync logic** (`data/.../LaunchPointRepository.kt`)
   - Transaction safety in `syncAndroidApps()`
   - Merge conflicts in update operations
   - Icon key cache-busting logic

3. **Gesture state machine** (`ui-home/.../HomeGestures.kt`, `GestureState.kt`)
   - State transition validation
   - Touch slop and threshold application
   - Conflict detection with child gesture handlers

**Medium Priority:**
1. **Icon loading and caching** (`app/.../IconRepository.kt`)
   - Async batching (36-icon prefetch)
   - Cache key correctness
   - Fallback rendering

2. **Notification filtering and execution** (`core-model/.../notifications/NotificationIndexer.kt`, `NotificationActionExecutor.kt`)
   - In-memory index correctness
   - Permission validation
   - Reply text handling

**Low Priority:**
1. **Search template rendering** (URL encoding, template substitution)
2. **Theme configuration persistence** (SharedPreferences serialization)
3. **Deck widget host integration** (AppWidgetHost lifecycle management)

## Testing Considerations

**Challenges with Current Architecture:**
- Gesture testing requires Android framework (PointerEvent simulation)
- Notification testing requires NotificationListenerService (system service)
- Icon loading tied to Android Bitmap API
- Database migrations require version tracking across multiple versions

**Recommended Test Stack (if testing is added):**
```gradle
// Testing dependencies (pattern to follow)
testImplementation(libs.junit)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)  // Flow testing

androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.test.rules)
androidTestImplementation(libs.mockk.android)
androidTestImplementation(libs.androidx.room.testing)
```

**Testing Libraries Best Practices:**
- **MockK**: For Kotlin-idiomatic mocking (coEvery, verify, etc.)
- **Turbine**: For Flow/StateFlow assertion (preferred over collectingStatesIn)
- **Coroutines Test**: runTest { } for suspend function testing
- **Room**: inMemoryDatabaseBuilder for database tests
- **Instrum entedTest**: Required for gesture, notification, and Compose UI testing

## When to Test

**Add Tests When:**
- Fixing a bug (write test that catches the bug first)
- Refactoring a critical algorithm (regression protection)
- Adding a new search provider or query type
- Changing database schema or migration logic
- Implementing new gesture interaction

**Skip Tests When:**
- Adding a simple Composable (visual inspection sufficient)
- Updating theme colors or UI strings
- Refactoring UI layout (screenshot comparison preferred)

## Test Organization Recommendation

When tests are added, follow this structure:
```
launcher-android/
├── app/
│   ├── src/main/...
│   └── src/test/kotlin/com/lunasysman/launcher/
│       ├── LauncherViewModelTest.kt
│       └── IconRepositoryTest.kt
│
├── core-model/
│   └── src/test/kotlin/com/lunasysman/launcher/core/
│       ├── justtype/engine/JustTypeEngineTest.kt
│       ├── justtype/providers/AppsProviderTest.kt
│       └── model/LaunchPointTest.kt
│
├── data/
│   ├── src/test/kotlin/com/lunasysman/launcher/data/
│   │   ├── LaunchPointRepositoryTest.kt
│   │   └── LauncherDatabaseMigrationTest.kt
│   └── src/androidTest/kotlin/com/lunasysman/launcher/data/
│       └── LaunchPointDaoTest.kt  (Room in-memory DB)
│
└── ui-home/
    └── src/androidTest/kotlin/com/lunasysman/launcher/ui/home/
        └── HomeScreenTest.kt  (Compose UI testing)
```

---

*Testing analysis: 2026-02-20*
