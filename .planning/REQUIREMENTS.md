# Requirements: Luna Launcher - Compose File Refactor

**Defined:** 2026-02-20
**Core Value:** Each file must be independently understandable and debuggable

## v1 Requirements

Requirements for this refactor milestone. Each will map to roadmap phases.

### File Extraction

- [ ] **EXTR-01**: JustType.kt (801 lines) extracted into layer-based files with max ~300-400 lines each (JustTypePanel.kt, JustTypeSearchBar.kt, JustTypeResultsList.kt, JustTypeState.kt, JustTypeProviderConfig.kt)
- [ ] **EXTR-02**: WidgetsPickerActivity.kt (840 lines) extracted into Activity entry point + UI composable + state + utilities (WidgetsPickerActivity.kt, WidgetsPickerComposable.kt, WidgetsPickerState.kt, WidgetBindingUtils.kt)
- [ ] **EXTR-03**: HomeScreen.kt (769 lines) extracted into composable + gesture handling + icon grid + state (HomeScreenComposable.kt, HomeGestureHandling.kt, HomeIconGrid.kt, HomeState.kt)
- [ ] **EXTR-04**: LauncherActivity.kt (797 lines) extracted into Activity entry + root composable + state + permissions (LauncherActivity.kt, LauncherRootComposable.kt, LauncherState.kt, LauncherPermissions.kt)

### Code Quality

- [ ] **QUAL-01**: Deprecated PackageManager API usage replaced with compat methods where encountered (queryIntentActivities, getPackageInfo - currently suppressed with @Suppress("DEPRECATION"))
- [ ] **QUAL-02**: Generic Exception catches replaced with specific exception types where encountered (PackageManager.NameNotFoundException, SecurityException, IllegalArgumentException, PendingIntent.CanceledException)
- [ ] **QUAL-03**: Compose compiler reports enabled in app and ui-home modules to establish recomposition baseline before refactoring
- [ ] **QUAL-04**: rememberSaveable calls use explicit keys to prevent state loss when composables move in composition tree

### Validation

- [ ] **VALD-01**: Manual testing completed after each file refactor - all features work identically to before
- [ ] **VALD-02**: Recomposition profiling completed after each file refactor using Layout Inspector - no performance regressions
- [ ] **VALD-03**: IDE responsiveness validated after each file refactor - autocomplete under 500ms, no syntax highlighting lag
- [ ] **VALD-04**: Code review completed for extracted file structure - clear separation of concerns, logical boundaries, feature-based naming

### Refactor Safety

- [ ] **SAFE-01**: Zero behavior changes - all gestures, animations, navigation flows work identically
- [ ] **SAFE-02**: GestureLock singleton coordination preserved across file splits (critical: single instance required, no duplicated constructors)
- [ ] **SAFE-03**: Closure capture patterns preserved (local functions and lambdas maintain correct state variable references)
- [ ] **SAFE-04**: BackHandler ordering preserved in LauncherActivity (5 handlers in specific sequence determine back-button behavior)
- [ ] **SAFE-05**: Effect lifecycle hooks remain correct (LaunchedEffect, DisposableEffect maintain proper dependencies)

## v2 Requirements

Potential improvements deferred to future refactoring work.

### Additional Files

- **EXTR-05**: AllAppsScreen.kt (684 lines) - also large but lower priority, defer to future
- **EXTR-06**: WidgetDeckOverlay.kt (600 lines) - also large but lower priority, defer to future
- **EXTR-07**: ui-search module cleanup - currently unused (SearchOverlay.kt exists but not imported), consider removal

### Advanced Optimizations

- **PERF-01**: Add kotlinx-collections-immutable if profiling identifies stability issues (research recommends evidence-driven approach, not premature)
- **PERF-02**: Add @Stable/@Immutable annotations if compiler reports show specific instability problems
- **PERF-03**: Extract LauncherRootState class if LauncherRootComposable remains over 400 lines after initial extraction

## Out of Scope

Explicitly excluded to maintain focus on file organization.

| Feature | Reason |
|---------|--------|
| Architectural changes (MVVM, StateFlow, DI pattern) | Preserve existing design - pure refactor only |
| UI/UX changes | No visual differences allowed - must be pixel-identical |
| Database schema changes | No migrations - Room database version stays at 11 |
| New feature development | Strict focus on reorganization, not new capabilities |
| Comprehensive CONCERNS.md cleanup | Only opportunistic fixes during refactor, not systematic cleanup |
| Test suite expansion | Update existing tests if broken, but don't add new test coverage |
| Navigation/coordinator pattern | Would require architectural changes (out of scope) |
| Unused module removal (ui-search) | Separate cleanup task, not part of file refactor |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| QUAL-03 | Phase 1 | Pending |
| QUAL-04 | Phase 1 | Pending |
| EXTR-01 | Phase 2 | Pending |
| VALD-01 | Phase 2 | Pending |
| VALD-02 | Phase 2 | Pending |
| VALD-03 | Phase 2 | Pending |
| VALD-04 | Phase 2 | Pending |
| SAFE-01 | Phase 2 | Pending |
| SAFE-03 | Phase 2 | Pending |
| SAFE-05 | Phase 2 | Pending |
| EXTR-02 | Phase 3 | Pending |
| EXTR-03 | Phase 4 | Pending |
| SAFE-02 | Phase 4 | Pending |
| SAFE-04 | Phase 4 | Pending |
| EXTR-04 | Phase 5 | Pending |
| QUAL-01 | Phase 6 | Pending |
| QUAL-02 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13 (100% coverage)
- Unmapped: 0

**Note:** Validation requirements (VALD-01 to VALD-04) and safety requirements (SAFE-01, SAFE-03, SAFE-05) are explicitly mapped to Phase 2 as they establish the validation pattern, but they apply to ALL extraction phases (2-5). Each extraction phase will validate against these criteria.

---
*Requirements defined: 2026-02-20*
*Last updated: 2026-02-20 after roadmap creation*
