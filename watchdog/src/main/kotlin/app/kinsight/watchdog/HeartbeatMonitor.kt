package app.kinsight.watchdog

/** Companion-side view of monitor liveness. */
enum class MonitorLiveness { ONLINE, OFFLINE }

/**
 * Invariant 8 — silence is a signal. The monitor heartbeats every
 * [HEARTBEAT_INTERVAL_MS]; when the companion has seen nothing for more than
 * [OFFLINE_THRESHOLD_MS], "monitor offline" must surface as a first-class
 * alert, because in elder care a silent monitor is itself an emergency.
 */
object HeartbeatMonitor {
    /** Monitor sends a ~100-byte encrypted heartbeat every 5 minutes. */
    const val HEARTBEAT_INTERVAL_MS: Long = 5L * 60 * 1000

    /** No heartbeat for more than 15 minutes => offline alert. */
    const val OFFLINE_THRESHOLD_MS: Long = 15L * 60 * 1000

    fun livenessAt(
        lastHeartbeatAtMs: Long?,
        nowMs: Long,
    ): MonitorLiveness {
        if (lastHeartbeatAtMs == null) return MonitorLiveness.OFFLINE
        return if (nowMs - lastHeartbeatAtMs > OFFLINE_THRESHOLD_MS) {
            MonitorLiveness.OFFLINE
        } else {
            MonitorLiveness.ONLINE
        }
    }

    /** True exactly when the companion must raise the offline alert. */
    fun isOfflineAlertDue(
        lastHeartbeatAtMs: Long?,
        nowMs: Long,
    ): Boolean = livenessAt(lastHeartbeatAtMs, nowMs) == MonitorLiveness.OFFLINE
}
