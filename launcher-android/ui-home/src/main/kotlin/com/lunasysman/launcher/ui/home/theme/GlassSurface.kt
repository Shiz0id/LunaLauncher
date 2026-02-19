package com.lunasysman.launcher.ui.home.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A glass-like surface using the iOS layered glass stack:
 *
 *   1. Base tint      — semi-transparent background colour (the "smoke")
 *   2. Noise texture  — fine white grain at low alpha, breaks visual flatness
 *   3. Specular edge  — narrow top-edge gradient simulating a light source above
 *   4. Ambient glow   — radial gradient from top-centre, soft fill light
 *   5. Stroke/border  — thin white rim defining the shape boundary
 *
 * All effect intensities are driven by theme tokens so SMOKY_GLASS and
 * CRYSTAL_GLASS can be tuned independently.
 *
 * Noise is generated procedurally from a fixed seed so it is stable across
 * recompositions with zero I/O overhead. The tile is 1024×1024 logical pixels
 * (~4 MB ARGB_8888) and repeated across the surface — retina-grade resolution
 * where individual dots map ~1:1 with physical pixels on high-DPI panels,
 * with only 2-3 drawImage() calls needed to cover a typical screen.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    backgroundColor: Color,
    showDepthGradient: Boolean = true,   // kept for API compatibility — controls specular + glow
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LauncherTheme.colors

    val noiseAlpha            = colors.glassNoiseAlpha
    val specularAlpha         = if (showDepthGradient) colors.glassSpecularAlpha         else 0f
    val specularFraction      = colors.glassSpecularHeightFraction
    val ambientGlowAlpha      = if (showDepthGradient) colors.glassAmbientGlowAlpha      else 0f
    val luminanceGradientAlpha = if (showDepthGradient) colors.glassLuminanceGradientAlpha else 0f
    val innerRimAlpha         = if (showDepthGradient) colors.glassInnerRimAlpha         else 0f
    val strokeWidth           = colors.glassStrokeWidth
    val strokeColor           = colors.glassStrokeColor

    // Pre-render the noise tile to a bitmap once per unique alpha value.
    // Previously this drew 320 individual rects per 48px tile per frame, which
    // at full-screen resolution meant ~400K drawRect() calls per GlassSurface.
    // With 14+ surfaces on screen, that caused ANRs (9M+ page faults).
    // Now we rasterize a 1024×1024 tile once into an ImageBitmap (~4 MB) and
    // stamp it with drawImage(). At this size the grain approaches 1:1 with
    // physical pixels on high-DPI panels (retina-grade texture) and only 2-3
    // tiles are needed to cover a full 1440×3200 display.
    val noiseTile = remember(noiseAlpha) {
        if (noiseAlpha <= 0f) return@remember null
        val tilePx = 1024
        val bitmap = ImageBitmap(tilePx, tilePx)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.White
            alpha = noiseAlpha
        }
        val rng = Random(seed = 0x4C554E41) // "LUNA" — stable across compositions
        repeat(80_000) {
            val x = rng.nextFloat() * tilePx
            val y = rng.nextFloat() * tilePx
            canvas.drawRect(
                left = x,
                top = y,
                right = x + 1f,
                bottom = y + 1f,
                paint = paint,
            )
        }
        bitmap
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                // ── 2. NOISE TEXTURE ─────────────────────────────────────────
                // Tile the pre-rendered 1024×1024 noise bitmap across the surface.
                val tilePx = 1024
                val cols = (size.width  / tilePx).toInt() + 1
                val rows = (size.height / tilePx).toInt() + 1

                // ── 3. SPECULAR EDGE (top strip) ─────────────────────────────
                val specularHeight = size.height * specularFraction
                val specularBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = specularAlpha),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY   = specularHeight,
                )

                // ── 4. AMBIENT GLOW (radial, top-centre) ─────────────────────
                val glowRadius = size.width * 0.60f
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = ambientGlowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(x = size.width / 2f, y = 0f),
                    radius = glowRadius,
                )

                // ── 5. LUMINANCE GRADIENT (full-height, Aero depth) ──────────
                val luminanceBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = luminanceGradientAlpha),
                        Color.Black.copy(alpha = luminanceGradientAlpha * 0.75f),
                    ),
                    startY = 0f,
                    endY   = size.height,
                )

                // ── 6. INNER RIM ─────────────────────────────────────────────
                val innerRimY = 1f

                onDrawBehind {
                    // 1 — base
                    drawRect(backgroundColor)

                    // 2 — noise (tile the pre-rendered bitmap)
                    val tile = noiseTile
                    if (tile != null) {
                        clipRect {
                            for (col in 0 until cols) {
                                for (row in 0 until rows) {
                                    drawImage(
                                        image = tile,
                                        dstOffset = IntOffset(col * tilePx, row * tilePx),
                                        dstSize = IntSize(tilePx, tilePx),
                                    )
                                }
                            }
                        }
                    }

                    // 3 — specular edge
                    if (specularAlpha > 0f) drawRect(brush = specularBrush)

                    // 4 — ambient glow
                    if (ambientGlowAlpha > 0f) drawRect(brush = glowBrush)

                    // 5 — luminance gradient (Aero full-height depth)
                    if (luminanceGradientAlpha > 0f) drawRect(brush = luminanceBrush)

                    // 6 — inner rim shadow
                    if (innerRimAlpha > 0f) {
                        drawLine(
                            color       = Color.Black.copy(alpha = innerRimAlpha),
                            start       = Offset(0f, innerRimY),
                            end         = Offset(size.width, innerRimY),
                            strokeWidth = 1f,
                        )
                    }
                }
            }
            .then(
                if (strokeWidth > 0.dp) Modifier.border(strokeWidth, strokeColor, shape) else Modifier
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

