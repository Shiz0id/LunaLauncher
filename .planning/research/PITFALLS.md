# Domain Pitfalls

**Domain:** Compose file refactoring (splitting large files without behavior changes)
**Project:** Luna Launcher
**Researched:** 2026-02-20

## Critical Pitfalls

Mistakes that cause runtime crashes, silent behavior changes, or recomposition regressions. These are the highest priority to guard against because the project constraint is **zero behavior changes**.

---

### Pitfall 1: Breaking Closure Captures When Extracting Functions

**What goes wrong:** `LauncherRoot()` (797 lines) defines local functions like `openSearch()`, `dismissSearch()`, `launchById()`, and `showNotificationResult()` that close over multiple `var` state variables (`searchOpen`, `deckSearchOpen`, `deckSearchQuery`, `menuTarget`, `dragPayload`, `dragPosition`) and objects (`vm`, `scope`, `notificationExecutor`, `context`). When these functions are extracted into a separate file, the closures break because the closed-over variables are no longer in lexical scope.

**Why it happens:** In the current code, `LauncherRoot()` uses Kotlin local functions (e.g., `fun openSearch()` at line 345, `fun dismissSearch()` at line 351, `fun launchById(id: String)` at line 407) that directly read and write `var` state defined in the same composable scope. Moving these to another file forces you to either pass every state variable as a parameter or restructure into a state holder class. Both approaches can subtly change behavior if done incorrectly.

**Consequences:**
- Compilation errors are the best case (easy to fix)
- Worst case: passing state by value instead of by reference, causing writes to go to a stale copy
- Lambda callbacks like `handleSearchItemClick` (line 425) and `handleNotificationActionClick` (line 448) call `dismissSearch()` and `launchById()` internally -- if these are reconnected incorrectly, search dismissal or app launching silently fails

**Prevention:**
- Before extracting, inventory every `var` that each function reads and writes
- Use a state holder class (`LauncherRootState`) that wraps all mutable state, pass it as a single object
- Keep lambdas that reference multiple state variables defined in the same scope as the state they reference
- Do NOT pass `Boolean` state values to extracted functions -- pass the `MutableState<Boolean>` or state holder so writes propagate

**Detection:** After extraction, verify each state mutation site by searching for all writes to `searchOpen`, `deckSearchOpen`, `deckOpen`, `allAppsOpen`, `menuTarget`, `dragPayload`, `dragPosition` and confirming they still mutate the same backing state instance.

---

### Pitfall 2: Visibility Modifier Changes Break Internal Compose Contracts

**What goes wrong:** Several composables in `HomeScreen.kt` are marked `private` (e.g., `HomeEditBar`, `HomeCanvas`, `BottomAppBar`, `ChevronHandle`). Moving them to separate files requires changing visibility to `internal` or `public`. If the extracted composable is in a different module, it must be `public`, which changes the API surface. More critically, composables marked `internal` in `JustType.kt` (e.g., `TopSearchBar`, `SearchResultsPanel`) are called from `HomeScreen.kt` in the same module (`ui-home`). If the refactor moves callers to a different module, `internal` visibility breaks compilation.

**Why it happens:** The current code relies on same-file or same-module visibility scoping. File splitting within the same module is safe for `internal`, but the refactor plan places some files in different packages or potentially different source sets.

**Consequences:**
- Compilation failures (best case)
- Accidentally exposing APIs as `public` that should remain module-internal, creating future maintenance burden
- Breaking the project constraint: "UI modules don't depend on each other" -- if an extracted file from `ui-home` needs to be consumed by `:app`, the dependency direction may need to change

**Prevention:**
- Keep all split files within the same module and package
- Use `internal` for all extracted composables (never `public` unless the composable was already public)
- REF-01 through REF-03 (JustType, HomeScreen, WidgetsPickerActivity) must keep extracted files in their original modules
- REF-04 (LauncherActivity) files must stay in `:app`

**Detection:** Run a full build after each file extraction. Verify no new `public` composables appear in the module's API surface.

---

### Pitfall 3: GestureLock Shared State Breaks When Separated From Its Consumers

**What goes wrong:** `HomeScreen.kt` defines `GestureLock` (lines 87-105) as an `@Stable` class with `mutableStateOf` backing. It is instantiated via `remember { GestureLock() }` in `HomeScreen()` and passed to `HomeCanvas`, `homeSurfaceGestureModifier`, and the edit-mode gesture system. If `GestureLock` is extracted to a separate file but its instance creation stays in `HomeScreen`, and consumers are also extracted, the shared reference must be threaded correctly through all extracted composables. A single missed parameter causes gesture coordination to silently fail -- two gesture handlers could process simultaneously, causing icon drags to fight with surface swipes.

**Why it happens:** `GestureLock` uses Compose's snapshot state (`mutableStateOf`) which is thread-safe but reference-sensitive. Creating a second instance instead of sharing one is a common copy-paste error during extraction.

**Consequences:**
- Gesture conflicts: icon drag and surface swipe activate simultaneously
- Edit-mode rotation and single-icon drag interfere with each other
- These bugs only manifest through manual interaction testing, not compilation

**Prevention:**
- Extract `GestureLock` to `HomeGestureHandling.kt` or its own file first, before touching its consumers
- Ensure the single `remember { GestureLock() }` call stays in the parent composable that passes it down
- Never create a `GestureLock()` inside a child composable -- always receive it as a parameter
- Add a comment at the instantiation site: "Single instance shared across all gesture handlers"

**Detection:** After refactor, grep for `GestureLock()` constructor calls. There must be exactly one (inside a `remember` block in `HomeScreen` or equivalent).

---

### Pitfall 4: rememberSaveable State Restoration Fails Across File Boundaries

**What goes wrong:** `HomeScreen.kt` uses `rememberSaveable` for `editMode` and `selectedIconId` (lines 154-155). `LauncherActivity.kt` uses `rememberSaveable` for `searchOpen`, `deckOpen`, `deckSearchOpen`, `deckSearchQuery`, `allAppsOpen`, `allAppsInitialTab`, `themeStyleName`, `colorThemeName`, `homeTintStrength`, `showNotificationPermissionDialog`, `pendingReplyText` (lines 186-331). If these are moved to extracted composables that change the composition tree structure, the `rememberSaveable` keys change, and saved state from before the refactor is silently lost on configuration change (rotation, process death).

**Why it happens:** `rememberSaveable` keys are derived from the composable's position in the composition tree. Moving a `rememberSaveable` call from one composable into another changes its tree position, generating a different key. After the refactor, the old saved state cannot be restored.

**Consequences:**
- On screen rotation or process death: search closes unexpectedly, edit mode resets, theme reverts to default, deck state lost
- Users experience the app "forgetting" their state after Android kills the process in the background
- This is invisible during normal development testing -- only manifests under process death

**Prevention:**
- Use explicit `key` parameters for all `rememberSaveable` calls: `rememberSaveable(key = "searchOpen") { mutableStateOf(false) }`
- Add explicit keys BEFORE the refactor, ship it, then do the file split in a second step -- this decouples the key assignment from the tree restructuring
- Alternatively, keep all `rememberSaveable` calls at the same composition level (same parent composable) and pass values down as parameters

**Detection:** Test process death restoration after refactor: `adb shell am kill com.lunasysman.launcher` while the app is backgrounded, then resume. Verify all state is restored (search open/closed, edit mode, theme selection, deck state).

---

### Pitfall 5: LaunchedEffect and DisposableEffect Key Stability Changes

**What goes wrong:** `HomeScreen.kt` has `LaunchedEffect(searchOpen)` (line 181) that clears edit mode when search opens, and `LaunchedEffect(searchOpen)` (line 273) that requests focus. `LauncherActivity.kt` has `LaunchedEffect(Unit)` blocks for permission checking, event collection, and app refresh. `DisposableEffect(lifecycleOwner, deckOpen)` (line 217) manages widget host lifecycle. If these effects are extracted to different composables, their keys may trigger differently (e.g., a `LaunchedEffect(Unit)` in a newly created composable runs when that composable enters composition, not when the parent originally ran).

**Why it happens:** `LaunchedEffect` and `DisposableEffect` are keyed to their position in the composition tree AND their explicit key arguments. Moving them into a child composable means they re-run when the child enters composition (which may differ from when the parent's scope originally ran them).

**Consequences:**
- `LaunchedEffect(Unit)` for `refreshInstalledApps()` could re-run on every recomposition if extracted into a composable that leaves/re-enters the tree
- `DisposableEffect` for lifecycle observer could leak observers or double-register
- Focus management (`searchFocusRequester.requestFocus()`) could fire at wrong times

**Prevention:**
- Keep `LaunchedEffect(Unit)` blocks at the same composition level they currently occupy
- If effects must move, verify their parent composable has the same lifecycle (enters/leaves composition at the same time)
- For `DisposableEffect`, verify the `onDispose` block still runs at the correct time
- Prefer extracting pure UI composables first; leave effect-heavy logic in the original composable

**Detection:** Add logging to each `LaunchedEffect` and `DisposableEffect` temporarily. Run the app and verify effects fire at exactly the same times as before refactor.

---

## Moderate Pitfalls

### Pitfall 6: Recomposition Scope Widening From Extraction

**What goes wrong:** Currently, `HomeCanvas` is a `private` composable inside `HomeScreen.kt`. The Compose compiler can optimize recomposition boundaries because it knows `HomeCanvas` is only called from one site. After extraction, if the composable signature changes (e.g., accepting additional parameters or changing parameter types), the compiler may not be able to skip recomposition as aggressively. For example, passing `GestureLock` as a parameter instead of capturing it from the parent scope adds a parameter that the compiler must check for equality on every recomposition.

**Prevention:**
- Mark `GestureLock` as `@Stable` (it already is -- verify this survives extraction)
- Use `@Immutable` or `@Stable` annotations on all data classes passed as parameters
- Do not pass lambda parameters that are re-created on every recomposition -- use `remember` to stabilize them
- Profile recomposition counts before and after refactor using Layout Inspector

---

### Pitfall 7: Import Conflicts and Ambiguous References After File Splitting

**What goes wrong:** `HomeScreen.kt` imports `androidx.compose.ui.geometry.Offset`, `Rect`, `CornerRadius`, `RoundRect`, `Path`. `JustType.kt` imports many of the same types. `LauncherActivity.kt` also imports `Offset`, `Rect`. After splitting, each new file needs its own imports. Copy-paste errors can import the wrong `Offset` (e.g., `android.graphics.Point` instead of Compose's `Offset`) or miss imports entirely, causing compilation errors or -- worse -- importing a similarly-named class from a different package that compiles but behaves differently.

**Prevention:**
- Let the IDE auto-resolve imports after extraction
- Verify no `android.graphics.*` types leak into Compose-only files
- After extraction, review imports for each new file to ensure consistency

---

### Pitfall 8: BackHandler Ordering Changes

**What goes wrong:** `LauncherRoot()` in `LauncherActivity.kt` declares five `BackHandler` blocks in a specific order (lines 382-405): deck search, deck, all apps, search, dragging. In Compose, when multiple `BackHandler` blocks are enabled, the **last** one in composition order wins. If these are extracted into separate composables or reordered during refactoring, back button behavior changes silently.

**Prevention:**
- Keep all `BackHandler` declarations in the same composable, in the same order
- If they must be distributed across extracted composables, document the required composition order
- Test back button behavior for every state combination: deck+search open, all apps open, search open, dragging

**Detection:** Manual testing matrix:
1. Open search, press back -- should close search
2. Open deck, press back -- should close deck
3. Open deck search, press back -- should close deck search but keep deck open
4. Open all apps, press back -- should close all apps
5. Start drag, press back -- should cancel drag

---

### Pitfall 9: SideEffect Timing for Window Blur Radius

**What goes wrong:** `LauncherRoot()` uses a `SideEffect` (line 313) to set the window's background blur radius based on `searchOpen`. `SideEffect` runs after every successful recomposition. If the `SideEffect` is extracted to a child composable that recomposes at a different rate or skips recomposition, the blur effect may get stuck in the wrong state.

**Prevention:**
- Keep the `SideEffect` in the same composable that owns the `searchOpen` state
- If it must move, convert to `LaunchedEffect(searchOpen)` which is explicitly keyed to the value change
- Test: open search (blur should appear), close search (blur should clear), rapidly toggle (should not flicker or get stuck)

---

### Pitfall 10: EditModeIconStates Registry Leaks

**What goes wrong:** `HomeCanvas` uses `EditModeIconStates` (line 572) with `SideEffect` to register icon states and `DisposableEffect` to unregister them. If `HomeCanvas` is extracted and the `remember { EditModeIconStates() }` moves to a different composition level, the registry may be recreated when it shouldn't be (losing all registrations) or persist when it should be cleared.

**Prevention:**
- Keep `EditModeIconStates` instantiation at the same composition level
- Ensure `LaunchedEffect(editMode)` that calls `iconStates.clearAll()` stays co-located with the registry
- The `SideEffect` registrations and `DisposableEffect` unregistrations must be in the same composable that iterates `uniqueIcons`

---

## Minor Pitfalls

### Pitfall 11: Losing the `key(lp.id)` Block During Extraction

**What goes wrong:** `HomeCanvas` uses `key(lp.id)` (line 599) inside a `forEach` loop to give each icon a stable identity in the composition. If the icon rendering is extracted to a separate composable without preserving the `key()` wrapper, Compose cannot track icons across recompositions, causing visual glitches (icons "jumping" positions) and lost `remember` state.

**Prevention:**
- Keep the `key(lp.id)` call in the parent that iterates, even if the content is extracted
- Never put `key()` inside the extracted composable -- it must wrap the call site

---

### Pitfall 12: Modifier.then() Chain Order Sensitivity

**What goes wrong:** `HomeCanvas` builds complex modifier chains using `.then(if (editMode) ... else ...)` (line 645). The order of modifiers matters in Compose (e.g., `graphicsLayer` before `offset` vs after). If these chains are refactored into helper functions that return `Modifier`, the order could be accidentally changed, causing visual layout differences.

**Prevention:**
- When extracting modifier builders, preserve the exact chain order
- Test visual output with screenshots before and after

---

### Pitfall 13: Hardcoded Dimension Values Diverging Across Files

**What goes wrong:** `HomeScreen.kt` defines `dockBarHeight = 128.dp`, `chevronGap = 10.dp`, `frameCornerRadius = 26.dp`, `iconSizeDp = 72.dp`, and many other dimension constants inline. `BottomAppBar` uses `dockTileSize = 88.dp`. If these are split across files, the same value may be hardcoded in two places. A future change to one without the other causes subtle layout misalignment.

**Prevention:**
- Extract shared dimensions into a `HomeDimensions` object before splitting files
- Reference the shared object from all extracted files
- This is a pre-refactor cleanup task, not a mid-refactor task

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| REF-01: JustType.kt split | Low risk. Composables are already well-separated internally (`TopSearchBar`, `SearchResultsPanel`, helper composables). No shared mutable state between them. | Straightforward extraction. Verify `internal` visibility is maintained. |
| REF-02: WidgetsPickerActivity.kt split | Medium risk. Activity class has `pendingAppWidgetId` and `pendingProviderInfo` nullable state that coordinates across `onActivityResult` callbacks. Extracting composables is safe, but extracting the binding state machine requires careful parameter threading. | Extract composables first, leave activity state machine in the Activity class. |
| REF-03: HomeScreen.kt split | High risk. `GestureLock`, `EditModeIconStates`, `editMode`/`selectedIconId` state, and gesture modifiers are tightly coupled. `HomeCanvas` cannot be extracted without also extracting `GestureLock` and the icon state registry. The `drawBehind` custom painting for the frame border references density and inset values computed in the parent. | Extract in dependency order: (1) `GestureLock` and dimension constants, (2) `BottomAppBar` and `ChevronHandle` (leaf composables with no shared state), (3) `HomeEditBar`, (4) `HomeCanvas` last (most coupled). |
| REF-04: LauncherActivity.kt split | Highest risk. `LauncherRoot()` is a 650+ line composable with 15+ state variables, 5 `BackHandler` blocks, lifecycle effects, and deeply nested lambdas. Breaking closures is almost guaranteed during extraction. | Extract `LaunchPointIcon` first (already self-contained). Then extract state into `LauncherRootState` class. Then extract visual sections. Keep all `BackHandler` and `LaunchedEffect(Unit)` blocks in the root composable. |

## Refactoring Order Recommendation

Based on pitfall analysis, the safest order is:

1. **JustType.kt** -- Lowest coupling, composables are already logically separated
2. **WidgetsPickerActivity.kt** -- Medium coupling, but composable code is distinct from activity lifecycle code
3. **HomeScreen.kt** -- High coupling via gesture system, but contained within one module
4. **LauncherActivity.kt** -- Highest coupling, most state variables, most side effects, most risk

This matches the order proposed in PROJECT.md (REF-01 through REF-04).

## Universal Prevention Checklist

Before each file extraction:
- [ ] Inventory all `var` state that the extracted code reads or writes
- [ ] Inventory all `remember`/`rememberSaveable` calls and their key strategies
- [ ] Inventory all `LaunchedEffect`/`DisposableEffect`/`SideEffect` blocks
- [ ] Inventory all `BackHandler` blocks and their ordering
- [ ] Identify all `@Stable`/`@Immutable` annotations that must survive extraction
- [ ] Full build after extraction
- [ ] Manual testing of affected feature area
- [ ] Recomposition profiling (Layout Inspector) comparing before/after

After each file extraction:
- [ ] Grep for duplicate `GestureLock()`, `EditModeIconStates()`, `FocusRequester()` instantiations (must be exactly one each)
- [ ] Verify `rememberSaveable` state survives process death (`adb shell am kill`)
- [ ] Verify back button behavior for all state combinations
- [ ] Verify window blur toggles correctly with search open/close
- [ ] Verify no new `public` composables leaked into module API surface

## Sources

- Direct analysis of `HomeScreen.kt` (769 lines), `JustType.kt` (801 lines), `LauncherActivity.kt` (797 lines), `WidgetsPickerActivity.kt` (840 lines)
- PROJECT.md constraints: zero behavior changes, preserve existing design, incremental refactor
- CONCERNS.md: identified recomposition storms from mega-files, gesture state machine fragility, widget binding state machine fragility
- Confidence: HIGH -- all findings derived from direct codebase analysis of actual patterns and coupling
