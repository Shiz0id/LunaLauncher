# Project Research Summary

**Project:** Luna Launcher - Compose File Refactoring
**Domain:** Jetpack Compose Code Organization & Performance Optimization
**Researched:** 2026-02-20
**Confidence:** HIGH

## Executive Summary

Luna Launcher requires refactoring four 800+ line Compose files (`JustType.kt`, `HomeScreen.kt`, `LauncherActivity.kt`, `WidgetsPickerActivity.kt`) to address debuggability issues, IDE performance degradation, and recomposition storms. The codebase runs Kotlin 2.0.21 with Compose BOM 2024.12.01, which means **strong skipping mode is already enabled by default** — the most impactful recomposition optimization available. This significantly reduces the urgency of manual stability fixes.

The recommended approach is **layer-based extraction with feature prefixes**: split files by architectural layer (UI composable, state, utilities) while using feature names as prefixes for discoverability. Each file should have a single responsibility, target 150-400 lines, and maintain the existing MVVM architecture with manual DI. The refactor must be purely structural with zero behavior changes — preserving all gestures, animations, state management, and visual output.

Critical risks center on breaking closure captures in `LauncherRoot`, disrupting gesture coordination via `GestureLock` shared state, and losing `rememberSaveable` state across configuration changes. Mitigation requires extracting in dependency order (simple leaf composables first), using explicit `rememberSaveable` keys before restructuring, and comprehensive manual testing after each extraction. The four-phase refactor order (JustType → WidgetsPicker → HomeScreen → LauncherActivity) follows increasing complexity and blast radius.

## Key Findings

### Recommended Stack

Luna Launcher already has the optimal technology stack in place. The focus is on using existing tooling correctly.

**Core technologies:**
- **Kotlin 2.0.21 with Strong Skipping Mode** — Already enabled by default (shipped in 2.0.20), provides automatic skipping for all restartable composables even with unstable parameters. This eliminates most recomposition issues that plague older Compose versions.
- **Compose Compiler Reports** — Enable via `composeCompiler { reportsDestination = ... }` to generate baseline recomposition metrics before extraction and validate no regression after. Focus on composables marked `restartable` but NOT `skippable`.
- **Layout Inspector with Recomposition Counting** — Use to profile actual runtime recomposition behavior. High recompose counts with low skip counts indicate real problems; high recompose with high skip counts means the system is working correctly.
- **State Hoisting with ViewModels** — Already in place. State flows down from ViewModels via StateFlow, collected in `LauncherRoot`, passed down as parameters. Maintain this pattern during extraction.

**Critical technique stack:**
- `derivedStateOf` for threshold-based state (gesture transitions, scroll thresholds)
- Lambda-based modifiers (`.offset { }`, `.graphicsLayer { }`) for animation/gesture state that changes every frame
- Stable item keys in all `LazyColumn`/`LazyRow` instances
- `@Stable` annotations on state holder classes (existing `GestureLock` is correctly annotated)

### Expected Features

Well-organized Compose code must meet these quality standards to succeed.

**Must have (table stakes):**
- **Single Responsibility per File** — Each file has one clear purpose describable in one sentence. Files like `GestureState.kt` (175 lines, one sealed interface) exemplify this. Current mega-files violate it.
- **State Hoisting to Callers** — State lives in ViewModels or parent composables, flows down as parameters. Events flow up as lambdas. No composable both reads and writes shared mutable state.
- **Explicit Visibility Modifiers** — Use `internal` for module-shared composables, `private` for helpers, `public` only for cross-module APIs.
- **Recomposition Scope Isolation** — Each composable function is a recomposition boundary. Separate composables for search bar vs results list prevents query changes from recomposing the icon grid.
- **Minimal Parameter Lists** — Keep under 10 parameters. Group related params into state holder data classes if needed.

**Should have (competitive quality):**
- **`@Preview` Functions** — Each extracted visual composable should have at least one preview for visual verification without running the app.
- **Dedicated State Holder Classes** — For 3+ related UI state values that interact. Example: `GestureState.kt` already follows this pattern.
- **KDoc on Internal Composables** — Brief doc comment explaining what the composable renders (following `GestureState.kt` example).
- **`@Stable`/`@Immutable` Annotations** — On state holder classes and data classes passed to composables.
- **Modifier as First Optional Parameter** — Every rendering composable accepts `modifier: Modifier = Modifier`.

**Defer (anti-features to avoid):**
- **God Files (800+ lines)** — Current problem. Creates IDE lag, recomposition storms, impossible reviews.
- **Splitting by Line Count Alone** — A 400-line file with single responsibility beats two 200-line coupled files. Split by responsibility boundary.
- **State Duplication Across Files** — Each state value owned in exactly one location.
- **Passing ViewModel to Composables** — Pass data + lambdas instead. Current codebase already does this at HomeScreen level.
- **Premature Abstraction** — Keep concrete during refactor. Abstract only with proven duplication.

### Architecture Approach

The existing architecture is sound; the refactor preserves it while improving file organization.

**Established pattern (maintain this):**
```
ViewModels (StateFlow)
  → LauncherRoot (collect state, create callbacks)
    → Feature Screens (receive state as params, events as lambdas)
      → Sub-composables (receive subset of params)
```

**Major components after refactoring:**

1. **JustType Search System** (801 lines → 3 files)
   - `JustTypeSearchBar.kt` (~50 lines) — Search input UI
   - `JustTypeResultsList.kt` (~500 lines) — Results rendering with all row/tile composables
   - `JustTypeIcons.kt` (~30 lines) — Icon mapping utilities

2. **Home Screen System** (769 lines → 3 files)
   - `HomeScreenComposable.kt` (~380 lines) — Main orchestrator + `GestureLock` class
   - `HomeIconGrid.kt` (~170 lines) — `HomeCanvas` with absolute positioning
   - `HomeDock.kt` (~150 lines) — Bottom bar, chevron, edit bar

3. **Widget Picker System** (840 lines → 4 files)
   - `WidgetsPickerActivity.kt` (~220 lines) — Activity lifecycle only
   - `WidgetsPickerComposable.kt` (~410 lines) — All picker UI composables
   - `WidgetsPickerState.kt` (~40 lines) — Data classes and grid calculations
   - `WidgetImageLoaders.kt` (~90 lines) — Async image loading composables

4. **Launcher Root System** (797 lines → 4 files)
   - `LauncherActivity.kt` (~50 lines) — Activity entry point
   - `LauncherRootComposable.kt` (~400 lines) — ViewModel wiring hub (will remain largest file)
   - `LauncherState.kt` (~80 lines) — Menu state, utility functions
   - `LauncherPermissions.kt` (~60 lines) — Permission dialog coordination
   - `LaunchPointIcon.kt` (~40 lines) — Icon renderer composable

**Key architectural principles:**
- **Layer-based with feature prefixes** — Files named `[Feature][Layer].kt` enable alphabetical grouping in IDE
- **Flat package structure** — No subdirectories. Feature prefixes provide natural organization for ~22 total files.
- **Dependency flow** — State ← UI ← Utils. No circular dependencies possible.
- **Gesture extraction already complete** — `HomeGestures.kt`, `IconGesture.kt`, `CanvasRotationGesture.kt` established the pattern: gesture logic in `Modifier` extension functions, state classes co-located.

### Critical Pitfalls

Top pitfalls that could cause runtime failures or silent behavior changes:

1. **Breaking Closure Captures When Extracting Functions**
   - **Risk:** `LauncherRoot()` defines local functions (`openSearch()`, `dismissSearch()`, `launchById()`) that close over multiple `var` state variables. Extracting to separate file breaks closures.
   - **Prevention:** Use state holder class (`LauncherRootState`) wrapping all mutable state. Pass as single object. Do NOT pass `Boolean` values — pass `MutableState<Boolean>` so writes propagate.
   - **Detection:** Verify each state mutation site after extraction. Search for all writes to `searchOpen`, `deckSearchOpen`, `menuTarget`, `dragPayload` and confirm they mutate the same backing instance.

2. **GestureLock Shared State Coordination**
   - **Risk:** `GestureLock` is instantiated once via `remember { GestureLock() }` in HomeScreen and shared across all gesture handlers. Creating second instance causes gesture conflicts (icon drag and surface swipe fire simultaneously).
   - **Prevention:** Extract `GestureLock` class first. Ensure single `remember { GestureLock() }` stays in parent composable. Never create instance in child composable.
   - **Detection:** Grep for `GestureLock()` constructor calls. Must be exactly one inside a `remember` block.

3. **rememberSaveable State Restoration Failures**
   - **Risk:** Moving `rememberSaveable` calls to different composables changes their composition tree position, generating different keys. Saved state from before refactor silently lost on rotation/process death.
   - **Prevention:** Add explicit keys BEFORE refactor: `rememberSaveable(key = "searchOpen") { ... }`. Ship this first, then do file split.
   - **Detection:** Test process death: `adb shell am kill com.lunasysman.launcher` while backgrounded, resume, verify all state restored.

4. **LaunchedEffect and DisposableEffect Key Stability**
   - **Risk:** Moving effects to child composables changes when they trigger. `LaunchedEffect(Unit)` in newly created composable runs when child enters composition, not when parent originally ran it.
   - **Prevention:** Keep `LaunchedEffect(Unit)` blocks at same composition level. For `DisposableEffect`, verify `onDispose` runs at correct time.
   - **Detection:** Add temporary logging to each effect. Verify they fire at exactly same times as before refactor.

5. **BackHandler Ordering Changes**
   - **Risk:** `LauncherRoot()` has five `BackHandler` blocks in specific order. Last enabled handler wins. Reordering during refactor silently changes back button behavior.
   - **Prevention:** Keep all `BackHandler` declarations in same composable in same order. Document required composition order if distributed.
   - **Detection:** Manual test matrix: search open → back, deck open → back, deck search → back, all apps → back, dragging → back.

## Implications for Roadmap

Based on research, the refactor should proceed in four phases ordered by increasing complexity and risk.

### Phase 1: JustType Search System (REF-01)
**Rationale:** Simplest extraction — pure UI rendering with no state management, no gesture coordination, no lifecycle effects. All composables are leaf renderers with clear boundaries. Lowest risk entry point that validates the extraction pattern before tackling harder files.

**Delivers:**
- `JustTypeSearchBar.kt` (search input UI)
- `JustTypeResultsList.kt` (results rendering)
- `JustTypeIcons.kt` (icon mapping utilities)

**Addresses:**
- Single responsibility per file (each extracted file has one clear purpose)
- Recomposition scope isolation (search bar and results list are separate scopes)
- Debuggability (search bugs route to specific file by name)

**Avoids:**
- Low risk phase — no closure captures, no shared mutable state, no gesture coordination
- Verify `internal` visibility maintained
- Standard testing: search results render identically

### Phase 2: Widget Picker System (REF-02)
**Rationale:** Self-contained Activity with clear boundary between Activity lifecycle code and Compose UI code. Does not affect main launcher flow. Medium complexity due to widget binding state machine but lower blast radius than home screen.

**Delivers:**
- `WidgetsPickerActivity.kt` (Activity lifecycle only)
- `WidgetsPickerComposable.kt` (picker UI)
- `WidgetsPickerState.kt` (data classes)
- `WidgetImageLoaders.kt` (async image loading)

**Addresses:**
- Layer separation (Activity vs Compose UI)
- File size reduction (840 → ~200 lines per file)
- Independent testing (widget picker isolated from launcher)

**Avoids:**
- Medium risk: `pendingAppWidgetId` and `pendingProviderInfo` state coordinates across `onActivityResult`
- Extract composables first, leave activity state machine in Activity class
- Testing: widget picker opens, selects, binds, returns result

### Phase 3: Home Screen System (REF-03)
**Rationale:** Gesture system is already extracted (`HomeGestures.kt`, `IconGesture.kt`), so remaining work is UI composable extraction. Higher complexity due to `GestureLock` coordination and `EditModeIconStates` registry. Must extract in dependency order.

**Delivers:**
- `HomeScreenComposable.kt` (main orchestrator + `GestureLock`)
- `HomeIconGrid.kt` (`HomeCanvas` composable)
- `HomeDock.kt` (bottom bar, chevron, edit bar)

**Uses:**
- Existing gesture system (`HomeGestures.kt`, `IconGesture.kt`, `CanvasRotationGesture.kt`)
- State hoisting pattern (parent creates `GestureLock`, children receive as param)

**Avoids:**
- **Pitfall 3:** `GestureLock` shared state breaks
- **Pitfall 10:** `EditModeIconStates` registry leaks
- **Pitfall 11:** Losing `key(lp.id)` block during extraction
- Extract order: `HomeDock.kt` first (leaf composables), then `HomeIconGrid.kt`, then clean up `HomeScreenComposable.kt`
- Testing: icon drag, rotation, edit mode, dock, swipe gestures all work

### Phase 4: Launcher Root System (REF-04)
**Rationale:** Composition root that wires all ViewModels to UI. Highest complexity (15+ state variables, 5 `BackHandler` blocks, lifecycle effects, deeply nested lambdas) and highest blast radius (affects entire app). Must be last because breaking it breaks everything.

**Delivers:**
- `LauncherActivity.kt` (Activity entry point only)
- `LauncherRootComposable.kt` (ViewModel wiring hub, ~400 lines)
- `LauncherState.kt` (menu state, utility functions)
- `LauncherPermissions.kt` (permission dialog coordination)
- `LaunchPointIcon.kt` (icon renderer)

**Addresses:**
- Closure capture pitfalls via state holder class
- `BackHandler` ordering preservation
- Side effect lifecycle correctness

**Avoids:**
- **Pitfall 1:** Breaking closure captures (highest risk)
- **Pitfall 4:** `LaunchedEffect`/`DisposableEffect` key stability
- **Pitfall 5:** `BackHandler` ordering
- **Pitfall 9:** `SideEffect` timing for window blur
- Extract order: `LauncherState.kt` → `LaunchPointIcon.kt` → `LauncherPermissions.kt` → split remaining code
- Testing: full launcher flow (launch, search, all apps, deck, menu, permissions)

### Phase Ordering Rationale

- **Increasing complexity:** JustType (pure rendering) → WidgetsPicker (Activity lifecycle) → HomeScreen (gesture coordination) → LauncherActivity (everything)
- **Increasing blast radius:** JustType failure affects search display only. LauncherActivity failure affects entire app.
- **Confidence building:** Successful simple extraction proves pattern works before tackling harder files.
- **Dependency order:** No phase depends on completion of later phases. Each is independently valuable.

### Research Flags

**Phases with standard patterns (skip research-phase):**
- **All four phases** — This is a refactoring project, not new feature development. All patterns are established in existing codebase or official Android documentation. No additional research needed during planning.

**Validation approach:**
- Generate Compose compiler reports before starting (baseline)
- Use Layout Inspector to record recomposition counts (baseline)
- After each extraction: regenerate reports, re-profile, manual functional testing
- Track red flags: composables losing `skippable` status, recomposition count increases, gesture lag

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Direct analysis of project's `build.gradle.kts` files. Kotlin 2.0.21 and Compose BOM versions verified. Strong skipping mode documented in official Android docs. |
| Features | HIGH | Sourced from direct codebase analysis. Existing well-organized files (`GestureState.kt`, `GestureThresholds.kt`) vs problem files (800+ lines) provide clear before/after examples. Patterns verified against official Android Compose documentation. |
| Architecture | HIGH | Complete analysis of all four target files (line-by-line structure mapping). Existing gesture extraction pattern (`HomeGestures.kt`, `IconGesture.kt`) provides proven blueprint. Dependency graph mapped with no circular dependencies. |
| Pitfalls | HIGH | All pitfalls derived from direct code analysis of closure captures, shared state patterns, and effect lifecycles in actual project files. Not theoretical — every pitfall is based on real coupling in the codebase. |

**Overall confidence:** HIGH

This is an unusual case where confidence is HIGH across all dimensions because:
1. All research is based on direct codebase analysis (not external sources)
2. The project already contains both good examples (to emulate) and bad examples (to fix)
3. The technology stack is stable and well-documented
4. The refactor is structural only — no new feature domains to research

### Gaps to Address

**No significant gaps identified.** The research is complete for structural refactoring needs.

**Minor refinements during execution:**
- Exact line counts after extraction may vary by ±50 lines from estimates
- `LauncherRootComposable.kt` will remain ~400 lines (largest file) because it is the wiring hub — this is acceptable
- If `JustTypeResultsList.kt` at ~500 lines feels too large, can optionally split further into `JustTypeRows.kt` and `JustTypeTiles.kt` — decision deferred to execution
- State holder classes (`LauncherRootState`, `HomeState`) may or may not be needed depending on parameter count after extraction — add only if natural

**Validation checkpoints:**
- Enable explicit `rememberSaveable` keys in pre-refactor commit (prevent state loss)
- Baseline compiler reports and Layout Inspector metrics before first extraction
- Process death testing after each phase
- Full gesture testing after HomeScreen and LauncherActivity phases

## Sources

### Primary (HIGH confidence)
- **Direct codebase analysis** — Line-by-line analysis of `JustType.kt` (801 lines), `HomeScreen.kt` (769 lines), `LauncherActivity.kt` (797 lines), `WidgetsPickerActivity.kt` (840 lines), plus all related gesture and state files
- **Existing well-organized files** — `GestureState.kt` (175 lines), `GestureThresholds.kt` (103 lines), `HomeGestures.kt` (135 lines), `LaunchPointTile.kt` (35 lines) provide proven patterns
- **Project documentation** — PROJECT.md (core values, constraints), CONCERNS.md (performance bottlenecks), CLAUDE.md (architecture patterns)
- **Build configuration** — Verified Kotlin 2.0.21 + Compose BOM 2024.12.01 from actual `build.gradle.kts` files

### Secondary (HIGH confidence from official sources)
- Android Developers: Compose Performance Best Practices — https://developer.android.com/develop/ui/compose/performance/bestpractices
- Android Developers: Stability in Compose — https://developer.android.com/develop/ui/compose/performance/stability
- Android Developers: Strong Skipping Mode — https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Android Developers: State Hoisting — https://developer.android.com/develop/ui/compose/state-hoisting
- Android Developers: Side-Effects in Compose — https://developer.android.com/develop/ui/compose/side-effects
- JetBrains Kotlin style guide — File organization, naming conventions

---
*Research completed: 2026-02-20*
*Ready for roadmap: yes*
