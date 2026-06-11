package app.kinsight.companion

import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.core.transport.AlertEvent
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.SealedEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCenterTest {
    private val alertCenter = AlertCenter()
    private val cipher = EnvelopeCipher(HardcodedPairing.current())

    private fun fallEnvelope(occurredAtMs: Long = OCCURRED_AT_MS): SealedEnvelope =
        cipher.seal(
            AlertEvent(
                monitorDeviceId = HardcodedPairing.MONITOR_DEVICE_ID,
                alertId = "alert-1",
                eventType = "FALL",
                severity = "CRITICAL",
                occurredAtMs = occurredAtMs,
            ),
        )

    @Test
    fun `sealed alert envelope renders as a full-screen active alert`() {
        // Arrange
        val envelope = fallEnvelope()

        // Act
        val state = alertCenter.onEnvelope(CompanionUiState.AllQuiet, envelope, nowMs = NOW_MS)

        // Assert
        val active = state as CompanionUiState.AlertActive
        assertEquals("alert-1", active.alert.alertId)
        assertEquals("FALL", active.alert.eventType)
        assertEquals(HardcodedPairing.CAREGIVER_ID, active.escalation.activeCaregiverId)
    }

    @Test
    fun `acknowledge moves the alert to the acknowledged state`() {
        // Arrange
        val active = alertCenter.onEnvelope(CompanionUiState.AllQuiet, fallEnvelope(), nowMs = NOW_MS)

        // Act
        val state = alertCenter.acknowledge(active, HardcodedPairing.CAREGIVER_ID)

        // Assert
        val acknowledged = state as CompanionUiState.AlertAcknowledged
        assertEquals(HardcodedPairing.CAREGIVER_ID, acknowledged.acknowledgedBy)
    }

    @Test
    fun `tampered ciphertext never crashes the alert surface and changes nothing`() {
        // Arrange
        val envelope = fallEnvelope()
        val tampered =
            SealedEnvelope(
                nonce = envelope.nonce,
                ciphertext = envelope.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
            )

        // Act
        val state = alertCenter.onEnvelope(CompanionUiState.AllQuiet, tampered, nowMs = NOW_MS)

        // Assert
        assertTrue(state is CompanionUiState.AllQuiet)
    }

    @Test
    fun `acknowledging the quiet state is a no-op`() {
        // Act
        val state = alertCenter.acknowledge(CompanionUiState.AllQuiet, HardcodedPairing.CAREGIVER_ID)

        // Assert
        assertTrue(state is CompanionUiState.AllQuiet)
    }

    private companion object {
        const val NOW_MS = 1_000_000L
        const val OCCURRED_AT_MS = 999_000L
    }
}
