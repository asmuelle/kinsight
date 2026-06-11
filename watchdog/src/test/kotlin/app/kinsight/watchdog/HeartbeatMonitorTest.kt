package app.kinsight.watchdog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Invariant 8: silence is a signal. */
class HeartbeatMonitorTest {
    private val minute = 60_000L

    @Test
    fun `heartbeat gap over fifteen minutes raises the offline alert`() {
        // Arrange
        val lastHeartbeatAt = 0L

        // Act / Assert — 16-minute gap simulation
        assertEquals(MonitorLiveness.OFFLINE, HeartbeatMonitor.livenessAt(lastHeartbeatAt, nowMs = 16 * minute))
        assertTrue(HeartbeatMonitor.isOfflineAlertDue(lastHeartbeatAt, nowMs = 16 * minute))
    }

    @Test
    fun `recent heartbeat keeps the monitor online`() {
        // Arrange / Act / Assert — 14-minute gap is within tolerance
        assertEquals(MonitorLiveness.ONLINE, HeartbeatMonitor.livenessAt(0L, nowMs = 14 * minute))
        assertFalse(HeartbeatMonitor.isOfflineAlertDue(0L, nowMs = 14 * minute))
    }

    @Test
    fun `exactly fifteen minutes is still online - the alert fires strictly after`() {
        // Arrange / Act / Assert
        assertEquals(MonitorLiveness.ONLINE, HeartbeatMonitor.livenessAt(0L, nowMs = 15 * minute))
        assertEquals(MonitorLiveness.OFFLINE, HeartbeatMonitor.livenessAt(0L, nowMs = 15 * minute + 1))
    }

    @Test
    fun `a monitor that never reported is treated as offline`() {
        // Arrange / Act / Assert — silence from first pairing is also a signal
        assertEquals(MonitorLiveness.OFFLINE, HeartbeatMonitor.livenessAt(null, nowMs = 0))
        assertTrue(HeartbeatMonitor.isOfflineAlertDue(null, nowMs = 0))
    }

    @Test
    fun `offline threshold is three missed heartbeats`() {
        // Arrange / Act / Assert — 5-min interval, 15-min threshold (DESIGN.md)
        assertEquals(3, HeartbeatMonitor.OFFLINE_THRESHOLD_MS / HeartbeatMonitor.HEARTBEAT_INTERVAL_MS)
    }
}
