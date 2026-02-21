# Feature Landscape: Well-Organized Jetpack Compose Code

**Domain:** Compose file refactoring / code organization principles
**Researched:** 2026-02-20
**Overall Confidence:** HIGH (established patterns stable since Compose 1.0; verified against this codebase's own good/bad examples)

---

## Context: Why This Research Matters

Luna Launcher has four files between 769-840 lines each (`JustType.kt`, `WidgetsPickerActivity.kt`, `HomeScreen.kt`, `LauncherActivity.kt`). These files mix multiple Composables, state holders, gesture handlers, and utility functions. The documented consequences (CONCERNS.md):

- **Hard to debug**: Too much code in one place; tracking down bugs requires reading 800+ lines of interleaved concerns
- **Recomposition storms**: Changes to one Composable trigger unintended recompositions in unrelated UI because state is shared at too high a scope
- **IDE lag**: Syntax highlighting and autocomplete degrade in files exceeding ~500 lines of Compose code

The refactor must produce files that are independently understandable and debuggable (PROJECT.md core value) while preserving all existing behavior, architecture (MVVM + manual DI), and module boundaries.

### Evidence from This Codebase

Luna already has well-organized files that demonstrate the target patterns alongside the problematic ones:

| File | Lines | Why It Works |
|------|-------|-------------|
| `GestureState.kt` | 175 | One sealed interface, one responsibility, excellent KDoc, `@Stable` annotation |
| `GestureThresholds.kt` | 103 | Pure configuration, no rendering, centralized constants |
| `HomeGestures.kt` | 135 | Single gesture pipeline, focused scope |
| `LaunchPointTile.kt` | 35 | One composable, slot API pattern (`iconContent: @Composable () -> Unit`), `internal` visibility |
| `NotificationPermissionDialog.kt` | 144 | One dialog, self-contained, clear name |

Contrast with the problem files:

| File | Lines | Why It Hurts |
|------|-------|-------------|
| `JustType.kt` | 801 | Search bar + results list + provider config + row items + category icons + dividers |
| `WidgetsPickerActivity.kt` | 840 | Activity lifecycle + Compose UI + widget binding + preview caching + permissions |
| `HomeScreen.kt` | 769 | Gesture lock + search integration + icon grid + dock bar + text input |
| `LauncherActivity.kt` | 797 | Activity setup + root composable + permission flows + dialog management |

The good files average 118 lines. The bad files average 802 lines. The good files each have one reason to change.

---

## Table Stakes

Features that well-organized Compose code must have. Missing any of these means the refactor failed.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Single Responsibility per File | A file that does one thing is findable, debuggable, and reviewable. Mixed concerns force developers to hold too much context. | Low | Luna's `GestureState.kt` (175 lines, one sealed interface) exemplifies this. `JustType.kt` (801 lines, search bar + results list + provider config + row items) violates it. |
| State Hoisting to Callers | Composables that own their own state cannot be reused or tested. State flows down as parameters; events flow up as lambdas. | Med | Luna's `HomeScreen` already hoists most state (icons, dock items, callbacks). Some files mix `remember`/`mutableStateOf` with business logic inside composables. |
| Stable, Descriptive File Names | File name tells you what is inside without opening it. Feature prefix + layer suffix: `JustTypeSearchBar.kt`, `HomeGestureHandling.kt`, `WidgetsPickerState.kt`. | Low | Already decided in PROJECT.md. Naming convention is feature-prefix + layer-suffix. |
| Explicit Visibility Modifiers | Composables for one file: `private`. Shared within module: `internal`. Cross-module API: `public`. | Low | Luna uses `internal` on some composables (good, see `LaunchPointTile.kt`). Many `private` helpers in large files will become `internal` when extracted -- correct behavior. |
| Unidirectional Data Flow | Data flows down (parameters), events flow up (lambdas). No composable both reads and writes shared mutable state. | Med | Standard Compose pattern. Luna follows it at ViewModel level but some composable-local state blurs the boundary. |
| Minimal Parameter Lists (< 10 params) | Composables with 15+ parameters do too much. Group related params into data classes or split the composable. | Med | `HomeScreen` currently takes many parameters. After extraction, each sub-composable takes only what it needs. |
| Recomposition Scope Isolation | Each composable function is a potential recomposition scope. Mixing unrelated UI in one composable means query changes recompose the icon grid. Separate composables = separate scopes. | High | Primary performance motivation. CONCERNS.md: "recomposition storms." |
| No Business Logic in Composables | Composables render UI. Filtering, sorting, mapping, formatting belong in ViewModels, state holders, or plain functions. | Med | JustType.kt has icon-to-vector mapping logic mixed into the UI layer. Extract to utility. |

## Differentiators

Features that elevate quality beyond baseline. Not required but provide significant value.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| `@Preview` Functions for Each Visual Composable | Previews enable visual verification in IDE without running the app. Each extracted visual composable should have at least one preview. | Low | Luna currently has no previews in the problem files. Adding them validates that extracted composables are self-contained. |
| Dedicated State Holder Classes | A plain class holding UI state for a feature scope. Example: `JustTypeState` with query, filter, results -- passed as a single parameter. | Med | Reduces parameter count, makes state testable without Compose. `GestureState.kt` is this pattern done well. |
| KDoc on Internal Composables | Brief doc comment explaining what the composable renders. Not for private helpers. | Low | `GestureState.kt` has excellent KDoc. Follow this standard. |
| Slot API Pattern | Using `@Composable () -> Unit` parameters (slots) instead of raw data. Enables caller to control rendering. | Med | `LaunchPointTile` uses `iconContent: @Composable () -> Unit` -- proven pattern in this codebase. |
| `@Stable` / `@Immutable` Annotations | Helps Compose compiler skip recomposition when parameters have not changed. Critical for data classes passed to composables. | Med | `GestureState.kt` uses `@Stable`. State holder classes should be annotated. |
| Modifier as First Optional Parameter | Every rendering composable accepts `modifier: Modifier = Modifier`. Enables caller layout control. | Low | Standard convention. Some extracted composables will need this added. |
| Utility Functions in Separate Files | Icon mapping, color derivation, formatting in `*Utils.kt`. Keeps composables focused on layout. | Low | JustType.kt has `categoryIcon()` type functions -- extract to `JustTypeUtils.kt`. |
| Consistent Error Boundaries | Wrap risky operations at composable boundary, not inside nested lambdas. Use specific exception types. | Med | Aligns with CONCERNS.md: replace generic Exception catches. |

## Anti-Features

Things to explicitly NOT do. Common mistakes that feel productive but cause problems.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| God Files (800+ lines mixing concerns) | IDE lag, recomposition storms, impossible reviews, merge conflicts. Luna has four right now. | Split by layer: UI composable, state holder, utilities. Each file has one reason to change. |
| Splitting by Line Count Alone | Arbitrary 300-line splits create files with no coherence. A 400-line file with single responsibility beats two 200-line coupled files. | Split by responsibility boundary. PROJECT.md: "Flexible line count target (logical boundaries over strict limits)." |
| Circular Dependencies Between Extracted Files | File A imports File B, File B imports File A. Happens with careless extraction. | Dependencies flow one direction: State <- UI <- Utils. If two files need each other, they belong together or need a shared interface. |
| Deep Composable Nesting in One File | A calls B calls C calls D calls E, all in same file. Recomposition tracking impossible. | Extract composables to own files when they exceed ~150 lines or have independent state. Keep tiny helpers private. |
| State Duplication Across Files | Same state tracked in two places (e.g., `searchQuery` in both search bar and results). Sync bugs. | Single source of truth. State lives in parent/state holder and is passed down. |
| Premature Abstraction | Creating `SearchResultItem<T>` or `BasePanel` before 3+ concrete uses. YAGNI. | Keep concrete during refactor. Abstract only with proven duplication. |
| Exposing Internal Composables as Public | Making everything `public` because easier. Expands API surface and coupling. | Default `internal`. Only `public` for cross-module needs. |
| Moving State into Composables | Reacting to "too many parameters" by putting `remember { mutableStateOf() }` inside extracted composables. Hides state. | Keep state hoisted. High parameter count -> group into state holder data class. |
| Over-Extracting Tiny Composables | Every `Row`, `Column`, `Text` in its own file. File explosion without benefit. | Private composables under ~30 lines stay in parent file. `RowDivider()` (7 lines in JustType.kt) does not need its own file. |
| Changing Behavior During Refactoring | "While I'm here, let me also fix..." Scope creep destroys refactor confidence. | Pure structural refactor. Zero behavior changes. PROJECT.md: "no visual differences, pure refactor." |
| Passing ViewModel to Composables | `fun MyComponent(viewModel: LauncherViewModel)` couples to implementation, prevents preview/testing. | Pass data + lambdas. Current codebase already does this at HomeScreen level -- maintain discipline. |

## Feature Dependencies

```
Recomposition Scope Isolation
  depends on -> Single Responsibility per File
  depends on -> State Hoisting to Callers

State Holder Classes
  depends on -> State Hoisting (state must be external before grouping)

@Preview Functions
  depends on -> Single Responsibility (previews work on self-contained composables)
  depends on -> Modifier as First Optional Parameter (previews control size)

@Stable/@Immutable Annotations
  depends on -> State Holder Classes (annotate the holders)

Utility Function Extraction
  independent (can be done first, reduces file size immediately)

Explicit Visibility Modifiers
  depends on -> Single Responsibility (visibility decisions require knowing boundaries)
```

## Splitting Decision Framework

When deciding whether to extract a composable into its own file, apply these criteria in order:

### Extract When:
1. **Different reason to change.** The search bar changes for UX reasons; results list changes for data reasons. Different change drivers = different files.
2. **Independent recomposition scope needed.** Typing in the search bar recomposes the results list unnecessarily -> they must be separate composables.
3. **The composable has its own state.** If it manages focus, animation, scroll position independently, it is a self-contained unit.
4. **It exceeds ~150 lines.** Large composables are hard to read. But only split along a natural responsibility boundary.
5. **It would benefit from a preview.** If you want to visually verify it in isolation, it needs a file where `@Preview` can provide fake data.

### Keep Together When:
1. **Always change together.** Modifying search bar always requires modifying results panel styling -> they share a responsibility.
2. **Under ~30 lines.** Tiny helpers like `RowDivider()`, `SectionHeader()` stay private in parent file.
3. **Tightly coupled parameters.** Two composables sharing 80% of parameters -> extracting creates awkward forwarding without reducing complexity.

---

## Addressing Specific Pain Points

### Pain Point: Debuggability

**Root cause:** Multiple unrelated concerns in one file -> understanding the entire file to debug any part.

**How the principles address it:**
- Single Responsibility: A search bar bug -> `JustTypeSearchBar.kt` directly
- Naming: File names map to features. No guessing which file has dock rendering.
- File Size: Stack traces and Logcat references point to scannable code
- No God Files: No scrolling through 800 lines for a 30-line section

**Measurable outcome:** Developer identifies correct file for any UI bug within 10 seconds using file names alone.

### Pain Point: IDE Performance

**Root cause:** Compose compiler plugin and syntax analyzer scale poorly with file size. Files over ~500 lines cause lag.

**How the principles address it:**
- File Size Budget: All files under 400 lines. Well within IDE comfort zone.
- Single Responsibility: Fewer imports per file. Import resolution is significant contributor to analysis time.

**Measurable outcome:** No autocomplete delay > 500ms in any Compose file. (Baseline: 800-line files show 1-3 second delays.)

### Pain Point: Recomposition Storms

**Root cause:** Multiple UI elements in same composition scope -> state change affecting one triggers recomposition of all.

**How the principles address it:**
- Minimal State Scope: State passed only to composables that need it. Query change does not recompose icon grid.
- Parameter Discipline: Composables receive only data they render. Compiler can skip recomposition on unchanged params.
- Stability Annotations: `@Stable` / `@Immutable` help compiler prove inputs unchanged.
- Unidirectional Flow: Recomposition propagates only along state-reading paths.

**Measurable outcome:** Layout Inspector recomposition counter shows each composable recomposing only when its inputs change. Zero unnecessary recompositions in standard flows (type search, drag icon, open All Apps).

---

## Concrete Splitting Guidance for Luna Launcher

### JustType.kt (801 lines -> 4-5 files)

| Extracted File | Responsibility | Expected Lines |
|---------------|----------------|----------------|
| `JustTypePanel.kt` | Top-level panel composable, orchestrates search bar + results | ~100-150 |
| `JustTypeSearchBar.kt` | `TopSearchBar` composable and visual variants | ~100-150 |
| `JustTypeResultsList.kt` | `SearchResultsPanel`, `BarRow`, `ContactBarRow`, result items, `RowDivider` (private) | ~250-300 |
| `JustTypeUtils.kt` | `categoryIcon()`, `categoryChipLabel()`, icon-to-vector mapping | ~80-100 |
| `JustTypeState.kt` | State holder class if needed (depends on current state shape) | ~50-80 |

**Key splitting boundary:** Search bar reads query + categories; results list reads search results. Separating prevents search bar from recomposing when results change.

### HomeScreen.kt (769 lines -> 3-4 files)

| Extracted File | Responsibility | Expected Lines |
|---------------|----------------|----------------|
| `HomeScreenComposable.kt` | Root `HomeScreen`, layout orchestration | ~150-200 |
| `HomeCanvas.kt` | Absolute-positioned icon grid rendering | ~200-250 |
| `HomeBottomBar.kt` | `BottomAppBar`, dock items, chevron | ~150-200 |
| `HomeState.kt` | Gesture lock, search open/close coordination | ~80-100 |

**Key splitting boundary:** Gesture handling and icon rendering operate on different state. Gestures read pointer events; icon grid reads positions. Separating contains recomposition.

### WidgetsPickerActivity.kt (840 lines -> 3-4 files)

| Extracted File | Responsibility | Expected Lines |
|---------------|----------------|----------------|
| `WidgetsPickerActivity.kt` | Activity entry, lifecycle, permission results | ~150-200 |
| `WidgetsPickerScreen.kt` | Picker UI composable, widget list, preview cards | ~200-300 |
| `WidgetsPickerState.kt` | Pending widget state, selection tracking | ~100-150 |
| `WidgetBindingUtils.kt` | Widget binding, preview bitmaps, AppWidgetManager helpers | ~100-150 |

**Key splitting boundary:** Activity lifecycle and Compose UI are fundamentally different concerns. Activity handles `onActivityResult`/permissions; Composable handles rendering.

### LauncherActivity.kt (797 lines -> 3-4 files)

| Extracted File | Responsibility | Expected Lines |
|---------------|----------------|----------------|
| `LauncherActivity.kt` | Activity entry, window setup, intent launching | ~100-150 |
| `LauncherRootComposable.kt` | `LauncherRoot()` tree, ViewModel wiring, dialog coordination | ~250-350 |
| `LauncherPermissions.kt` | Permission request flows (notification, contacts) | ~100-150 |
| `LauncherState.kt` | Root state coordination if needed | ~50-100 |

**Key splitting boundary:** `LauncherRoot()` is the main orchestrator wiring all ViewModels. Permissions are a separate concern triggered from multiple places.

---

## Quality Signals Checklist

After refactoring, each file should pass these checks:

| Signal | Pass | Fail |
|--------|------|------|
| Describe purpose in one sentence? | "Renders JustType search results list" | "Contains various JustType stuff" |
| One reason to change? | Changes when result item layout changes | Changes when search bar styling OR result layout OR provider config changes |
| Can write a `@Preview`? | Yes, with fake data | No, requires ViewModel or Activity context |
| All parameters used (no pass-through)? | Parameters + lambdas consumed in body | Forwards half its params to a single child |
| IDE responds instantly? | Yes (< 300 lines, focused imports) | No (800 lines, 50+ imports) |
| Findable by name? | `JustTypeResultsList.kt` -- obvious | `JustTypeHelpers.kt` -- what helpers? |
| Imports scoped to responsibility? | Compose layout + Material3 only | Compose + gesture + animation + database + lifecycle |

## MVP Recommendation

**Prioritize (apply to every file):**
1. Single responsibility per file -- the non-negotiable principle
2. State hoisting -- no hidden `mutableStateOf` in child composables unless truly local (animation, scroll position)
3. Explicit visibility -- `internal` for shared, `private` for helpers
4. Stable file names with feature prefix

**Apply when natural (do not force):**
5. `@Preview` functions for extracted UI composables
6. `@Stable` annotations on state holder classes
7. KDoc on `internal` composables

**Defer (not part of this refactor):**
- Generic abstractions or shared component libraries
- Comprehensive test coverage for extracted composables (out of scope per PROJECT.md)
- Module-level restructuring (module boundaries preserved)

---

## Summary of Principles (Quick Reference)

| ID | Principle | Measurable Criterion |
|----|-----------|---------------------|
| MH-1 | Single Responsibility Per File | One-sentence description without "and" joining unrelated concerns |
| MH-2 | Minimal State Scope | Only direct state readers recompose on state change |
| MH-3 | Feature-Layer Naming | Correct file locatable by name alone in < 10 seconds |
| MH-4 | Parameter Discipline | Every parameter used in body; no ViewModel params |
| MH-5 | 150-300 Line Budget | No file > 400 lines without documented justification |
| MH-6 | Unidirectional Data Flow | State down, events up; no bidirectional dependencies |
| NH-1 | Preview Functions | Visual composables include @Preview |
| NH-2 | KDoc on Public APIs | Entry-point composables have one-line documentation |
| NH-3 | Import Organization | Grouped by framework/compose/project; no wildcards |
| NH-4 | Stability Annotations | State holders `@Stable`; data classes `@Immutable` |
| NH-5 | Explicit Visibility | `internal` default; `private` for helpers; `public` only cross-module |
| AP-1 | No God Files | No file > 400 lines; no file with > 5 public composables |
| AP-2 | No State Duplication | Each state value owned in exactly one location |
| AP-3 | No Tight Coupling | Extracted files communicate through parent-mediated parameters |
| AP-4 | No Over-Extraction | No file < 50 lines without independent responsibility |
| AP-5 | No ViewModel Parameters | Composables take data + lambdas, not ViewModel references |
| AP-6 | No Bare Side Effects | All side effects in LaunchedEffect/DisposableEffect/SideEffect |

---

## Sources

- Direct codebase analysis: Luna Launcher's well-organized files (`GestureState.kt` 175 lines, `GestureThresholds.kt` 103 lines, `LaunchPointTile.kt` 35 lines, `HomeGestures.kt` 135 lines) vs. problem files (`JustType.kt` 801, `HomeScreen.kt` 769, `LauncherActivity.kt` 797, `WidgetsPickerActivity.kt` 840)
- PROJECT.md: core value ("independently understandable and debuggable"), key decisions (flexible line count, feature-prefix naming)
- CONCERNS.md: Performance Bottlenecks ("Single mega-files without clear layering cause recomposition storms and poor IDE performance")
- CLAUDE.md: Architecture patterns (StateFlow, manual DI, module dependency flow)
- Google Android Developers: "Thinking in Compose" -- state hoisting, unidirectional data flow (HIGH confidence, stable guidance)
- Google Android Developers: "Jetpack Compose performance" -- recomposition skipping, stability (HIGH confidence)
- Google Android Developers: "State and Jetpack Compose" -- state ownership patterns (HIGH confidence)
- JetBrains Kotlin style guide -- file organization, naming conventions (HIGH confidence)

---

*Research completed: 2026-02-20*
