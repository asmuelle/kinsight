package app.kinsight.core.transport

import app.kinsight.core.pairing.HardcodedPairing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Invariant 7: E2E-encrypted metadata — the relay sees ciphertext only. */
class EnvelopeCipherTest {
    private val cipher = EnvelopeCipher(HardcodedPairing.current())

    private val alert =
        AlertEvent(
            monitorDeviceId = "monitor-001",
            alertId = "alert-evt-1",
            eventType = "FALL",
            severity = "CRITICAL",
            occurredAtMs = 1_718_000_000_000,
        )

    @Test
    fun `seal and open round-trips every payload type`() {
        // Arrange
        val payloads =
            listOf(
                alert,
                ActivitySummary("monitor-001", "2026-06-10", 42, listOf("kitchen", "living")),
                Heartbeat("monitor-001", 1_718_000_000_000, "NOMINAL", 80),
            )

        // Act / Assert
        payloads.forEach { payload ->
            assertEquals(payload, cipher.open(cipher.seal(payload)))
        }
    }

    @Test
    fun `wire bytes contain no plaintext fragments`() {
        // Arrange / Act
        val envelope = cipher.seal(alert)
        val wire = String(envelope.nonce + envelope.ciphertext, Charsets.ISO_8859_1)

        // Assert — nothing legible leaves the house
        listOf("monitor-001", "FALL", "CRITICAL", "alertId").forEach { fragment ->
            assertFalse(wire.contains(fragment)) { "plaintext '$fragment' visible on the wire" }
        }
    }

    @Test
    fun `tampered ciphertext fails authentication instead of decoding garbage`() {
        // Arrange
        val envelope = cipher.seal(alert)
        val tampered = envelope.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        // Act / Assert
        assertThrows(Exception::class.java) {
            cipher.open(SealedEnvelope(envelope.nonce, tampered))
        }
    }

    @Test
    fun `alert envelopes fit comfortably inside the 4KB budget`() {
        // Arrange / Act
        val envelope = cipher.seal(alert)

        // Assert — FCM's 4KB ceiling is also the Invariant 1 budget
        assertTrue(envelope.wireSizeBytes <= EnvelopeCipher.MAX_WIRE_BYTES)
    }

    @Test
    fun `oversized payloads are rejected at the seal boundary`() {
        // Arrange — a pathological summary nothing legitimate would produce
        val bloated =
            ActivitySummary(
                monitorDeviceId = "monitor-001",
                dateIso = "2026-06-10",
                motionMinutes = 42,
                zonesActive = List(800) { "zone-$it-padding-padding-padding" },
            )

        // Act / Assert
        assertThrows(OversizedPayloadException::class.java) { cipher.seal(bloated) }
    }

    @Test
    fun `each seal uses a fresh nonce`() {
        // Arrange / Act
        val first = cipher.seal(alert)
        val second = cipher.seal(alert)

        // Assert
        assertFalse(first.nonce.contentEquals(second.nonce))
    }
}
