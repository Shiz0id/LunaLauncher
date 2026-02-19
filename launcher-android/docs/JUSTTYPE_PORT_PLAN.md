# Just Type v2 (WebOS-faithful) — Android Launcher Implementation Plan

## Goals (non-negotiables)
- **Registry-first**: Just Type is a *provider registry + preferences*, not a simple search box.
- **WebOS categories**: `ACTION`, `DBSEARCH`, `SEARCH` (+ local `APPS` provider).
- **Sectioned results**: results are grouped by provider, not interleaved into one list.
- **Stable ordering**: section order comes from registry `orderIndex` (with a final enforcement that `SEARCH using` is last).
- **Deterministic ranking**: no jitter; stable tie-breakers.
- **Contacts included** (DBSEARCH) and **“Search using …”** at the bottom (SEARCH).
- **No root / no ROM / no system shenanigans**. All in-process.

---

## Architecture Overview (WebOS faithful mapping)
WebOS components → Android equivalents:

- **SearchItemsManager** (registry, defaults, patches, integrity)
  → `JustTypeRegistry` (Room + DataStore) + `JustTypeRegistryInitializer` (defaults merge)
- **UniversalSearchService** (surface API to UI; list of providers; preferences)
  → `JustTypeEngine` (in-process orchestrator) + `LauncherViewModel` state + events
- **OpenSearchHandler** (import and normalize OpenSearch)
  → `SearchProviderImporter` (optional Phase 2/3; start with hardcoded templates)

Provider categories:
- `APPS` (local launcher provider; Android apps and later WebOS apps)
- `ACTION` (Quick Actions / verbs)
- `DBSEARCH` (OS-owned data stores queried by the launcher: Contacts first)
- `SEARCH` (OpenSearch-like URL templates: “Search using Google/Wikipedia…”)

---

## Milestones (recommended order)
This order minimizes risk and keeps the launcher runnable at every stage.

### Milestone 1 — UI supports sectioned results (APPS only)
Outcome: same behavior as today, but Just Type UI is ready for WebOS-style sections.

### Milestone 2 — Actions (Quick Actions section)
Outcome: WebOS feel begins (“New Email”, “New Event”, etc.).

### Milestone 3 — Search using (templates)
Outcome: bottom “Search using …” section like webOS.

### Milestone 4 — Contacts (DBSEARCH)
Outcome: real DBSEARCH provider with permission + query.

### Milestone 5 — Provider registry (defaults + merge + prefs)
Outcome: true WebOS registry behavior (enable/order/default engine + update-safe merges).

---

# Phase A — UI Refactor (Sectioned Just Type)
## A1) Add Just Type UI models (pure data)
Create: `core/justtype/model/`

- `JustTypeCategory.kt`
  - `APPS`, `ACTION`, `DBSEARCH`, `SEARCH`

- `JustTypeUiState.kt`
  - `JustTypeUiState(query, sections)`
  - `JustTypeSectionUi(providerId, title, category, items)`
  - `JustTypeItemUi` sealed class:
    - `LaunchPointItem(lpId: String)`
    - `ActionItem(actionId: String, title: String, subtitle: String? = null)`
    - `DbRowItem(providerId: String, stableId: String, title: String, subtitle: String?)`
    - `SearchTemplateItem(providerId: String, title: String, query: String)`

Notes:
- Keep `LaunchPointItem` as `lpId` so UI resolves icons/titles from the canonical LaunchPoint map.
- `DbRowItem.stableId` is used for keys + routing.

## A2) Update SearchOverlay to render sections
Edit: `SearchOverlay.kt`

Old:
- `results: List<LaunchPoint>`

New:
- `state: JustTypeUiState`
- `launchPointsById: Map<String, LaunchPoint>`
- `onItemClick: (JustTypeItemUi) -> Unit`

Rendering rules:
- One section header + one `LazyRow` per section.
- Items render by type:
  - LaunchPoint tile: app icon + title
  - Action tile: distinct action icon style (bolt/plus)
  - DbRow tile: avatar placeholder + title/subtitle
  - Search template tile: provider icon + “Search with X”

## A3) Update LauncherActivity wiring
Edit: `LauncherActivity.kt`

- Replace passing `query`/`results` into SearchOverlay with passing:
  - `state = vm.justTypeState`
  - `launchPointsById = vm.launchPointsById`

- Click routing:
  - `LaunchPointItem` → existing launch flow
  - `ActionItem` → VM maps to Intent
  - `DbRowItem` → VM maps to contact view Intent
  - `SearchTemplateItem` → VM maps to browser Intent

---

# Phase B — Provider Registry (WebOS “secret sauce”)
Milestone 5 implements this fully, but define contracts early so providers don’t drift.

## B1) Room entity for provider registry
Create: `data/justtype/`

- `JustTypeProviderEntity.kt` (single table with category)
  Fields (minimal faithful subset):
  - `id` (PK)
  - `category` ("apps"|"action"|"dbsearch"|"search")
  - `displayName` (nullable ok for apps provider)
  - `enabled` (bool)
  - `orderIndex` (int)
  - `version` (int)
  - `source` ("default"|"cust"|"user"|"system")
  - `urlTemplate` (SEARCH)
  - `suggestUrlTemplate` (SEARCH optional)

- `JustTypeProviderDao.kt`
  - observe enabled providers ordered by `orderIndex`
  - get by id
  - upsert list
  - delete by id
  - update enabled/orderIndex

- Database migration
  - create `just_type_provider` table

## B2) DataStore prefs
Create: `data/justtype/JustTypePrefs.kt`
- `defaultSearchProviderId: String`

## B3) Defaults + cust patch (WebOS layering)
Assets:
- `assets/justtype_defaults.json`
- `assets/justtype_cust.json` (optional; can be empty)

Defaults JSON items:
- ACTION: `new_email`, `new_event`, `new_message` (order 10/20/30)
- APPS: `apps` (order 100)
- DBSEARCH: `contacts` (order 200)
- SEARCH: `google` (order 900), `wikipedia` (910)

Cust patch semantics (future-ready):
- `{ id, category?, remove?: true, enabled?: bool, order?: int }`

## B4) Registry initializer + merge rules (faithful to webOS)
Create: `data/justtype/JustTypeRegistryInitializer.kt`

Rules:
1) Read existing DB providers
2) Load defaults JSON
3) Merge defaults into DB:
   - insert missing
   - if existing and `defaults.version > db.version`:
     - overwrite fields but **preserve db.enabled** (webOS behavior)
4) Apply cust patch:
   - remove deletes
   - enabled/order updates
5) Integrity pass (optional now; required later for app-backed providers)
6) Ensure `defaultSearchProviderId` exists and enabled; else set first enabled SEARCH provider

Initializer runs on app start (Application/container).

---

# Phase C — Providers (ACTION/APPS/DBSEARCH/SEARCH)
## C0) Provider interface
Create: `core/justtype/providers/`

- `JustTypeProvider.kt`
  - `id`, `category`, `title`
  - `suspend fun query(query: String): List<JustTypeItemUi>`

## C1) AppsProvider (APPS)
Create: `AppsProvider.kt`

Inputs:
- launchPoints snapshot (list)
- pinned/favorites and recents (from your model)

Behavior:
- Empty query: pinned/favorites then recents (cap 12)
- Non-empty: deterministic scoring:
  - token-prefix match +100
  - title prefix +90
  - substring +60
  - pinned +20
  - recency decay +0..+25
Tie-break:
- pinned desc, lastLaunched desc, title asc

Output:
- `LaunchPointItem(lp.id)`

## C2) ActionsProvider (ACTION)
Create: `ActionsProvider.kt`

Behavior:
- Empty query: show top N default actions (3–5)
- Non-empty: show actions only when query suggests verbs:
  - starts with `new`, `compose`, `email`, `text`, `sms`, `call`, `event`, `note`, `search`, `navigate`

Output:
- `ActionItem(actionId, title, subtitle?)`

Execution mapping (handled in ViewModel router):
- `new_email` → ACTION_SENDTO mailto:
- `new_event` → CalendarContract insert
- `new_message` → sms compose

## C3) ContactsProvider (DBSEARCH)
Create: `ContactsProvider.kt`

Permission:
- If `READ_CONTACTS` missing, return `ActionItem("enable_contacts_search", "Enable Contacts Search")`
- Clicking emits a VM event (Activity requests permission)

Query:
- Only when query non-empty
- Use `ContactsContract` to search by display name (limit ~15–25)

Output:
- `DbRowItem(providerId="contacts", stableId=contactId, title=name, subtitle=phoneOrEmail)`

Click:
- opens contact card via Intent

Add debounce (~150ms) in VM before calling provider to avoid query spam.

## C4) SearchTemplatesProvider (SEARCH) — “Search using …”
Create: `SearchTemplatesProvider.kt`

Behavior:
- Only show when query non-empty
- First item is default engine
- Remaining enabled engines in order

Output:
- `SearchTemplateItem(providerId, title="Search with X", query=query)`

Click:
- urlTemplate replace `{searchTerms}` with URLEncoded query
- open browser Intent

---

# Phase D — Engine Orchestration
## D1) JustTypeEngine
Create: `core/justtype/engine/JustTypeEngine.kt`

Inputs:
- query
- enabled ordered provider defs (from registry or interim hardcoded list)
- launchPoints snapshot
- prefs (default engine)

Execution:
1) Resolve provider instances in stable order
2) Execute queries (sequential ok for MVP; parallel optional)
3) Build sections (omit empty)
4) Enforce category positioning:
   - ACTION first
   - APPS second
   - DBSEARCH third
   - SEARCH last

Output:
- `JustTypeUiState(query, sections)`

---

# Phase E — LauncherViewModel integration
Edit: `LauncherViewModel.kt`

## E1) Replace `searchResults` with `justTypeState`
Add:
- `val justTypeState: StateFlow<JustTypeUiState>`
- `val launchPointsById: StateFlow<Map<String, LaunchPoint>>`

Compute:
- combine `_searchQuery` + launchPoints snapshot (+ registry/prefs once milestone 5)
- call `JustTypeEngine.buildState(...)`

## E2) Click router
Add:
- `fun intentFor(item: JustTypeItemUi): Intent?`

Cases:
- LaunchPointItem → existing `intentFor(lpId)`
- ActionItem → map actions
- DbRowItem(contacts) → contact view
- SearchTemplateItem → build search url intent

## E3) Permission request event channel
Add:
- `val events: SharedFlow<LauncherEvent>`
- Emit `RequestContactsPermission` when enable-contacts action tapped.

Activity:
- collects events and triggers permission prompt.

---

# Phase F — Settings (WebOS authenticity, can be later)
(Not required for early milestones, but registry model supports it.)

Add a “Just Type Settings” screen:
- toggle providers enabled
- reorder within category
- select default search engine

Persist:
- enabled/orderIndex in Room
- defaultSearchProviderId in DataStore

---

# Milestone Implementation Checklist (Codex-friendly)
## Milestone 1 (APPS only, section UI)
- [ ] Add JustType models (A1)
- [ ] Refactor SearchOverlay to sections (A2)
- [ ] Add minimal engine that outputs one APPS section
- [ ] Wire LauncherActivity to new state

## Milestone 2 (ACTIONS)
- [ ] Add ActionsProvider
- [ ] Add ViewModel click mapping for actions

## Milestone 3 (SEARCH templates)
- [ ] Add SearchTemplatesProvider (hardcoded providers initially)
- [ ] Show “Search using …” section at bottom

## Milestone 4 (CONTACTS)
- [ ] Add ContactsProvider + debounce
- [ ] Add permission event flow + Activity permission prompt
- [ ] Add contact click intent

## Milestone 5 (REGISTRY)
- [ ] Add Room table + DAO + migration
- [ ] Add defaults.json + initializer with WebOS merge rules
- [ ] Add DataStore defaultSearchProviderId
- [ ] Switch providers to be driven by registry enable/order/default

---

# Acceptance Criteria
Functional:
- Empty query shows Quick Actions + apps favorites/recents
- Typing shows Apps matches + Contacts matches (if permitted)
- “Search using …” at bottom when query non-empty
- All clicks route correctly
- Missing contacts permission degrades gracefully

Non-functional:
- No UI jank while typing (debounce contacts query)
- Stable ordering (no jitter)
- Compose lists keyed with stable IDs

---

## Notes on WebOS faithfulness
- Registry + merge rules (version overwrite while preserving enabled) mirrors SearchItemsManager.
- Search templates behave like OpenSearchHandler (normalize templates; do not “rank” inside provider).
- Categories and section output mirror UniversalSearchService list separation.
