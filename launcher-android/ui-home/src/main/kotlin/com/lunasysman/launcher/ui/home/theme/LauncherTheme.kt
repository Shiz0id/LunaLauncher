package com.lunasysman.launcher.ui.home.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunasysman.launcher.core.model.LauncherColorTheme
import com.lunasysman.launcher.core.model.LauncherThemeStyle

private val HOME_GUTTER_SCRIM: Color = Color.Black.copy(alpha = 0.78f)

/** Default tint strength — 1/3 reproduces the original glass alpha values. */
const val DEFAULT_HOME_TINT_STRENGTH: Float = 1f / 3f

@Immutable
data class LauncherColorTokens(
    val homeSearchBarBackground: Color,
    val homeGutterScrim: Color,
    val dockBackground: Color,
    val homeEditBarBackground: Color,
    val homeEditBarPrimaryText: Color,
    val homeEditBarSecondaryText: Color,
    val justTypeResultsPanelBackground: Color,
    val justTypeSectionCardBackground: Color,
    val justTypeRowDivider: Color,
    val justTypeChipBackground: Color,
    val justTypePillBackground: Color,
    val justTypeFirstItemHighlight: Color,
    val notificationLiveBadgeColor: Color,
    val notificationHistoricalBadgeColor: Color,
    val glassBlurRadius: Dp,
    val glassStrokeColor: Color,
    val glassStrokeWidth: Dp,
    // iOS-style layered glass effect parameters
    val glassNoiseAlpha: Float,               // grain texture intensity (0..1)
    val glassSpecularAlpha: Float,            // top-edge specular highlight intensity (0..1)
    val glassSpecularHeightFraction: Float,   // fraction of surface height the specular covers (0..1)
    val glassAmbientGlowAlpha: Float,         // radial top-center ambient glow intensity (0..1)
    val glassLuminanceGradientAlpha: Float,   // full-height light-top/dark-bottom depth gradient (0=off)
    val glassInnerRimAlpha: Float,            // 1px dark line just inside top border for glass-rim depth (0=off)
    val deckForegroundCardBackground: Color,
    val justTypeScrim: Color,
    val justTypePanelBacking: Color,          // solid backing behind the search bar + results panel
    val homeBarBacking: Color,                // solid backing behind dock, edit bar, and inactive search bar
    val allAppsSearchBarBackground: Color,
    val allAppsBackgroundTop: Color,
    val allAppsBackgroundBottom: Color,
)

private val LocalLauncherColors = staticCompositionLocalOf {
    // Fallback — should never be read in practice because LunaLauncherTheme wraps the tree.
    buildTokens(LauncherThemeStyle.SMOKY_GLASS, LauncherColorTheme.SMOKE)
}

object LauncherTheme {
    val colors: LauncherColorTokens
        @Composable get() = LocalLauncherColors.current
}

@Composable
fun LunaLauncherTheme(
    style: LauncherThemeStyle,
    colorTheme: LauncherColorTheme = LauncherColorTheme.SMOKE,
    homeTintStrength: Float = DEFAULT_HOME_TINT_STRENGTH,
    content: @Composable () -> Unit,
) {
    val colors = buildTokens(style, colorTheme, homeTintStrength)
    CompositionLocalProvider(LocalLauncherColors provides colors) {
        content()
    }
}

// ── Token builder ───────────────────────────────────────────────────────────────

private fun buildTokens(
    glass: LauncherThemeStyle,
    colorTheme: LauncherColorTheme,
    homeTintStrength: Float = DEFAULT_HOME_TINT_STRENGTH,
): LauncherColorTokens {
    val primary = Color(colorTheme.primaryArgb)
    // homeTintStrength 0..1 maps to 0..3× the base glass alpha.
    // Default (1/3) reproduces the original glass values.
    val tintScale = homeTintStrength.coerceIn(0f, 1f) * 3f

    // Alpha profiles — the glass type controls how transparent the tinted surfaces are.
    val smoky = glass == LauncherThemeStyle.SMOKY_GLASS

    // 7 themed background tokens (primary color × glass-type alpha)
    // The first 3 (search bar, dock, edit bar) are scaled by tintScale.
    val searchBarBg      = primary.copy(alpha = ((if (smoky) 0.30f else 0.22f) * tintScale).coerceAtMost(1f))
    val dockBg           = primary.copy(alpha = ((if (smoky) 0.30f else 0.22f) * tintScale).coerceAtMost(1f))
    val editBarBg        = primary.copy(alpha = ((if (smoky) 0.30f else 0.22f) * tintScale).coerceAtMost(1f))
    val resultsPanelBg   = primary.copy(alpha = if (smoky) 0.32f else 0.26f)
    val sectionCardBg    = primary.copy(alpha = if (smoky) 0.38f else 0.32f)
    val deckCardBg       = primary.copy(alpha = if (smoky) 0.35f else 0.32f)
    val allAppsSearchBg  = primary.copy(alpha = if (smoky) 0.24f else 0.20f)

    // Non-themed tokens — these depend only on the glass type.
    return if (smoky) {
        LauncherColorTokens(
            homeSearchBarBackground         = searchBarBg,
            homeGutterScrim                 = HOME_GUTTER_SCRIM,
            dockBackground                  = dockBg,
            homeEditBarBackground           = editBarBg,
            homeEditBarPrimaryText          = Color.White.copy(alpha = 0.92f),
            homeEditBarSecondaryText        = Color.White.copy(alpha = 0.70f),
            justTypeResultsPanelBackground  = resultsPanelBg,
            justTypeSectionCardBackground   = sectionCardBg,
            justTypeRowDivider              = Color.Black.copy(alpha = 0.27f),
            justTypeChipBackground          = Color.White.copy(alpha = 0.15f),
            justTypePillBackground          = Color.White.copy(alpha = 0.18f),
            justTypeFirstItemHighlight      = Color(0xFF6FAEDB).copy(alpha = 0.35f),
            notificationLiveBadgeColor      = Color(0xFF6FAEDB),
            notificationHistoricalBadgeColor = Color(0xFF888888),
            glassBlurRadius                 = 12.dp,
            glassStrokeColor                = Color.White.copy(alpha = 0.13f),
            glassStrokeWidth                = 0.5.dp,
            glassNoiseAlpha                 = 0.04f,
            glassSpecularAlpha              = 0.18f,
            glassSpecularHeightFraction     = 0.30f,
            glassAmbientGlowAlpha           = 0.10f,
            glassLuminanceGradientAlpha     = 0f,
            glassInnerRimAlpha              = 0f,
            deckForegroundCardBackground    = deckCardBg,
            justTypeScrim                   = Color(0xFF0A1628).copy(alpha = 0.50f),
            justTypePanelBacking            = Color(0xFF1E2226).copy(alpha = 0.50f),
            homeBarBacking                  = Color(0xFF1E2226).copy(alpha = 0.50f),
            allAppsSearchBarBackground      = allAppsSearchBg,
            allAppsBackgroundTop            = Color(0xFF2C3136).copy(alpha = 0.93f),
            allAppsBackgroundBottom          = Color(0xFF1E2226).copy(alpha = 0.93f),
        )
    } else {
        LauncherColorTokens(
            homeSearchBarBackground         = searchBarBg,
            homeGutterScrim                 = HOME_GUTTER_SCRIM,
            dockBackground                  = dockBg,
            homeEditBarBackground           = editBarBg,
            homeEditBarPrimaryText          = Color.White,
            homeEditBarSecondaryText        = Color.White.copy(alpha = 0.90f),
            justTypeResultsPanelBackground  = resultsPanelBg,
            justTypeSectionCardBackground   = sectionCardBg,
            justTypeRowDivider              = Color.White.copy(alpha = 0.18f),
            justTypeChipBackground          = Color.White.copy(alpha = 0.26f),
            justTypePillBackground          = Color.White.copy(alpha = 0.30f),
            justTypeFirstItemHighlight      = Color(0xFF6FAEDB).copy(alpha = 0.40f),
            notificationLiveBadgeColor      = Color(0xFF6FAEDB),
            notificationHistoricalBadgeColor = Color(0xFF888888),
            glassBlurRadius                 = 10.dp,
            glassStrokeColor                = Color.White.copy(alpha = 0.35f),
            glassStrokeWidth                = 1.dp,
            glassNoiseAlpha                 = 0.05f,
            glassSpecularAlpha              = 0.32f,
            glassSpecularHeightFraction     = 0.22f,
            glassAmbientGlowAlpha           = 0.18f,
            glassLuminanceGradientAlpha     = 0.08f,
            glassInnerRimAlpha              = 0.15f,
            deckForegroundCardBackground    = deckCardBg,
            justTypeScrim                   = Color(0xFF2A2A2A).copy(alpha = 0.50f),
            justTypePanelBacking            = Color(0xFF1E1E1E).copy(alpha = 0.60f),
            homeBarBacking                  = Color(0xFF1E1E1E).copy(alpha = 0.50f),
            allAppsSearchBarBackground      = allAppsSearchBg,
            allAppsBackgroundTop            = Color(0xFF2A3036).copy(alpha = 0.93f),
            allAppsBackgroundBottom          = Color(0xFF1B2024).copy(alpha = 0.93f),
        )
    }
}
