# Phase 1: Baseline & Safety Setup - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish validation baseline and safety measures before any file extraction. This phase sets up measurement tools (Compose compiler reports, Layout Inspector profiling) and adds safety guardrails (explicit rememberSaveable keys, process death testing) to ensure future extractions can be validated against a known-good baseline.

</domain>

<decisions>
## Implementation Decisions

### Compiler Reports
- Store in build directory (`app/build/compose_metrics/`) - standard location, gitignored
- Capture all metrics: skippability, stability, composable counts - comprehensive baseline
- Module selection: Claude's discretion (at minimum app and ui-home)
- Documentation format: Claude's discretion (summary doc vs raw reports)

### Baseline Capture
- Profile target files only: JustType, WidgetsPicker, HomeScreen, LauncherActivity top-level composables
- Exercise these flows: Search flow (open search, type query, select result), Widget flow (open picker, select widget), Home gestures (drag, long-press, rotate, swipe)
- Capture format: Both Layout Inspector screenshots and text metrics (numeric skip/restart counts)
- Capture device: Physical device for real-world performance accuracy
- Storage location: Local only - don't commit baselines to git (reduces repo size)
- Metrics to record per composable: Skip count, Restart count, Total invocations
- Include both cold start and steady-state baselines (separate captures)
- Comparison method: Manual review - visual inspection sufficient for this refactor

### rememberSaveable Keys
- Naming convention: Descriptive strings (`"justtype_search_query"`, `"home_edit_mode"`) - clear and readable
- Organization: Inline strings in each `rememberSaveable` call (not centralized constants)
- Uniqueness validation: Manual review during code review
- Scope: Files being refactored only (JustType, WidgetsPicker, HomeScreen, LauncherActivity) - don't expand to entire codebase

### Claude's Discretion
- Exact number of modules to include in compiler reports (at minimum: app and ui-home)
- Format for documenting compiler report metrics (summary document vs raw reports only)
- Process death testing approach (manual vs automated) - success criteria already specify "validates all state restoration paths work correctly"

</decisions>

<specifics>
## Specific Ideas

No specific product references or interaction patterns - this is a tooling and safety setup phase. Implementation should follow standard Android/Compose practices for profiling and state management.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Testing approach (manual vs automated) was intentionally left to Claude's discretion as success criteria are already clear.

</deferred>

---

*Phase: 01-baseline-safety-setup*
*Context gathered: 2026-02-20*
