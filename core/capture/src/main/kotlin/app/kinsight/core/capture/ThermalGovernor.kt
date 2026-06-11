package app.kinsight.core.capture

/** Coarse device thermal state as reported by the platform. */
enum class ThermalState { NOMINAL, ELEVATED, CRITICAL }

/**
 * Deterministic capture-rate governor (DESIGN.md flow 4): full pose rate only
 * while the motion gate is open and the device is cool; drops toward ~1 fps
 * when idle or hot so a 24/7 donor phone survives at 100% duty cycle.
 */
object ThermalGovernor {
    const val IDLE_FPS: Int = 1
    const val HOT_FPS: Int = 1
    const val REDUCED_FPS: Int = 5
    const val FULL_FPS: Int = 10

    fun targetFps(
        thermalState: ThermalState,
        isGateOpen: Boolean,
    ): Int {
        if (!isGateOpen) return IDLE_FPS
        return when (thermalState) {
            ThermalState.NOMINAL -> FULL_FPS
            ThermalState.ELEVATED -> REDUCED_FPS
            ThermalState.CRITICAL -> HOT_FPS
        }
    }
}
