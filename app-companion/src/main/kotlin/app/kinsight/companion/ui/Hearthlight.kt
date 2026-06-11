package app.kinsight.companion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kinsight.core.design.HearthlightTokens
import app.kinsight.core.design.OklchColor

/**
 * Compose-side view of the "Hearthlight" tokens — DERIVED from
 * [HearthlightTokens] via the oklch converter, never restated as hex
 * (core/design is the single source of truth).
 */
internal object Hearthlight {
    /** Warm-paper default background. */
    val surfaceOat = Color(OklchColor.parse(HearthlightTokens.SURFACE_OAT).toArgb())

    /** The single brand color. */
    val brandPine = Color(OklchColor.parse(HearthlightTokens.BRAND_PINE).toArgb())

    /** Reserved EXCLUSIVELY for alert states. */
    val alertEmber = Color(OklchColor.parse(HearthlightTokens.ALERT_EMBER).toArgb())

    /** Elder-legible minimum touch target. */
    val minTouchTarget = HearthlightTokens.MIN_TOUCH_TARGET_DP.dp
}
