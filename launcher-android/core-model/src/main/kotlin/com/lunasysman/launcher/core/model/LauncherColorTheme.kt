package com.lunasysman.launcher.core.model

/**
 * Named color themes for the launcher.
 *
 * Each theme provides a [primaryArgb] base color (fully opaque ARGB Long) that
 * is applied at varying alpha levels to the 7 glass background surfaces.
 * The glass *type* (Smoky / Crystal) controls the effect parameters (blur,
 * specular, noise, etc.) independently of the color chosen here.
 */
enum class LauncherColorTheme(
    val displayName: String,
    val primaryArgb: Long,
) {
    SMOKE("Smoke", 0xFF000000),
    AERO("Aero", 0xFF2D82C6),
    SKY("Sky", 0xFF74B8FC),
    TWILIGHT("Twilight", 0xFF0045AC),
    SEA("Sea", 0xFF31CECE),
    LIME("Lime", 0xFF15A600),
    LEAF("Leaf", 0xFF98D937),
    FIRE("Fire", 0xFFFADC0E),
    ORANGE("Orange", 0xFFFF9900),
    RUBY("Ruby", 0xFFCE0F0F),
    FUCHSIA("Fuchsia", 0xFFFF0099),
    BLUSH("Blush", 0xFFFCC7F8),
    VIOLET("Violet", 0xFF6F3CA2),
    LAVENDER("Lavender", 0xFF8C5A94),
    TAUPE("Taupe", 0xFF95814A),
    CHOCOLATE("Chocolate", 0xFF501B1B),
    PRO_BLACK("Pro Black", 0xFF1E1E1E),
}
