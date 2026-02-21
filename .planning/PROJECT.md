# Luna Launcher - Compose File Refactor

## What This Is

A systematic refactor of Luna Launcher's four largest Compose files to improve debuggability, fix recomposition performance issues, and enhance IDE responsiveness. This project extracts tightly-coupled Composables, state holders, and utilities into smaller, layer-organized files with clear feature-based naming.

## Core Value

**Each file must be independently understandable and debuggable.** If a developer can't quickly locate and comprehend the code they need to change, the structure has failed.

## Requirements

### Validated

<!-- Current Luna Launcher capabilities that must continue working -->

- ✓ Absolute-positioned home screen with gesture support (drag, long-press, rotate, swipe) — existing
- ✓ "Just Type" universal search with provider-based results (apps, notifications, actions, templates) — existing
- ✓ Widget picker and deck system with preview caching — existing
- ✓ All Apps drawer with icon prefetching and package change detection — existing
- ✓ Notification integration with in-memory indexing and 4-day retention — existing
- ✓ Room database persistence with 11 migration history intact — existing
- ✓ Manual dependency injection via LauncherContainer — existing

### Active

<!-- Refactor work to be completed -->

- [ ] **REF-01**: Extract JustType.kt (801 lines) into layer-based files: JustTypePanel.kt, JustTypeSearchBar.kt, JustTypeResultsList.kt, JustTypeState.kt, JustTypeProviderConfig.kt (max ~300 lines each)
- [ ] **REF-02**: Extract WidgetsPickerActivity.kt (840 lines) into: WidgetsPickerActivity.kt (activity entry), WidgetsPickerComposable.kt, WidgetsPickerState.kt, WidgetBindingUtils.kt
- [ ] **REF-03**: Extract HomeScreen.kt (769 lines) into: HomeScreenComposable.kt, HomeGestureHandling.kt, HomeIconGrid.kt, HomeState.kt
- [ ] **REF-04**: Extract LauncherActivity.kt (797 lines) into: LauncherActivity.kt (activity entry), LauncherRootComposable.kt, LauncherState.kt, LauncherPermissions.kt
- [ ] **REF-05**: Fix deprecated PackageManager API usage opportunistically (@Suppress("DEPRECATION") on queryIntentActivities, getPackageInfo) — migrate to compat methods
- [ ] **REF-06**: Replace generic Exception catches with specific exception types (PackageManager.NameNotFoundException, SecurityException, etc.) where encountered during refactor
- [ ] **REF-07**: Performance validation after each file refactor (manual testing + recomposition profiling + IDE responsiveness check)
- [ ] **REF-08**: Code review of extracted file structure (clear separation of concerns, feature-based naming, logical boundaries)

### Out of Scope

- Architectural changes (MVVM pattern, StateFlow usage, manual DI) — preserve existing design
- UI/UX changes — no visual differences, pure refactor
- Database schema changes — no migration work
- New feature development — strict focus on reorganization
- Comprehensive CONCERNS.md cleanup — only opportunistic fixes during refactor
- Test suite expansion — update existing tests if needed, but don't add new coverage

## Context

### Current Codebase State

**Luna Launcher** is a modern Android home screen replacement built with:
- 100% Kotlin, Jetpack Compose (no XML)
- MVVM + Repository pattern, StateFlow-based reactive UI
- Min SDK 30 (Android 11), Target SDK 35 (Android 15)
- Manual dependency injection (no Hilt/Dagger)
- Room database with 11 migrations

**Problem Files Identified in CONCERNS.md:**
1. `JustType.kt` (801 lines) — search panel with provider config UI, search bar, results list, and state management mixed together
2. `WidgetsPickerActivity.kt` (840 lines) — widget picker, binding flow, preview caching, permission handling in one file
3. `HomeScreen.kt` (769 lines) — home screen composable, gesture state machine, icon grid rendering combined
4. `LauncherActivity.kt` (797 lines) — activity entry point, root composable, permissions, and initialization logic

**Impact:** "Single mega-files without clear layering cause recomposition storms and poor IDE performance" (CONCERNS.md line 67)

### Prior Work

- Codebase mapping completed (`.planning/codebase/` documents analyze current structure)
- CONCERNS.md identifies Large Compose Files as a "Performance Bottleneck" with suggested split: target max 300 lines per file
- Existing docs in `launcher-android/docs/` provide architecture guidance (Gestures.md, FIRST_BOOT_COMPLETE.md, HOME_ABSOLUTE_LAYOUT_TODO.md referenced but missing)

### User Pain Points

- **Hard to debug:** Too much code in one place makes tracking down bugs difficult
- Changes to one Composable trigger unintended recompositions in unrelated UI
- IDE lag when editing large files (syntax highlighting, autocomplete slowdown)

## Constraints

- **Build System**: Gradle 8.7, AGP 8.7.0, Kotlin 2.0.21, Compose BOM 2024.12.01 — no version changes
- **Module Boundaries**: Preserve existing module dependencies (core-model has zero Android UI deps, UI modules don't depend on each other)
- **API Compatibility**: Must maintain all public APIs and StateFlow contracts — ViewModels and repositories unchanged
- **Migration Strategy**: Incremental refactor (one file at a time), validate after each
- **Functionality**: Zero behavior changes — all features must work identically before/after refactor
- **Naming Convention**: Layer-based organization with feature prefixes (e.g., `JustTypeSearchBar.kt`, `HomeGestureHandling.kt`, `WidgetsPickerState.kt`)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Layer-based organization (UI/State/Utils) with feature prefixes | Clear separation of concerns while maintaining feature context; files like `JustTypeSearchBar.kt` are immediately understandable | — Pending |
| Start with JustType.kt first | Largest file (801 lines), most complex, biggest win for debuggability and IDE performance | — Pending |
| Incremental approach (one file at a time) | Reduces risk, allows validation after each step, maintains working state throughout | — Pending |
| Flexible line count target (logical boundaries over strict limits) | Better to have a 400-line file with cohesive responsibility than artificially split at 300 lines | — Pending |
| Opportunistic fixes for CONCERNS.md issues | While refactoring, replace deprecated APIs and improve exception handling without scope creep | — Pending |
| Validation: manual testing + performance checks + code review | No automated tests for gesture system or UI; must rely on manual validation and performance profiling | — Pending |

---
*Last updated: 2026-02-20 after initialization*
