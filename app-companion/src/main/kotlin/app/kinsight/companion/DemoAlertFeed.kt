package app.kinsight.companion

import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.core.transport.AlertEvent
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.SealedEnvelope

/**
 * M1 "alert drill": seals a staged-fall [AlertEvent] with the real pairing
 * cipher so the companion exercises the exact decrypt-and-render path that
 * relay-delivered envelopes will use from M2 on. Drills double as a product
 * feature — caregivers should rehearse the alert flow before they need it.
 */
internal object DemoAlertFeed {
    fun stagedFallEnvelope(nowMs: Long): SealedEnvelope =
        EnvelopeCipher(HardcodedPairing.current()).seal(
            AlertEvent(
                monitorDeviceId = HardcodedPairing.MONITOR_DEVICE_ID,
                alertId = "drill-$nowMs",
                eventType = "FALL",
                severity = "CRITICAL",
                occurredAtMs = nowMs,
            ),
        )
}
