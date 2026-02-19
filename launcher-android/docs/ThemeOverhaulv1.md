# Just Type UI & Theme Tokens Exploration

## Context
User wants to review the current Just Type search UI and theme token implementation to evaluate ideas for improvements/changes.

## Current State Summary

### Just Type UI Implementation (Active)

**ui-home module** (`JustType.kt`) — **THE ACTIVE SEARCH UI**:
- Integrated search panel below top search bar on home screen
- Theme-aware via `LauncherTheme` tokens
- Supports both SMOKY_GLASS and CRYSTAL_GLASS variants
- Feature-rich: notification actions visible as pills, section-based layout
- State managed by `LauncherViewModel.setSearchQuery()` → `JustTypeEngine.buildState()`

**ui-search module** (`SearchOverlay.kt`) — **UNUSED LEGACY CODE**:
- Full-screen dialog overlay approach
- Hard-coded colors (not theme-aware)
- ~693 lines with result tiles
- **⚠️ NOT IMPORTED ANYWHERE** — appears to be abandoned design
- Do not use for new search functionality

### Theme Token System (`LauncherTheme.kt`)

**LauncherColorTokens** (18 properties):
- Home search bar, gutter, dock backgrounds
- Just Type panels, section cards, dividers
- Chip & pill backgrounds
- Glass effect styling (blur radius, stroke)
- All Apps backgrounds (top/bottom gradient)

**Two Themes**:
1. **SMOKY_GLASS** (dark, opaque) - default
   - Darker surfaces, lower transparency
   - All Apps: `0xFF2C3136` → `0xFF1E2226`

2. **CRYSTAL_GLASS** (light, acrylic)
   - Light blue-white tint: `Color(0xFFEAF4FF)`
   - Higher transparency (more translucent)
   - Visible stroke: `Color.White.copy(alpha = 0.22f)`, `1.dp` width
   - All Apps: `0xFF2A3036` → `0xFF1B2024` (slightly lighter)

### Material 3 Integration
- Uses Material 3 typography defaults (titleMedium, bodyMedium, labelLarge, etc.)
- No custom color scheme override
- Material components for UI controls

### Key Files
- `LauncherTheme.kt` - Main theme definition
- `GlassSurface.kt` - Reusable glassmorphism wrapper
- `SearchOverlay.kt` - Standalone search (ui-search)
- `JustType.kt` - Integrated search (ui-home)
- `LauncherActivity.kt` - Theme application & persistence

### Current Limitations
1. **Hard-coded colors**: Energy blue `0xFF6FAEDB` appears 5 times in JustType.kt (lines 490, 615, 649, 704, 751) — not a token
2. **No blur implementation**: `glassBackdropBlurRadius = 60.dp` defined in theme but never applied
3. **No focus state token**: Energy blue highlight should be a theme token, not hard-coded
4. **Flat glass aesthetic**: Current SMOKY_GLASS is purely alpha-transparent, lacks depth/specular highlights
5. **No custom shader/graphics**: Could add depth and highlight effects via Canvas or RenderEffect

## User's Vision

**Goal**: Upgrade SMOKY_GLASS from flat alpha transparency to sophisticated glass with:
- **Depth**: Multi-layered blur effect
- **Specular highlights**: Bright spots catching light
- **Premium feel**: More refined, less flat

**Implementation priorities**:
1. Add energy blue as a focus state token (used on first search result item)
2. Implement blur effect for glass surfaces (using `glassBackdropBlurRadius`)
3. Explore depth/highlight techniques (Canvas drawing, gradients, or RenderEffect)
4. Consolidate hard-coded colors to theme tokens

---

## Technical Foundation Available

**Graphics capabilities already in use**:
- ✅ `graphicsLayer` — Used actively in HomeScreen, LauncherActivity, WidgetDeckOverlay
- ✅ Canvas drawing — `drawBehind`, `drawWithCache`, `Path`, `clipPath` used in HomeScreen
- ✅ Brush gradients — Vertical gradients used throughout (AllAppsScreen, JustTypeSettingsActivity)
- ❌ RenderEffect — Not used yet (opportunity for blur)
- ❌ Shader — Not used yet (opportunity for advanced effects)
- ⚠️ Blur — Infrastructure exists (theme token) but never applied to GlassSurface

**Current GlassSurface** (`ui-home/theme/GlassSurface.kt`):
- Only uses `.background()` + `.border()` modifiers
- Does NOT apply `glassBackdropBlurRadius` from theme
- Could be enhanced with blur and/or custom drawing

## Implementation Strategy

### Phase 1: Add Focus State Token
**File to modify**: `ui-home/theme/LauncherTheme.kt`
- Add `justTypeFirstItemFocusBackground: Color` to `LauncherColorTokens` data class
- Define for both SMOKY_GLASS and CRYSTAL_GLASS variants:
  - SMOKY: Energy blue `Color(0xFF6FAEDB).copy(alpha = 0.35f)`
  - CRYSTAL: Energy blue `Color(0xFF6FAEDB).copy(alpha = 0.35f)` (same or adjusted)
- Update GlassSurface to optionally accept focus state
- **Files to update**: `JustType.kt` (replace 5 hard-coded instances at lines 490, 615, 649, 704, 751)

### Phase 2: Blur Implementation (Choose One)

**BLOCKER**: minSdk 30 (Android 11) does NOT support native Compose blur or RenderEffect
- `Modifier.blur()` requires API 31+
- `RenderEffect` requires API 31+
- Both silently ignored on API 30

**Recommended Approach: Conditional Blur with Fallback**
```kotlin
// In GlassSurface.kt, add blur conditionally:
Modifier.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // API 31+
        Modifier.blur(radiusX = 6.dp, radiusY = 6.dp)
    } else {
        // API 30: Fallback to gradient overlay for depth perception
        Modifier  // Will add depth via gradient in next phase
    }
)
```

**Alternative (if full blur on all APIs is critical)**:
- Add Cloudy library dependency (handles blur via NEON/SIMD CPU on API 30, GPU on 31+)
- Single Modifier.cloudy() works across all versions

### Phase 3: Add Depth/Premium Aesthetic
- **Canvas-based specular highlight**: Optional bright spot using `drawCircle` in GlassSurface
- **Gradient overlay**: Subtle top-lighter-bottom-darker gradient for depth perception
- **Combination**: Blur (where available) + gradient + optional highlight

## Critical Files to Modify
1. `ui-home/src/main/kotlin/.../theme/LauncherTheme.kt` — Add focus token
2. `ui-home/src/main/kotlin/.../theme/GlassSurface.kt` — Implement blur + gradient + highlight
3. `ui-home/src/main/kotlin/.../JustType.kt` — Replace hard-coded colors with token

## Verification
- Visual inspection on API 30 (Pixel 4) and API 34 (Pixel 8)
- Ensure SMOKY_GLASS and CRYSTAL_GLASS both look polished
- Check first search result has energy blue focus state
- Test gesture responsiveness (blur should not impact performance)

