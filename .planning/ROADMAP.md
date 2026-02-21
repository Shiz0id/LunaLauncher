# Roadmap: Luna Launcher - Compose File Refactor

## Overview

This roadmap transforms Luna Launcher's four largest Compose files (800+ lines each) into independently debuggable files organized by architectural layer. The refactor proceeds in order of increasing complexity: baseline tooling setup, then JustType (pure UI), WidgetsPicker (isolated Activity), HomeScreen (gesture coordination), and LauncherActivity (composition root). Each phase includes extraction, validation, and safety checks to ensure zero behavior changes while improving IDE performance and code comprehension.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Baseline & Safety Setup** - Enable validation tooling and safety measures before extraction
- [ ] **Phase 2: JustType Extraction** - Extract search system into layer-based files (simplest extraction)
- [ ] **Phase 3: WidgetsPicker Extraction** - Extract widget picker into Activity + UI + state files
- [ ] **Phase 4: HomeScreen Extraction** - Extract home screen into composable + gesture + grid files
- [ ] **Phase 5: LauncherActivity Extraction** - Extract launcher root into Activity + composable + state files
- [ ] **Phase 6: Code Quality Cleanup** - Opportunistic fixes to deprecated APIs and exception handling

## Phase Details

### Phase 1: Baseline & Safety Setup
**Goal**: Establish validation baseline and safety measures before any file extraction
**Depends on**: Nothing (first phase)
**Requirements**: QUAL-03, QUAL-04
**Success Criteria** (what must be TRUE):
  1. Compose compiler reports generate successfully for app and ui-home modules showing current recomposition metrics
  2. All rememberSaveable calls use explicit keys to prevent state loss during composition tree changes
  3. Layout Inspector recomposition baseline captured showing current skip/restart counts for target composables
  4. Process death testing validates all state restoration paths work correctly before refactoring
**Plans**: TBD

### Phase 2: JustType Extraction
**Goal**: Extract JustType.kt (801 lines) into independently debuggable files proving the extraction pattern
**Depends on**: Phase 1
**Requirements**: EXTR-01, VALD-01, VALD-02, VALD-03, VALD-04, SAFE-01, SAFE-03, SAFE-05
**Success Criteria** (what must be TRUE):
  1. JustTypeSearchBar.kt contains search input UI only (approximately 100 lines)
  2. JustTypeResultsList.kt contains all results rendering composables (approximately 500 lines)
  3. JustTypeProviderConfig.kt contains provider configuration UI (approximately 150 lines)
  4. All search functionality works identically to before extraction - query input, provider filtering, result selection, templates, shortcuts
  5. Compose compiler reports show no composables lost skippable status
  6. IDE autocomplete responds under 500ms when editing extracted files
**Plans**: TBD

### Phase 3: WidgetsPicker Extraction
**Goal**: Extract WidgetsPickerActivity.kt (840 lines) into Activity entry point + UI composables + state + utilities
**Depends on**: Phase 2
**Requirements**: EXTR-02, VALD-01, VALD-02, VALD-03, VALD-04, SAFE-01, SAFE-03, SAFE-05
**Success Criteria** (what must be TRUE):
  1. WidgetsPickerActivity.kt contains only Activity lifecycle code and onActivityResult handling (approximately 220 lines)
  2. WidgetsPickerComposable.kt contains all picker UI composables (approximately 410 lines)
  3. WidgetsPickerState.kt contains data classes and grid calculations (approximately 40 lines)
  4. WidgetBindingUtils.kt contains async image loading and binding utilities (approximately 90 lines)
  5. Widget picker opens, allows widget selection, binds widget, and returns result identically to before extraction
  6. Layout Inspector shows no recomposition count increases compared to baseline
**Plans**: TBD

### Phase 4: HomeScreen Extraction
**Goal**: Extract HomeScreen.kt (769 lines) into composable + gesture handling + icon grid with preserved GestureLock coordination
**Depends on**: Phase 3
**Requirements**: EXTR-03, VALD-01, VALD-02, VALD-03, VALD-04, SAFE-01, SAFE-02, SAFE-03, SAFE-04, SAFE-05
**Success Criteria** (what must be TRUE):
  1. HomeScreenComposable.kt contains main orchestrator and GestureLock instance creation (approximately 380 lines)
  2. HomeIconGrid.kt contains HomeCanvas with absolute positioning (approximately 170 lines)
  3. HomeDock.kt contains bottom bar, chevron, and edit bar (approximately 150 lines)
  4. All home screen gestures work identically - icon drag, long-press, rotate, swipe, edit mode
  5. GestureLock singleton coordination preserved - only one instance exists, no gesture conflicts
  6. EditModeIconStates registry does not leak references after extraction
**Plans**: TBD

### Phase 5: LauncherActivity Extraction
**Goal**: Extract LauncherActivity.kt (797 lines) into Activity entry + root composable + state + permissions with preserved closure captures and BackHandler ordering
**Depends on**: Phase 4
**Requirements**: EXTR-04, VALD-01, VALD-02, VALD-03, VALD-04, SAFE-01, SAFE-03, SAFE-04, SAFE-05
**Success Criteria** (what must be TRUE):
  1. LauncherActivity.kt contains only Activity entry point code (approximately 50 lines)
  2. LauncherRootComposable.kt contains ViewModel wiring hub (approximately 400 lines - will remain largest file)
  3. LauncherState.kt contains menu state and utility functions (approximately 80 lines)
  4. LauncherPermissions.kt contains permission dialog coordination (approximately 60 lines)
  5. All closure captures work correctly - state mutations propagate, no broken variable references
  6. BackHandler ordering preserved - back button behavior identical in all contexts (search open, deck open, all apps, dragging)
  7. LaunchedEffect and DisposableEffect blocks trigger at identical times as before extraction
  8. Full launcher flow works identically - launch, search, all apps, deck, menu, permissions, gestures
**Plans**: TBD

### Phase 6: Code Quality Cleanup
**Goal**: Replace deprecated APIs and improve exception handling across all extracted files
**Depends on**: Phase 5
**Requirements**: QUAL-01, QUAL-02
**Success Criteria** (what must be TRUE):
  1. All PackageManager API calls use compat methods instead of deprecated versions (queryIntentActivities, getPackageInfo)
  2. All generic Exception catches replaced with specific exception types (PackageManager.NameNotFoundException, SecurityException, IllegalArgumentException, PendingIntent.CanceledException)
  3. No @Suppress("DEPRECATION") annotations remain in extracted files
  4. All functionality works identically after API migration - no new crashes or behavior changes
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Baseline & Safety Setup | 0/0 | Not started | - |
| 2. JustType Extraction | 0/0 | Not started | - |
| 3. WidgetsPicker Extraction | 0/0 | Not started | - |
| 4. HomeScreen Extraction | 0/0 | Not started | - |
| 5. LauncherActivity Extraction | 0/0 | Not started | - |
| 6. Code Quality Cleanup | 0/0 | Not started | - |
