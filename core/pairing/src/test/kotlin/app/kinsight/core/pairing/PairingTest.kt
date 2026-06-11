package app.kinsight.core.pairing

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PairingTest {
    @Test
    fun `hardcoded m1 pairing yields a stable 32-byte secret`() {
        // Arrange / Act
        val first = HardcodedPairing.current()
        val second = HardcodedPairing.current()

        // Assert — deterministic dev placeholder until QR X25519 lands (M2)
        assertEquals(PairingKey.SECRET_LENGTH_BYTES, first.sharedSecret.size)
        assertArrayEquals(first.sharedSecret, second.sharedSecret)
        assertEquals(HardcodedPairing.MONITOR_DEVICE_ID, first.monitorDeviceId)
        assertEquals(HardcodedPairing.CAREGIVER_ID, first.caregiverId)
    }

    @Test
    fun `pairing key rejects secrets that are not 32 bytes`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            PairingKey("m", "c", ByteArray(16))
        }
    }

    @Test
    fun `pairing key rejects blank participant ids`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            PairingKey("", "c", ByteArray(32))
        }
    }
}
