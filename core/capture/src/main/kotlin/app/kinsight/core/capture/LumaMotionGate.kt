package app.kinsight.core.capture

/**
 * Deterministic luma frame-diff motion gate (DESIGN.md cost tier 1).
 *
 * Runs on every frame, 24/7, at ~zero cost: the pose pipeline only spins up
 * while this gate is open. The gate opens when enough pixels change by more
 * than [pixelDeltaThreshold] and closes again after [quietMsToClose] without
 * motion.
 *
 * State holder by design (previous frame + open/close state); all decisions
 * are pure functions of consecutive frames.
 */
class LumaMotionGate(
    private val pixelDeltaThreshold: Int = DEFAULT_PIXEL_DELTA_THRESHOLD,
    private val minChangedFraction: Double = DEFAULT_MIN_CHANGED_FRACTION,
    private val quietMsToClose: Long = DEFAULT_QUIET_MS_TO_CLOSE,
) {
    private var previous: LumaFrame? = null
    private var lastMotionAtMs: Long? = null

    /** Feeds one frame; returns whether the gate is open after this frame. */
    fun process(frame: LumaFrame): Boolean {
        val baseline = previous
        previous = frame
        if (baseline == null || baseline.pixelCount != frame.pixelCount) {
            // First frame or resolution change: no diff possible, gate stays closed.
            return isOpenAt(frame.timestampMs)
        }
        if (changedFraction(baseline, frame) >= minChangedFraction) {
            lastMotionAtMs = frame.timestampMs
        }
        return isOpenAt(frame.timestampMs)
    }

    /** True when motion was seen within [quietMsToClose] of [nowMs]. */
    fun isOpenAt(nowMs: Long): Boolean {
        val lastMotion = lastMotionAtMs ?: return false
        return nowMs - lastMotion < quietMsToClose
    }

    private fun changedFraction(
        before: LumaFrame,
        after: LumaFrame,
    ): Double {
        var changed = 0
        for (i in after.luma.indices) {
            val delta = (after.luma[i].toInt() and LUMA_MASK) - (before.luma[i].toInt() and LUMA_MASK)
            if (delta >= pixelDeltaThreshold || -delta >= pixelDeltaThreshold) changed++
        }
        return changed.toDouble() / after.pixelCount
    }

    companion object {
        /** Minimum per-pixel luma delta (0-255) counted as change. */
        const val DEFAULT_PIXEL_DELTA_THRESHOLD: Int = 18

        /** Fraction of pixels that must change for the gate to open. */
        const val DEFAULT_MIN_CHANGED_FRACTION: Double = 0.02

        /** Quiet period after the last motion before the gate closes. */
        const val DEFAULT_QUIET_MS_TO_CLOSE: Long = 10_000L

        private const val LUMA_MASK: Int = 0xFF
    }
}
