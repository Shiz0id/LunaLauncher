# Architecture Patterns: Compose File Refactoring Strategies

**Domain:** Large Compose file decomposition for Android launcher
**Researched:** 2026-02-20
**Overall confidence:** HIGH (based on direct codebase analysis + established Compose patterns)

---

## Organization Strategies Compared

### Strategy 1: Layer-Based with Feature Prefixes (RECOMMENDED)

Files are split by architectural layer (UI composable, state, utilities) but prefixed with the feature name for discoverability.

**Example for JustType:**
```
JustTypePanel.kt          -- Top-level orchestration composable
JustTypeSearchBar.kt      -- Search bar UI component
JustTypeResultsList.kt    -- Results rendering (sections, rows)
JustTypeState.kt          -- State holders, derivedStateOf, remember wrappers
JustTypeProviderConfig.kt -- Provider icon mappings, constants
```

**Tradeoffs:**
- (+) Clear layer boundaries prevent accidental coupling between UI and state
- (+) Feature prefix groups files naturally in IDE file tree (alphabetical)
- (+) Each file has a single responsibility axis (render vs compute vs configure)
- (+) Maps to how developers debug: "rendering bug" -> open UI file, "wrong data" -> open state file
- (-) Cross-layer imports within a feature are expected (state file imported by UI file)
- (-) Shared UI primitives (SectionCard, RowDivider) need a home -- either in the main feature file or a shared components file

**Verdict:** Best fit for this codebase. Already decided in PROJECT.md. This analysis validates that decision with concrete extraction plans.

### Strategy 2: Feature-Based (One File Per Feature Slice)

Each file contains all layers for a narrow feature slice.

**Example for JustType:**
```
JustTypeAppsSection.kt     -- Apps header + horizontal row + state
JustTypeNotifications.kt   -- Notification section + state
JustTypeActions.kt         -- Action section + state
JustTypeSearchBar.kt       -- Search bar + its state
```

**Tradeoffs:**
- (+) Maximum cohesion -- everything for "notifications section" is in one place
- (+) Easy to delete or replace an entire section
- (-) State management fragments across files, making it hard to reason about recomposition scope
- (-) Shared UI primitives (RowDivider, SectionCard) must be duplicated or put in a separate file anyway
- (-) For this codebase, state is already centralized in ViewModels via StateFlow -- slicing by feature would fight the existing architecture

**Verdict:** Worse fit. The existing MVVM pattern already centralizes state in ViewModels. Feature-sliced files would create artificial boundaries within what is fundamentally a single state tree.

### Strategy 3: Hybrid (Layer Primary, Feature Sub-Grouping)

Layer-based organization with subdirectories per feature.

**Example:**
```
home/
  justtype/
    JustTypePanel.kt
    JustTypeSearchBar.kt
    JustTypeResultsList.kt
    JustTypeState.kt
  homescreen/
    HomeScreenComposable.kt
    HomeGestureHandling.kt
    HomeIconGrid.kt
    HomeState.kt
```

**Tradeoffs:**
- (+) Scales well for very large codebases (50+ files in a package)
- (-) Overkill for 4 features producing ~16 files total
- (-) Adds directory navigation friction in IDE
- (-) Kotlin package declarations must change, which affects `internal` visibility

**Verdict:** Unnecessary complexity for this scope. The ui-home package will go from ~14 files to ~22 files. Flat organization with feature prefixes is sufficient and avoids package restructuring.

---

## State Extraction Patterns

### Pattern 1: State Parameters Stay at the Caller (Primary Pattern)

The current codebase already follows this correctly. State lives in ViewModels, is collected in `LauncherRoot`, and passed down as parameters.

**Current flow (preserve this):**
```
LauncherRoot (collects StateFlow)
  -> HomeScreen (receives state as params + callbacks)
    -> HomeCanvas (receives subset of params)
    -> SearchResultsPanel (receives JustTypeUiState)
```

**Rule:** Extracted files receive state as parameters. They do NOT call `collectAsStateWithLifecycle()` or access ViewModels directly. Only `LauncherRoot` touches ViewModels.

### Pattern 2: Local UI State via remember/rememberSaveable

For state that is purely UI-local (not persisted, not shared with other features), keep `remember` / `rememberSaveable` in the composable that owns it.

**Current examples in the codebase:**
```kotlin
// HomeScreen.kt - edit mode is local UI state
var editMode by rememberSaveable { mutableStateOf(false) }
var selectedIconId by rememberSaveable { mutableStateOf<String?>(null) }
```

**Extraction rule:** When extracting HomeScreen, these stay in the top-level `HomeScreen` composable (now `HomeScreenComposable.kt`), not in a separate state file. They are tightly coupled to the composable's lifecycle.

**When to extract to a state holder class:**
- When 3+ `remember` values interact with each other
- When the logic between them is non-trivial (e.g., state machine transitions)
- When the same state combination is needed by multiple child composables

**For this codebase:** None of the 4 target files have complex enough local state to warrant a state holder class. `editMode` + `selectedIconId` in HomeScreen is the most complex case with just 2 values. Keep them inline.

### Pattern 3: derivedStateOf for Computed Values

Use `derivedStateOf` when a value is computed from other state and read frequently.

**Current example:**
```kotlin
// HomeScreen.kt
val hasResults = searchOpen && justTypeState.sections.any { it.items.isNotEmpty() }
```

This is computed every recomposition but only used for conditional rendering. It could be wrapped in `remember { derivedStateOf { ... } }` but the cost is negligible here since it is only read once per recomposition. Leave it inline.

**When to use derivedStateOf:**
- Value is read in multiple places within the same composition
- Computation is non-trivial (list filtering, sorting)
- Input state changes more frequently than the derived value

**Current candidate in JustType.kt:**
```kotlin
val sections = remember(state) { state.sections.filter { it.items.isNotEmpty() } }
```
This is already correctly using `remember` with a key. After extraction to `JustTypeResultsList.kt`, keep it in place.

---

## Gesture Handler Extraction Patterns

The codebase has already established an excellent pattern for gesture extraction. Three gesture files already exist as models:

| File | Pattern | Lines |
|------|---------|-------|
| `HomeGestures.kt` | Extension function `Modifier.homeSurfaceGestures()` | 136 |
| `IconGesture.kt` | Extension function `Modifier.iconDrag()` + `IconDragState` data class | 139 |
| `CanvasRotationGesture.kt` | Extension function `Modifier.editModeCanvasGestures()` + `EditModeIconStates` class | 241 |

**Established gesture extraction pattern:**
1. Gesture logic goes in a `Modifier` extension function
2. Related state classes (`IconDragState`, `EditModeIconStates`) live in the same file as the gesture that uses them
3. All gesture handlers receive `GestureLock` and `GestureThresholds` as parameters
4. Gesture handlers are `internal` visibility

**Implication for refactoring:** The gestures are ALREADY extracted. The remaining work for `HomeScreen.kt` is not gesture extraction -- it is UI composable extraction (HomeCanvas, BottomAppBar, ChevronHandle, HomeEditBar, GestureLock class).

---

## Event Callback Extraction Patterns

### Problem: Callback Explosion at the Top Level

`HomeScreen` currently takes 25+ parameters, many of which are event callbacks. This is a known Compose pattern challenge.

**Current HomeScreen signature (simplified):**
```kotlin
fun HomeScreen(
    homeIcons: ...,
    onUpdateHomeIcon: ...,
    homeGridIconContent: ...,
    onHomeSlotClick: ...,
    onHomeSlotLongPress: ...,
    dockItems: ...,
    // ... 20+ more parameters
)
```

### Recommended approach: Keep callback parameters, do NOT wrap in event classes

**Rationale:**
- The current codebase uses direct lambda callbacks everywhere. Consistency matters more than theoretical elegance.
- Event sealed classes add indirection without improving debuggability for this project scale.
- Compose's compiler plugin optimizes lambda stability well when using method references (`vm::setSearchQuery`).
- The 25-parameter signature is a symptom of HomeScreen doing too much, which extraction fixes naturally.

**After extraction:**
- `HomeScreenComposable.kt` keeps the full parameter list (it is the orchestrator)
- `HomeIconGrid.kt` receives only icon-related params (5-6 params)
- `HomeDock.kt` receives only dock-related params (5-6 params)
- The parameter count per file drops to manageable levels

---

## Concrete Extraction Plan Per File

### REF-01: JustType.kt (801 lines -> 3 files)

**Current structure analysis:**
- `TopSearchBar` composable (lines 59-104, 46 lines) -- search bar UI
- `SearchResultsPanel` composable (lines 107-321, 215 lines) -- main results rendering
- `SectionCard`, `SectionHeader`, `RowDivider` (lines 323-359, 37 lines) -- shared primitives
- `BarRow`, `ContactBarRow`, `NotificationBarRow` (lines 362-582, 221 lines) -- row renderers
- `actionIconFor`, `searchIconFor` (lines 584-609, 26 lines) -- icon mapping utilities
- `SearchResultTile`, `ActionResultTile`, `SearchTemplateResultTile`, `DbRowResultTile` (lines 611-801, 191 lines) -- tile renderers

**Proposed split:**

| File | Contents | Est. Lines | Visibility |
|------|----------|------------|------------|
| `JustTypeSearchBar.kt` | `TopSearchBar` | ~50 | `internal` |
| `JustTypeResultsList.kt` | `SearchResultsPanel` + `SectionCard` + `SectionHeader` + `RowDivider` + all row/tile composables | ~500 | `internal` |
| `JustTypeIcons.kt` | `actionIconFor` + `searchIconFor` | ~30 | `private` -> `internal` |

**Notes:**
- The results list is ~500 lines but is highly cohesive (all rendering, no state management). Splitting it further would separate tightly-coupled renderers.
- Alternative: split further into `JustTypeRows.kt` (BarRow, ContactBarRow, NotificationBarRow) and `JustTypeTiles.kt` (tile composables) if the 500-line file feels too large.
- `actionIconFor`/`searchIconFor` are pure functions that map IDs to icons. They belong in a utility file.
- No state extraction needed -- JustType.kt contains zero state management (all state comes via parameters).

**Dependency order:** Can be done independently. No circular dependencies possible since all composables are leaf renderers.

### REF-02: WidgetsPickerActivity.kt (840 lines -> 4 files)

**Current structure analysis:**
- `WidgetsPickerActivity` class (lines 97-305, 209 lines) -- Activity with widget binding flow
- Data classes `WidgetGroup`, `WidgetDisplayInfo` (lines 311-324, 14 lines) -- models
- Utility functions `gridCells`, `widgetSpanCells` (lines 330-344, 15 lines) -- size calculation
- `WidgetsPickerContent` composable (lines 350-561, 212 lines) -- main UI
- `WidgetsPickerSearchBar` composable (lines 563-602, 40 lines) -- search bar
- `WidgetGroupHeader` composable (lines 604-686, 83 lines) -- group header
- `WidgetItemRow` composable (lines 688-754, 67 lines) -- widget item
- `rememberAppIcon`, `rememberWidgetPreview`, `loadPreviewImageLegacy` (lines 760-840, 81 lines) -- async image loading

**Proposed split:**

| File | Contents | Est. Lines | Visibility |
|------|----------|------------|------------|
| `WidgetsPickerActivity.kt` | Activity class + `beginAddWidget` + bind/configure flow + companion | ~220 | `public` (Activity) |
| `WidgetsPickerComposable.kt` | `WidgetsPickerContent` + `WidgetsPickerSearchBar` + `WidgetGroupHeader` + `WidgetItemRow` | ~410 | `internal` |
| `WidgetsPickerState.kt` | `WidgetGroup`, `WidgetDisplayInfo` data classes + `gridCells` + `widgetSpanCells` | ~40 | `internal` |
| `WidgetImageLoaders.kt` | `rememberAppIcon` + `rememberWidgetPreview` + `loadPreviewImageLegacy` | ~90 | `internal` |

**Key risk:** The Activity references composables and data classes. After extraction, `WidgetsPickerActivity.kt` must import from the new files. Since they are all in the same package (`com.lunasysman.launcher`), `internal` visibility works.

**Dependency order:** Extract `WidgetsPickerState.kt` first (data classes), then `WidgetImageLoaders.kt` (no deps on other new files), then `WidgetsPickerComposable.kt` (imports state + loaders), then clean up `WidgetsPickerActivity.kt`.

### REF-03: HomeScreen.kt (769 lines -> 3 files)

**Current structure analysis:**
- `GestureLock` class (lines 87-105, 19 lines) -- gesture coordination
- `HomeScreen` composable (lines 107-460, 354 lines) -- main orchestrator
- `HomeEditBar` composable (lines 462-515, 54 lines) -- edit mode toolbar
- `HomeCanvas` composable (lines 517-675, 159 lines) -- absolute-positioned icon grid
- `BottomAppBar` composable (lines 677-743, 67 lines) -- dock
- `ChevronHandle` composable (lines 745-769, 25 lines) -- all-apps button

**Proposed split:**

| File | Contents | Est. Lines | Visibility |
|------|----------|------------|------------|
| `HomeScreenComposable.kt` | `HomeScreen` (main orchestrator) + `GestureLock` class | ~380 | `public` (HomeScreen), `internal` (GestureLock) |
| `HomeIconGrid.kt` | `HomeCanvas` composable | ~170 | `internal` |
| `HomeDock.kt` | `BottomAppBar` + `ChevronHandle` + `HomeEditBar` | ~150 | `internal` |

**Key considerations:**
- `GestureLock` is used by HomeScreen, HomeGestures, IconGesture, and CanvasRotationGesture. It is currently defined in HomeScreen.kt. It should move to `HomeScreenComposable.kt` or its own file. Since it is only ~19 lines, keeping it in `HomeScreenComposable.kt` is pragmatic.
- `HomeCanvas` is the most complex sub-composable (icon positioning, drag state management, gesture modifier wiring). It is a natural extraction unit.
- `BottomAppBar`, `ChevronHandle`, and `HomeEditBar` are simple leaf composables that group naturally as "home chrome."
- The main `HomeScreen` composable is ~354 lines after extraction. This exceeds the 300-line target but the logic is sequential orchestration (search panel + canvas + dock + edit bar + gutter scrim). Splitting it further would scatter the layout logic.

**Dependency order:** Extract `HomeDock.kt` first (leaf composables, no complex state), then `HomeIconGrid.kt` (depends on gesture system files), then rename/clean `HomeScreenComposable.kt`.

### REF-04: LauncherActivity.kt (797 lines -> 4 files)

**Current structure analysis:**
- `MenuSource` enum + `MenuTarget` data class (lines 84-93, 10 lines) -- menu state
- `slotIndexForDrop` utility (lines 95-108, 14 lines) -- grid position calculation
- `LauncherActivity` class (lines 110-145, 36 lines) -- Activity entry point
- `Intent.toDebugString()` extension (lines 147-154, 8 lines) -- debug utility
- `LauncherRoot` composable (lines 156-761, 606 lines) -- THE REAL PROBLEM
- `LaunchPointIcon` composable (lines 764-797, 34 lines) -- icon renderer

**LauncherRoot is 606 lines and is the wiring hub of the entire app.** It contains:
- ViewModel creation and state collection (lines 161-304, ~144 lines)
- Deck lifecycle management (lines 186-264, ~79 lines)
- Theme/permission state (lines 266-296, ~31 lines)
- Drag-and-drop state + handlers (lines 333-340, ~8 lines declaration)
- Event handling functions (lines 345-459, ~115 lines)
- HomeScreen call with 25+ params (lines 461-503, ~43 lines)
- WidgetDeckOverlay call (lines 506-548, ~43 lines)
- Reply dialog (lines 550-586, ~37 lines)
- AllAppsScreen call (lines 588-680, ~93 lines)
- Drag ghost rendering (lines 682-697, ~16 lines)
- Notification dialog (lines 700-710, ~11 lines)
- Menu sheet (lines 712-759, ~48 lines)

**Proposed split:**

| File | Contents | Est. Lines | Visibility |
|------|----------|------------|------------|
| `LauncherActivity.kt` | Activity class + `Intent.toDebugString()` | ~50 | `public` |
| `LauncherRootComposable.kt` | `LauncherRoot` composable (slimmed) | ~400 | `private` -> `internal` |
| `LauncherState.kt` | `MenuSource`, `MenuTarget`, `slotIndexForDrop`, helper functions | ~80 | `internal` |
| `LauncherPermissions.kt` | Notification permission dialog wiring + contacts permission handling | ~60 | `internal` |
| `LaunchPointIcon.kt` | `LaunchPointIcon` composable | ~40 | `internal` |

**Hard truth:** `LauncherRoot` will remain the largest file (~400 lines) because it is the composition root. It wires ViewModels to UI. Extracting it further requires either:
1. Creating artificial intermediate composables that just pass parameters through (adds indirection without value)
2. Moving to a navigation/coordinator pattern (architectural change, out of scope)

The 400-line target is acceptable because `LauncherRoot` is sequential wiring code -- easy to navigate with IDE folding, and each section is independent.

**Dependency order:** Extract `LauncherState.kt` first (data classes + utility), then `LaunchPointIcon.kt` (leaf composable), then `LauncherPermissions.kt`, then reorganize remaining into `LauncherRootComposable.kt` and `LauncherActivity.kt`.

---

## Recommended Refactor Order

```
Phase 1: REF-01 JustType.kt
  |  Why first: Simplest extraction (pure UI, no state management, no gestures)
  |  Risk: LOW (all composables are leaf renderers with no side effects)
  |  Validation: Search results render identically
  v
Phase 2: REF-02 WidgetsPickerActivity.kt
  |  Why second: Self-contained Activity, does not affect main launcher flow
  |  Risk: LOW (isolated Activity with clear boundaries)
  |  Validation: Widget picker opens, selects, binds, returns result
  v
Phase 3: REF-03 HomeScreen.kt
  |  Why third: Gesture system is already extracted, but HomeCanvas is complex
  |  Risk: MEDIUM (GestureLock visibility change, HomeCanvas icon state wiring)
  |  Validation: Icon drag, rotation, edit mode, dock, swipe gestures all work
  v
Phase 4: REF-04 LauncherActivity.kt
  |  Why last: Composition root, touches everything, highest blast radius
  |  Risk: MEDIUM (wiring hub, easy to break import chains)
  |  Validation: Full launcher flow: launch, search, all apps, deck, menu, permissions
```

**Rationale for this order:**
1. **Increasing complexity:** JustType is pure rendering. WidgetsPicker has Activity lifecycle. HomeScreen has gesture coordination. LauncherActivity has everything.
2. **Increasing blast radius:** JustType failure affects search display only. LauncherActivity failure affects the entire app.
3. **Each phase builds confidence:** Successful JustType extraction proves the pattern works before tackling harder files.

---

## Dependency Map

```
LauncherActivity.kt
  imports from: HomeScreen.kt, JustType.kt (indirectly via HomeScreen),
                WidgetsPickerActivity.kt, AllAppsScreen.kt, WidgetDeckOverlay.kt

HomeScreen.kt
  imports from: JustType.kt (TopSearchBar, SearchResultsPanel),
                HomeGestures.kt, IconGesture.kt, CanvasRotationGesture.kt,
                GestureThresholds.kt, GestureState.kt, PointerCompat.kt

JustType.kt
  imports from: GlassSurface.kt, LauncherTheme.kt (theme only)
  imported by: HomeScreen.kt

WidgetsPickerActivity.kt
  imports from: GlassSurface.kt, LauncherTheme.kt
  imported by: LauncherActivity.kt (via Intent)
```

**No circular dependencies exist.** Extraction maintains the existing DAG.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Extracting State Into Separate State Holder Classes Prematurely

**What:** Creating `HomeScreenState`, `JustTypeState` wrapper classes to group remember values.
**Why bad:** The codebase uses ViewModel StateFlows for shared state and inline `remember` for UI-local state. Adding an intermediate state holder layer creates a third state management pattern without solving a real problem.
**Instead:** Keep `remember`/`rememberSaveable` inline in the composable that owns the state. Only extract if 4+ related state values interact.

### Anti-Pattern 2: Creating Overly Granular Files

**What:** Splitting every composable into its own file (e.g., `SectionHeader.kt` with 8 lines).
**Why bad:** File overhead (imports, package declarations) exceeds content. IDE navigation becomes harder with 30+ files.
**Instead:** Group related leaf composables. `RowDivider`, `SectionHeader`, and `SectionCard` belong together because they are always used together.

### Anti-Pattern 3: Changing Visibility During Extraction

**What:** Making previously `private` composables `public` during extraction.
**Why bad:** Expands the API surface unnecessarily. Other modules might start depending on internal implementation details.
**Instead:** Use `internal` for all extracted composables (visible within the module but not to other modules). The only `public` composable should be the top-level entry point (`HomeScreen`, `AllAppsScreen`).

### Anti-Pattern 4: Refactoring Signatures During Extraction

**What:** "While we're moving this, let's also clean up the parameter names" or "let's combine these two callbacks."
**Why bad:** Makes it impossible to verify the refactor is behavior-preserving. Any signature change is a potential regression.
**Instead:** Move code with ZERO changes first. Clean up signatures in a separate, subsequent commit.

---

## Recomposition Safety During Extraction

### Key Rule: Do Not Change Composable Boundaries

Extracting a composable to a new file does NOT change recomposition behavior as long as:
1. The function signature is identical
2. The function is still marked `@Composable`
3. Parameters are passed in the same order with the same stability

**What could go wrong:**
- Adding a wrapper composable that passes all params through creates an additional recomposition scope. This is usually harmless but could affect `remember` keying.
- Changing `private` to `internal` does not affect recomposition.
- Moving a `remember` block to a different composable scope changes its lifecycle.

**Validation approach:**
- Use Layout Inspector to verify composable tree structure is identical before and after extraction
- Check that `remember` and `rememberSaveable` blocks are in the same composable scope after extraction
- Run manual gesture tests (the most complex recomposition-sensitive code path)

---

## Sources

- Direct codebase analysis of all 4 target files (primary source, HIGH confidence)
- Existing extracted gesture files as proven pattern models (HIGH confidence)
- Jetpack Compose documentation on state hoisting and side effects (training data, MEDIUM confidence -- stable API, unlikely to have changed)
- Android developer guidance on Compose performance and recomposition (training data, MEDIUM confidence)

---

*Architecture research: 2026-02-20*
