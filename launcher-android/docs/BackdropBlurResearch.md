# Backdrop Blur Research — Luna Launcher

## Problem Statement

We want frosted glass / backdrop blur on GlassSurface components. The blur should affect
what's **behind** the surface (the wallpaper), not the content inside it.

## Constraints

- Kotlin 1.9.24, Compose BOM 2024.06.00, minSdk 30, targetSdk 34
- Launcher app — we own the home screen but NOT the wallpaper pixels
- Must work across API 30–35+ (Android 11–16)

---

## Approaches Investigated

### 1. Modifier.blur() (Compose)

- **What it does**: Blurs the composable node and all its children
- **API**: 31+ (silently ignored on 30)
- **Verdict**: DOES NOT WORK for backdrop blur. This is CSS `filter: blur()`, not
  `backdrop-filter: blur()`. Applying it to GlassSurface blurs the icons/text inside,
  not the wallpaper behind. We already tried this — it rendered as a smeared layer on
  top of everything.

### 2. WallpaperManager.getDrawable()

- **What it does**: Returns a Drawable/Bitmap of the current wallpaper
- **API**: Available since API 1
- **The idea**: Capture wallpaper bitmap → pre-blur it → draw blurred copy behind glass regions
- **Verdict**: BROKEN on Android 16 (API 36) due to scoped storage restrictions.
  Already attempted — bitmap access fails. Even on older APIs, this is a static
  snapshot that doesn't handle live wallpapers or wallpaper scrolling.

### 3. Haze Library (chrisbanes/haze)

- **What it does**: Compose Multiplatform backdrop blur library using GraphicsLayer APIs
- **Latest version**: 1.7.2 (Feb 2026)
- **Requirements**: Kotlin 2.2.20, Compose Multiplatform 1.9.3 / Jetpack Compose 1.9.4
- **Verdict**: INCOMPATIBLE with our stack. We're on Kotlin 1.9.24 and Compose BOM
  2024.06.00 (~Compose 1.6.x). Would require upgrading Kotlin by two major versions
  and Compose by several releases. Older Haze versions (0.6.x) used the old
  `haze-jetpack-compose` artifact but still had higher Compose requirements.
- **How it works internally**: Uses `GraphicsLayer` APIs (Compose 1.7+) to capture
  rendered content into a layer, blur it via platform RenderNode, and composite.
  On Android <12, falls back to scrim (no blur). On Android 11, has experimental
  RenderScript path.
- **Source**: https://github.com/chrisbanes/haze
- **Docs**: https://chrisbanes.github.io/haze/

### 4. Window.setBackgroundBlurRadius() (API 31+)

- **What it does**: System-level blur applied to the window background
- **API**: 31+ (Android 12)
- **Worth investigating**: This blurs what's behind the entire window (including
  wallpaper) at the compositor level. Works differently from Modifier.blur().
  Available via `window.setBackgroundBlurRadius(radiusPx)`. The system compositor
  (SurfaceFlinger) handles the blur — no pixel access needed.
- **Limitation**: Applies to the entire window, not individual composables. Would need
  creative use of transparent regions vs. opaque regions to control where blur appears.
- **Launcher relevance**: Since our window is already transparent (`windowShowWallpaper`),
  this could potentially blur the wallpaper behind transparent regions.

### 5. WindowManager.LayoutParams.FLAG_BLUR_BEHIND

- **What it does**: Legacy flag for blur behind a window
- **API**: Deprecated, behavior varies by OEM
- **Verdict**: Unreliable. Not worth pursuing.

### 6. PixelCopy API

- **What it does**: Captures pixels from a Surface/Window into a Bitmap
- **API**: 26+ (expanded in 34)
- **The idea**: Capture screen content → blur → draw behind glass
- **Limitation**: Only captures your own window's content, not the wallpaper surface
  behind it. Same fundamental problem as Modifier.blur().

### 7. RenderEffect.createBlurEffect() (API 31+)

- **What it does**: GPU-accelerated Skia blur via RenderNode
- **API**: 31+
- **In Compose**: Accessible via `graphicsLayer { renderEffect = ... }`
- **Verdict**: Same problem as Modifier.blur() — blurs the node it's applied to,
  not what's behind it. However, could be used to blur a captured bitmap efficiently
  if we can get one.

### 8. How Other Launchers Do It

- **Nova Launcher**: Uses WallpaperManager bitmap capture + pre-blur. Static approach.
- **Lawnchair**: Same WallpaperManager approach. Has had issues on newer Android versions.
- **Most launchers**: Fake it with heavy alpha tint + noise texture when blur unavailable.

---

## Promising Unexplored Paths

### Window.setBackgroundBlurRadius() (API 31+)
The most promising system API. Since our launcher window sits directly over the wallpaper
surface, this could blur the wallpaper at the compositor level without ever touching
wallpaper pixels. Needs investigation for:
- Per-region control (can we use it selectively for glass areas?)
- Interaction with `windowShowWallpaper=true`
- Whether the blur radius can be dynamic

### SurfaceControl APIs
Android's `SurfaceControl` (API 29+) gives lower-level access to the compositor.
`SurfaceControl.Transaction.setBackgroundBlurRadius()` could potentially apply blur
to specific surface regions. Needs deep investigation.

### Skia Shader + Noise (Fake It Well)
If real blur is impossible across all API levels, a combination of:
- Semi-transparent tinted background (already have)
- Depth gradient (already have)
- Noise texture overlay (subtle grain = frosted glass feel)
- Specular highlight (radial gradient light catch)

...can convincingly fake frosted glass without any pixel capture. This is what iOS
actually does — the blur is secondary to the tint + noise + saturation.

---

## Recommendation

**Two-tier strategy**:
1. **API 31+**: Investigate `Window.setBackgroundBlurRadius()` for real compositor blur
2. **API 30 / fallback**: Enhanced faux-glass (noise + specular + current gradient/tint)

The `glassBlurRadius` theme token already exists and can drive both tiers.

---

## Sources

- Haze GitHub: https://github.com/chrisbanes/haze
- Haze Docs: https://chrisbanes.github.io/haze/
- Haze Platform Support: https://chrisbanes.github.io/haze/latest/platforms/
- Haze Releases: https://github.com/chrisbanes/haze/releases
