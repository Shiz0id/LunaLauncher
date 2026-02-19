# Plan: Custom Glow Text Library for Luna Launcher Search Results

## Context

The Luna Launcher uses bright, vibrant themes (especially CRYSTAL_GLASS with icy blue-white backgrounds) that can make search result text less readable, particularly when text has low contrast against certain backgrounds. The Windows 7 Aero aesthetic that inspired the launcher's glass effects also featured soft text glows that improved readability while maintaining visual elegance.

This plan creates a reusable `ui-glow` module that implements a soft outer halo glow effect for text, initially targeting search result titles and subtitles, with theme-aware configuration to allow future brightly-colored themes to opt-in.

## Goals

1. **Improve readability** of search results in bright themes without altering core text styling
2. **Maintain Windows 7 Aero aesthetic** with soft, subtle glow (not neon or harsh)
3. **Theme-configurable** — allow each theme variant to customize or disable glow
4. **Reusable library** — separate module enables future use cases beyond search
5. **Performance-conscious** — efficient Canvas-based implementation, cached where possible

## Approach: Soft Outer Halo Implementation

### Windows 7 Glow Effect Mechanics

Windows 7 text glow achieves readability through:
- **Multiple blurred shadow layers** rendered *behind* text at decreasing opacity
- **Soft falloff** — no sharp edges, multiple concentric rings at progressively lower alpha
- **Color matching text** — glow inherits text color but at lower opacity
- **Subtle intensity** — glow blurs 4-8 pixels outward, not dramatic

### Implementation Strategy in Compose

Since Compose's `Text` composable doesn't natively support shadow/glow, we'll use blur-based shadow rendering:

1. **Use `graphicsLayer` with `RenderEffect` (API 31+)** to apply blur-based shadow:
   - Render text to an offscreen layer
   - Apply blur effect via `RenderEffect.createBlurEffect()` or custom blur paint
   - Composite back with alpha blending
   - Fallback: For API <31, use concentric rings (see below)

2. **Create reusable `Modifier.glowText()`** that:
   - Takes glow color, blur radius, and opacity parameters from theme tokens
   - Applies blur effect to a shadow layer rendered beneath text
   - Let Compose's text rendering happen on top naturally

3. **Integrate into theme system** with new `GlowTextConfig` tokens in `LauncherTheme.kt`

4. **Wrap in `GlowText()` composable** helper that applies modifier automatically

5. **Apply to search results** in `JustType.kt` for titles and subtitles

### Why This Approach

- **Accurate to Windows 7 Aero** — Uses blur-based shadows, matching original design
- **Theme-aware by design** — tokens in `LauncherTheme.kt` allow per-theme configuration
- **Composable-level control** — can be applied selectively to any Text without global changes
- **API level handling** — Uses `RenderEffect` for API 31+ (modern Android), ring fallback for SDK 30
- **Performance** — Blur is computed once at composition, not per-frame

## Module Structure

### New Module: `ui-glow`

```
ui-glow/
├── src/main/kotlin/com/lunasysman/launcher/ui/glow/
│   ├── GlowText.kt              # Main composable + modifier
│   ├── GlowTextConfig.kt        # Data class for glow parameters
│   └── GlowTextDefaults.kt      # Default glow values, theme integration
├── build.gradle.kts             # Dependencies: Compose, core-model
└── README.md                    # Usage guide
```

### Build Configuration

- **Depends on**: `:core-model` (none), Compose UI (`androidx.compose.ui:ui`)
- **Depended on by**: `:ui-home` (for search results)
- **No Compose Material** — only low-level Canvas/drawing APIs

### Files to Modify

1. **`CLAUDE.md`** — Add ui-glow to module map and dependency diagram
2. **`ui-home/src/main/kotlin/.../theme/LauncherTheme.kt`** — Add glow tokens to theme config
3. **`ui-home/src/main/kotlin/.../JustType.kt`** — Replace `Text()` calls with `GlowText()` for result titles/subtitles
4. **`app/build.gradle.kts`** — Add `:ui-glow` as dependency (or `:ui-home` adds it as transitive)

## Implementation Details

### 1. `ui-glow/src/main/kotlin/com/lunasysman/launcher/ui/glow/GlowText.kt`

```kotlin
// Main composable that wraps Material Text with glow
fun GlowText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle? = null,
    glowConfig: GlowTextConfig = GlowTextDefaults.config(),
) {
    val glowModifier = modifier.glowText(color, glowConfig)

    Text(
        text = text,
        modifier = glowModifier,
        color = color,
        fontSize = fontSize,
        // ... other params ...
        style = style,
    )
}

// Reusable modifier for any Text
fun Modifier.glowText(
    textColor: Color,
    glowConfig: GlowTextConfig,
): Modifier = if (glowConfig.enabled && android.os.Build.VERSION.SDK_INT >= 31) {
    graphicsLayer {
        // Apply blur effect using RenderEffect for API 31+ (native GPU blur)
        val blurRadiusPx = glowConfig.blurRadius.dp.toPx()
        renderEffect = RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.CLAMP
        )
    }
} else if (glowConfig.enabled) {
    // Fallback for API <31: render concentric rings for glow approximation
    drawBehind {
        // Draw 4 concentric circles at increasing radius with decreasing alpha
        // Approximates blur effect via ring layering (Windows 7 fallback approach)
        val blurRadiusPx = glowConfig.blurRadius.dp.toPx()
        val baseAlpha = glowConfig.shadowAlpha
        for (i in 1..4) {
            drawCircle(
                color = textColor.copy(alpha = baseAlpha * (1f - i / 5f)),
                radius = blurRadiusPx * (i / 2f)
            )
        }
    }
} else {
    this
}
```

### 2. `ui-glow/src/main/kotlin/.../GlowTextConfig.kt`

```kotlin
data class GlowTextConfig(
    val enabled: Boolean = true,
    val blurRadius: Float = 4f,          // dp, blur radius for RenderEffect (API 31+) or ring radius (API <31)
    val shadowAlpha: Float = 0.15f,      // opacity of glow layer
    val shadowOffsetY: Float = 1f,       // vertical offset of glow (dp) — future use for drop shadow
)
```

### 3. `ui-glow/src/main/kotlin/.../GlowTextDefaults.kt`

```kotlin
object GlowTextDefaults {
    fun config(
        enabled: Boolean = true,
        blurRadius: Float = 4f,
        shadowAlpha: Float = 0.15f,
        shadowOffsetY: Float = 1f,
    ) = GlowTextConfig(enabled, blurRadius, shadowAlpha, shadowOffsetY)
}
```

### 4. Theme Integration in `LauncherTheme.kt`

Add to `LauncherThemeConfig`:

```kotlin
val glowTextConfig: GlowTextConfig,
```

Populate in theme builders:

```kotlin
// SMOKY_GLASS: Glow disabled (text clear, subtle theme)
glowTextConfig = GlowTextDefaults.config(enabled = false),

// CRYSTAL_GLASS: Glow enabled (bright Aero theme, improve contrast)
glowTextConfig = GlowTextDefaults.config(
    enabled = true,
    blurRadius = 5f,      // 5 pixel blur radius
    shadowAlpha = 0.18f,  // Slightly stronger for bright background
    shadowOffsetY = 1f,   // Subtle downward offset
),
```

### 5. Usage in `JustType.kt`

Replace existing `Text()` calls:

```kotlin
// Before:
Text(
    text = title,
    style = MaterialTheme.typography.bodyMedium,
    color = Color.White.copy(alpha = 0.92f),
)

// After:
GlowText(
    text = title,
    style = MaterialTheme.typography.bodyMedium,
    color = Color.White.copy(alpha = 0.92f),
    glowConfig = LauncherTheme.glowTextConfig,  // Inject from theme
)
```

Apply to:
- Result titles (line ~403 in JustType.kt)
- Result subtitles (line ~411 in JustType.kt)
- Section headers (line ~343 in JustType.kt) — optional
- Notification titles (line ~527 in JustType.kt) — optional

## Critical Design Decisions

### 1. Glow Rendering Technique: RenderEffect (API 31+) with Concentric Rings (API <31)

**Hybrid approach:**

**Primary (API 31+): `graphicsLayer` + `RenderEffect.createBlurEffect()`**
- Uses native Android blur implementation (GPU-accelerated)
- Smooth, authentic blur-based glow appearance
- Matches Windows 7's original shadow blur technique
- Clean API, minimal code, zero performance cost

**Fallback (API <31, SDK 30): Concentric rings approximation**
- Draw 4 concentric circles of decreasing radius and alpha around text
- Approximates blur effect via layered rings (old Windows 7 fallback approach)
- Fast, compatible with older Android versions
- Less smooth than blur but still visually effective

**Decision**: Use hybrid approach. API 31+ gets true Windows 7 blur via RenderEffect. SDK 30 devices get ring-based approximation (no blur computation needed). Best of both worlds: authenticity on modern devices, compatibility on older ones.

### 2. Glow Shadow Color Strategy

- Windows 7 Aero uses subtle shadows (often dark) beneath text for depth
- In Luna's case with white text on dark/translucent backgrounds, a white-tinted shadow works best
- The blurred shadow layer will be semi-transparent, creating a subtle halo effect
- **Decision**: Shadow color = text color (inherits white), alpha controlled via `shadowAlpha` token (15-18%)

### 3. Per-Text Control vs. Global Fallback

- Some text should NOT have glow (too cluttered, e.g., small secondary labels)
- **Decision**: Make `glowConfig` a parameter to `GlowText()`, defaulting to theme's `glowTextConfig`. Callers can override per-text.

## Testing & Verification

### 1. Visual Verification
- Build and run on API 31+ emulator/device (or API 30 device to test fallback)
- With CRYSTAL_GLASS theme, confirm search results show soft blurred glow beneath/around text
- Confirm text remains readable at all sizes (12sp to 20sp) with glow
- Compare against SMOKY_GLASS (should have no glow or very subtle)
- Verify glow appearance matches Windows 7 Aero aesthetic (soft, not harsh)

### 2. Performance Testing
- Search for a query that returns 10+ results
- Monitor frame rate / GPU usage in Android Profiler
- Confirm no jank or performance regression vs. non-glowing text

### 3. Edge Cases
- Text truncation with ellipsis — glow should not extend beyond text bounds
- Single-line vs. multi-line text — glow should work both ways
- Dynamic text color changes — glow should follow color smoothly
- Dark wallpapers vs. light wallpapers — glow visibility should be reasonable both ways

### 4. Integration Testing
- `GlowText()` composable instantiation
- `Modifier.glowText()` applied correctly
- Theme tokens populated for both variants
- Glow disabled in SMOKY_GLASS, enabled in CRYSTAL_GLASS
- JustType search results display with glow

## Dependencies & Constraints

- **Gradle**: No new dependencies required (Compose UI + `android.graphics.RenderEffect` already available in AGP 8.7)
- **Min SDK**: No change to project minimum (SDK 30)
  - API 31+: Uses native RenderEffect blur (GPU-accelerated, true Windows 7 blur)
  - SDK 30: Uses concentric rings approximation (compatible, performant)
  - Both approaches produce visible, effective glow
- **Kotlin version**: No change (plain Kotlin, no special language features)
- **Architecture**: Follows existing patterns (theme tokens from `LauncherTheme.kt`, composable-level modifiers)

## Implementation Order

1. **Phase 1**: Create `ui-glow` module with `GlowText.kt`, `GlowTextConfig.kt`, `GlowTextDefaults.kt`
2. **Phase 2**: Integrate into `LauncherTheme.kt` — add glow tokens to both theme variants
3. **Phase 3**: Wire `JustType.kt` to use `GlowText()` for result titles/subtitles
4. **Phase 4**: Test visually on device with both themes
5. **Phase 5**: Update `CLAUDE.md` and create `docs/GlowText.md` for future reference

## File Paths Summary

### New Files
- `ui-glow/build.gradle.kts`
- `ui-glow/src/main/kotlin/com/lunasysman/launcher/ui/glow/GlowText.kt`
- `ui-glow/src/main/kotlin/com/lunasysman/launcher/ui/glow/GlowTextConfig.kt`
- `ui-glow/src/main/kotlin/com/lunasysman/launcher/ui/glow/GlowTextDefaults.kt`
- `ui-glow/README.md`

### Modified Files
- `CLAUDE.md` — Update module map and dependency diagram
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/theme/LauncherTheme.kt` — Add glow tokens
- `ui-home/src/main/kotlin/com/lunasysman/launcher/ui/home/JustType.kt` — Replace Text with GlowText
- `app/build.gradle.kts` — Add `:ui-glow` dependency (or `:ui-home` transitively)
- `settings.gradle.kts` — Include `:ui-glow` module

### Documentation Updates
- `CLAUDE.md` — Update module map, add glow to common tasks

## Success Criteria

- [ ] Search results text displays with visible soft blurred glow in CRYSTAL_GLASS theme (matches Windows 7 Aero)
- [ ] SMOKY_GLASS theme has no glow (theme-configurable disable)
- [ ] GlowText composable is importable and works with all Text parameters
- [ ] Glow effect uses RenderEffect on API 31+ (native GPU blur)
- [ ] Graceful fallback for API 30: text renders with ring glow approximation (performant, visible)
- [ ] No performance regression (frame rate smooth at 60 FPS, no jank on search results)
- [ ] Text remains readable and contrast improves with glow on bright backgrounds
- [ ] Module structure follows existing patterns (separate ui-glow module, proper dependencies)
