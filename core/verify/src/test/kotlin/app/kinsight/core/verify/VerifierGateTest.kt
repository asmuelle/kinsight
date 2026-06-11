package app.kinsight.core.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** Invariant 4: verifier RAM gate, decided once at service start. */
class VerifierGateTest {
    private val gb = 1024L * 1024 * 1024

    @Test
    fun `sub-4GB device never loads the verifier`() {
        // Arrange
        val loads = AtomicInteger(0)
        val capabilities = DeviceCapabilities(totalRamBytes = 3 * gb)

        // Act
        val gate =
            VerifierGate(capabilities) {
                loads.incrementAndGet()
                FallVerifier { _ -> VerifierVerdict.FALL_CONFIRMED }
            }

        // Assert — deterministic path only; the loader must never run
        assertFalse(capabilities.isVerifierEligible)
        assertNull(gate.verifier)
        assertEquals(0, loads.get())
    }

    @Test
    fun `4GB device loads the verifier exactly once at service start`() {
        // Arrange
        val loads = AtomicInteger(0)
        val capabilities = DeviceCapabilities(totalRamBytes = 4 * gb)

        // Act
        val gate =
            VerifierGate(capabilities) {
                loads.incrementAndGet()
                FallVerifier { _ -> VerifierVerdict.FALL_CONFIRMED }
            }
        // Reference the verifier repeatedly — no lazy mid-session load may occur.
        repeat(3) { assertNotNull(gate.verifier) }

        // Assert
        assertTrue(capabilities.isVerifierEligible)
        assertEquals(1, loads.get(), "verifier must load once, eagerly, at service start")
    }
}
