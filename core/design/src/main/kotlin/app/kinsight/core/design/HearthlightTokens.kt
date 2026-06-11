package app.kinsight.core.design

/**
 * "Hearthlight" design tokens (DESIGN.md): warm-paper calm, light-only.
 * Ember amber is reserved EXCLUSIVELY for alert states so color carries
 * semantic weight; pine green is the single brand color. Compose theme
 * objects in the apps must derive from these values, never restate them.
 */
object HearthlightTokens {
    /** Oat/cream surface — the default background everywhere. */
    const val SURFACE_OAT: String = "oklch(96% 0.015 85)"

    /** Deep pine green — the single brand color. */
    const val BRAND_PINE: String = "oklch(38% 0.07 165)"

    /** Ember amber — ALERT STATES ONLY (semantic color discipline). */
    const val ALERT_EMBER: String = "oklch(70% 0.16 60)"

    /** Display serif: warmth, domesticity, trust. */
    const val FONT_DISPLAY: String = "Fraunces"

    /** Body face designed for low-vision readers — the demographic demands it. */
    const val FONT_BODY: String = "Atkinson Hyperlegible"

    /** Elder-legible minimum touch target (DESIGN.md: >= 56dp). */
    const val MIN_TOUCH_TARGET_DP: Int = 56
}
