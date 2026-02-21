# Technology Stack: Compose File Organization and Recomposition Optimization

**Project:** Luna Launcher - Compose File Refactor
**Researched:** 2026-02-20
**Focus:** Splitting 800+ line Compose files and optimizing recomposition performance
**Overall Confidence:** HIGH (sourced from official Android developer documentation)

---

## Executive Summary

Luna Launcher runs Kotlin 2.0.21 with Compose BOM 2024.12.01. This means **strong skipping mode is already enabled by default** (shipped in Kotlin 2.0.20), which is the single most impactful recomposition optimization available. The refactor should focus on (1) structural extraction using state hoisting patterns, (2) enabling Compose compiler reports to identify actual recomposition hotspots before and after extraction, and (3) applying stability fixes only where compiler reports flag real problems.

The core principle: **split by responsibility, not by line count**. A 400-line file with cohesive responsibility is better than two 200-line files that must always be edited together.

---

## Recommended Techniques for This Refactor

### 1. Compose Compiler Reports (Measure Before Optimizing)

**Version:** Already available with Kotlin 2.0.21 + Compose compiler plugin
**Confidence:** HIGH (official Android documentation)

Enable compiler reports to get a baseline before any refactoring:

```kotlin
// In each module's build.gradle.kts
android {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}
```

Run a **release build** (not debug) to generate accurate reports. Three files per module:

| File | Contents | Use For |
|------|----------|---------|
| `<module>-classes.txt` | Stability of each class | Finding unstable data classes |
| `<module>-composables.txt` | Restartable/skippable status per composable | Finding non-skippable composables |
| `<module>-composables.csv` | CSV version | Spreadsheet analysis |

**Action for this refactor:** Generate reports for `app`, `ui-home`, and `core-model` modules before starting. After each file extraction, regenerate and compare. Focus on composables that are `restartable` but NOT `skippable` -- those are the recomposition problem areas.

**Important caveat:** With strong skipping mode (default in Kotlin 2.0.21), most composables will already be skippable. The compiler reports will primarily help identify edge cases and validate that extraction did not introduce regressions.

### 2. Layout Inspector (Runtime Recomposition Counting)

**Version:** Built into Android Studio
**Confidence:** HIGH (official tooling)

Use Layout Inspector to observe **recomposition counts** and **skip counts** at runtime:

- Open Layout Inspector while app is running
- Navigate to each screen (home, search, widget picker)
- Observe which composables have high recomposition counts relative to their skip counts
- A composable that recomposes 50 times but skips 0 times is a problem
- A composable that recomposes 50 times and skips 45 times is working correctly

**Action for this refactor:** Profile each of the four target files before extraction. Record recomposition hotspots. After extraction, verify that hotspots improved or at minimum did not regress.

### 3. Strong Skipping Mode

**Version:** Default since Kotlin 2.0.20 (Luna Launcher is on 2.0.21)
**Confidence:** HIGH (official documentation)

Strong skipping is **already active** in this project. This means:

- All restartable composables are automatically skippable, even with unstable parameters
- Unstable parameters use instance equality (`===`) instead of structural equality
- Lambdas with captures are automatically wrapped in `remember()` by the compiler
- No configuration needed -- it is on by default

**Implication:** Many recomposition issues that would exist in older Compose versions are already mitigated. The refactor should still apply good patterns (state hoisting, remember scoping), but the urgency of fixing every stability issue is reduced.

**Opt-out annotation** (use only if strong skipping causes issues for a specific composable):
```kotlin
@NonSkippableComposable
@Composable
fun MyComposable() { ... }
```

---

## File Splitting Patterns

### Pattern A: State Hoisting Extraction

**What:** Extract state management into dedicated state holder files.
**Confidence:** HIGH (official Android state hoisting documentation)

For each mega-file, extract a `*State.kt` file containing:

```kotlin
// JustTypeState.kt
@Stable
class JustTypeState(
    val searchQuery: MutableState<String>,
    val isExpanded: MutableState<Boolean>,
    val activeProviders: SnapshotStateList<SearchProvider>
) {
    val hasQuery: Boolean get() = searchQuery.value.isNotEmpty()

    fun clearSearch() {
        searchQuery.value = ""
    }
}

@Composable
fun rememberJustTypeState(
    searchQuery: MutableState<String> = remember { mutableStateOf("") },
    isExpanded: MutableState<Boolean> = remember { mutableStateOf(false) },
    activeProviders: SnapshotStateList<SearchProvider> = remember { mutableStateListOf() }
): JustTypeState = remember(searchQuery, isExpanded, activeProviders) {
    JustTypeState(searchQuery, isExpanded, activeProviders)
}
```

**Why this pattern:** Annotating the state holder with `@Stable` tells the compiler it can trust equality checks. The `remember*State()` factory follows the same convention as `rememberLazyListState()`, `rememberScrollState()`, etc. This is the idiomatic Compose pattern for complex UI state.

**Rules for extraction:**
- State holder contains UI state and UI logic only (not business logic)
- Business logic stays in ViewModels (which Luna Launcher already uses via StateFlow)
- State holder is created and remembered in composition, following composable lifecycle
- Mark with `@Stable` when properties are MutableState-backed

### Pattern B: Composable Responsibility Extraction

**What:** Split composable functions by visual/functional responsibility.
**Confidence:** HIGH (standard Compose architecture)

Each extracted composable should:
1. Accept only the parameters it needs (not a giant state object)
2. Accept event callbacks as lambdas (`onSearch: (String) -> Unit`)
3. Be independently previewable with `@Preview`

```kotlin
// JustTypeSearchBar.kt - Only the search bar UI
@Composable
fun JustTypeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Search bar implementation
}

@Preview
@Composable
private fun JustTypeSearchBarPreview() {
    JustTypeSearchBar(query = "test", onQueryChange = {}, onSearch = {})
}
```

**Why individual parameters over state objects:** The official Android documentation explicitly recommends passing state and events as individual lambda parameters. This gives callers visibility into exactly what each composable needs, enables better skipping (a composable only recomposes when its specific parameters change), and makes composables independently testable and previewable.

### Pattern C: Utility/Helper Extraction

**What:** Extract non-composable helper functions, constants, and configuration.
**Confidence:** HIGH (standard software engineering)

For each mega-file, identify:
- Extension functions that do data transformation
- Constants and configuration objects
- Non-composable helper functions

These go into `*Utils.kt` or `*Config.kt` files. They have no Compose dependency and can be unit tested trivially.

---

## Recomposition Optimization Techniques

### Technique 1: Defer State Reads with Lambdas

**Confidence:** HIGH (official best practices)

Instead of reading state high in the tree and passing values down, pass lambda providers:

```kotlin
// BAD: Reads scroll position in parent, recomposes everything
@Composable
fun HomeScreen(scrollState: ScrollState) {
    val offset = scrollState.value  // Read here = recompose entire HomeScreen
    IconGrid(offset = offset)
}

// GOOD: Defer read to where it's consumed
@Composable
fun HomeScreen(scrollState: ScrollState) {
    IconGrid(offsetProvider = { scrollState.value })  // Read deferred
}

@Composable
fun IconGrid(offsetProvider: () -> Int) {
    Box(modifier = Modifier.offset { IntOffset(0, offsetProvider()) }) {
        // Only this Box recomposes when scroll changes
    }
}
```

**Relevance to Luna Launcher:** HomeScreen.kt has gesture state that changes frequently during drag/swipe operations. Deferring reads of gesture coordinates into lambda-based modifiers (`.offset { }`, `.graphicsLayer { }`) will prevent recomposition storms during gestures.

### Technique 2: derivedStateOf for Threshold-Based State

**Confidence:** HIGH (official best practices)

Use `derivedStateOf` when a boolean or enum is derived from rapidly-changing numeric state:

```kotlin
// BAD: Recomposes on every scroll pixel
val showJustType = scrollState.value > 100

// GOOD: Recomposes only when crossing the threshold
val showJustType by remember {
    derivedStateOf { scrollState.value > 100 }
}
```

**Relevance to Luna Launcher:** Gesture thresholds in HomeScreen (detecting when a drag becomes a swipe, when to show/hide search) are ideal candidates. The gesture state machine transitions (Idle/Pressed/LongPressArmed/Dragging) should use `derivedStateOf` if derived from continuous pointer input values.

### Technique 3: remember for Expensive Computations

**Confidence:** HIGH (official best practices)

```kotlin
// BAD: Filters on every recomposition
@Composable
fun JustTypeResultsList(results: List<SearchResult>, query: String) {
    val filtered = results.filter { it.matches(query) }  // Runs every recomposition
}

// GOOD: Only recomputes when inputs change
@Composable
fun JustTypeResultsList(results: List<SearchResult>, query: String) {
    val filtered = remember(results, query) {
        results.filter { it.matches(query) }
    }
}
```

**Relevance to Luna Launcher:** JustType.kt performs search result filtering and sorting. These computations should be wrapped in `remember` with appropriate keys. Better yet, move filtering to the ViewModel (which Luna Launcher already has) so it never runs during composition.

### Technique 4: LazyColumn/LazyRow Keys

**Confidence:** HIGH (official best practices)

```kotlin
// All search result lists and app lists must use stable keys
LazyColumn {
    items(
        items = searchResults,
        key = { result -> result.id }  // Stable unique identifier
    ) { result ->
        SearchResultRow(result)
    }
}
```

**Relevance to Luna Launcher:** The All Apps drawer, search results list, and widget picker list should all use stable item keys. Without keys, reordering items (e.g., search results updating) causes all visible items to recompose.

### Technique 5: Lambda-Based Modifiers for Animations/Gestures

**Confidence:** HIGH (official best practices)

```kotlin
// BAD: Recomposes on every animation frame
Box(Modifier.offset(x = animatedX.dp, y = animatedY.dp))

// GOOD: Defers to layout phase, skips composition entirely
Box(Modifier.offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) })

// GOOD: Defers to draw phase for visual-only changes
Box(Modifier.graphicsLayer { alpha = animatedAlpha })
```

**Relevance to Luna Launcher:** The home screen drag/rotate/swipe gestures update positions every frame. All position-related modifiers must use the lambda variants (`.offset { }`, `.graphicsLayer { }`) to bypass the composition phase entirely.

---

## Stability Fixes (Apply Only Where Compiler Reports Flag Issues)

### Stability Configuration File

If compiler reports show external types (from `core-model` module) as unstable in UI modules:

```
// stability_config.conf (project root)
// Mark core-model types as stable since they're immutable data classes
com.lunasysman.launcher.core.*
```

```kotlin
// build.gradle.kts
composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}
```

**When to use:** Only if compiler reports show `core-model` data classes as unstable in `ui-home` or `app` modules. Since `core-model` does not have the Compose compiler plugin, its types are inferred as unstable by default. The stability config file tells the Compose compiler to trust these types.

### Immutable Collections (kotlinx-collections-immutable)

**Do NOT add this dependency preemptively.** With strong skipping mode enabled, `List<T>` parameters no longer prevent skipping. Only add `kotlinx-collections-immutable` if:
1. Compiler reports show specific `List`/`Set`/`Map` parameters causing non-skippable composables
2. AND those composables show high recomposition counts in Layout Inspector
3. AND the recompositions cause measurable performance impact

If needed:
```kotlin
// build.gradle.kts
implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
```

---

## CompositionLocal Usage Guidelines

**Confidence:** HIGH (official documentation)

For this refactor, CompositionLocal should be used sparingly:

**Appropriate uses (already in the codebase via Material theme):**
- `LocalContext` for Android context access
- `MaterialTheme` color/typography/shapes

**Do NOT introduce CompositionLocal for:**
- Passing ViewModels or state holders to child composables
- Sharing gesture state between HomeScreen components
- Search provider configuration

**Why:** CompositionLocal creates implicit dependencies that make composables harder to reason about and test. For this refactor, explicit parameter passing is strongly preferred. The goal is to make each extracted file independently understandable -- implicit dependencies work against that goal.

**Performance note:** `compositionLocalOf` (dynamic) only recomposes readers when the value changes. `staticCompositionLocalOf` recomposes the entire provided subtree. Use `staticCompositionLocalOf` only for values that never change (like app-level config).

---

## File Organization: Feature-Prefixed Layer Structure

**Recommended approach:** Organize by feature first, then by layer within each feature.

For each of the four target files:

```
ui-home/
  src/main/kotlin/.../ui/home/
    # Feature: JustType search
    JustTypePanel.kt          # Root composable, orchestrates sub-composables
    JustTypeSearchBar.kt      # Search input composable
    JustTypeResultsList.kt    # Search results list composable
    JustTypeState.kt          # State holder class + remember factory
    JustTypeProviderConfig.kt # Provider configuration UI (if substantial)

    # Feature: HomeScreen
    HomeScreen.kt             # Root composable (renamed from original)
    HomeGestureHandling.kt    # Gesture detection + state machine integration
    HomeIconGrid.kt           # Icon grid rendering
    HomeState.kt              # Home screen state holder

    # Existing (unchanged)
    GestureState.kt           # Already extracted (175 lines)
    HomeGestures.kt           # Already extracted (135 lines)
    GestureThresholds.kt      # Already extracted

app/
  src/main/kotlin/.../
    # Feature: Launcher root
    LauncherActivity.kt       # Activity entry point only (onCreate, lifecycle)
    LauncherRootComposable.kt # Root Compose tree setup
    LauncherState.kt          # App-level state management
    LauncherPermissions.kt    # Permission request handling

    # Feature: Widget picker
    WidgetsPickerActivity.kt  # Activity entry point only
    WidgetsPickerComposable.kt # Widget picker UI composables
    WidgetsPickerState.kt     # Widget picker state holder
    WidgetBindingUtils.kt     # Widget binding helper functions
```

**Why feature-prefixed over package-per-feature:** The existing code is already in its correct module (`ui-home`, `app`). Creating sub-packages for 3-5 files per feature adds navigation friction without benefit. Feature-prefixed filenames in a flat structure allow IDE alphabetical sorting to naturally group related files.

**Target file sizes:** Aim for 150-400 lines per file. The 300-line target from CONCERNS.md is a guideline, not a hard rule. A 400-line composable with cohesive responsibility is better than an artificial split at 300.

---

## Side-Effect API Usage During Extraction

When extracting composables, correctly scope side-effects:

| Side-Effect | When to Use | Extraction Rule |
|-------------|-------------|-----------------|
| `LaunchedEffect(key)` | Suspend work tied to composable lifecycle | Keep in the composable that owns the trigger |
| `DisposableEffect(key)` | Register/unregister observers | Keep with the composable that manages the resource |
| `rememberCoroutineScope()` | Launch coroutines from event handlers | Can be created in any composable; prefer the one handling the event |
| `derivedStateOf` | Derived boolean/enum from rapidly changing state | Keep in the composable that reads the derived value |
| `snapshotFlow` | Convert Compose state to Flow | Keep in `LaunchedEffect` near the state source |

**Critical rule during extraction:** When moving a `LaunchedEffect` to a new file, ensure all its key parameters are still accessible. If a key comes from a parent scope, pass it as a parameter to the extracted composable.

---

## Validation Approach

### Before Starting (Baseline)

1. Enable Compose compiler reports in `app` and `ui-home` `build.gradle.kts`
2. Run release build to generate reports
3. Use Layout Inspector to record recomposition counts on:
   - Home screen idle state
   - Home screen during gesture (drag an icon)
   - JustType search with typing
   - Widget picker scrolling
4. Save baseline numbers

### After Each File Extraction

1. Regenerate compiler reports
2. Verify no composables lost skippability (compare `composables.txt`)
3. Re-profile with Layout Inspector
4. Verify recomposition counts are equal or better than baseline
5. Manual functional testing (all gestures, search, widget picker)

### Red Flags to Watch For

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Composable lost `skippable` status | Extracted function introduced unstable parameter | Check parameter types in compiler report |
| Recomposition count increased | State read moved higher in tree during extraction | Defer state read with lambda parameter |
| New composable recomposes on every frame | Missing `remember` on computed value | Wrap computation in `remember(keys)` |
| Gesture feels laggy after extraction | Position modifier not using lambda variant | Switch to `.offset { }` or `.graphicsLayer { }` |

---

## What NOT to Do

| Anti-Pattern | Why | Instead |
|-------------|-----|---------|
| Add `@Immutable` to every data class | Creates a contract you must maintain; if a property is later made mutable, compiler silently trusts the lie | Let strong skipping handle it; annotate only when compiler reports show a real problem |
| Wrap every `List` in an `@Immutable` wrapper | Unnecessary with strong skipping mode | Only wrap if profiling shows actual recomposition issues |
| Use `CompositionLocal` to avoid parameter drilling | Creates hidden dependencies, defeats the "independently understandable" goal | Pass parameters explicitly; 5-6 parameters is fine |
| Extract every 50-line composable into its own file | Creates navigation overhead, file explosion | Extract only when a composable has independent responsibility |
| Add `kotlinx-collections-immutable` without evidence | Adds a dependency for a problem that may not exist | Profile first, add only if needed |
| Move ViewModel logic into state holders | Breaks existing MVVM architecture | State holders contain UI state/logic only; business logic stays in ViewModels |

---

## Sources

All findings sourced from official Android developer documentation (HIGH confidence):

- Android Developers: Compose Performance Best Practices - https://developer.android.com/develop/ui/compose/performance/bestpractices
- Android Developers: Stability in Compose - https://developer.android.com/develop/ui/compose/performance/stability
- Android Developers: Diagnosing Stability Issues - https://developer.android.com/develop/ui/compose/performance/stability/diagnose
- Android Developers: Fixing Stability Issues - https://developer.android.com/develop/ui/compose/performance/stability/fix
- Android Developers: Strong Skipping Mode - https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Android Developers: State Hoisting - https://developer.android.com/develop/ui/compose/state-hoisting
- Android Developers: CompositionLocal - https://developer.android.com/develop/ui/compose/compositionlocal
- Android Developers: Side-Effects in Compose - https://developer.android.com/develop/ui/compose/side-effects
- Android Developers: Compose Performance Overview - https://developer.android.com/develop/ui/compose/performance

---

*Research completed: 2026-02-20*
