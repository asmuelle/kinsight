package app.kinsight.core.alerts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EscalationTest {
    private val chain =
        Escalation(
            caregiverIds = listOf("caregiver-001", "caregiver-002", "caregiver-003"),
            deliveredAtMs = 0,
        )

    @Test
    fun `unacknowledged alert escalates to the next caregiver after five minutes`() {
        // Arrange
        val fiveMinutes = Escalation.ACK_TIMEOUT_MS

        // Act
        val escalated = chain.advance(nowMs = fiveMinutes)

        // Assert
        assertEquals("caregiver-002", escalated.activeCaregiverId)
        assertEquals(fiveMinutes, escalated.deliveredAtMs)
    }

    @Test
    fun `no escalation before the ack timeout elapses`() {
        // Arrange / Act
        val unchanged = chain.advance(nowMs = Escalation.ACK_TIMEOUT_MS - 1)

        // Assert
        assertSame(chain, unchanged)
        assertEquals("caregiver-001", unchanged.activeCaregiverId)
    }

    @Test
    fun `acknowledged alert never escalates`() {
        // Arrange
        val acknowledged = chain.acknowledge("caregiver-001")

        // Act
        val later = acknowledged.advance(nowMs = Escalation.ACK_TIMEOUT_MS * 10)

        // Assert
        assertTrue(later.isAcknowledged)
        assertEquals("caregiver-001", later.activeCaregiverId)
        assertFalse(later.isEscalationDue(nowMs = Escalation.ACK_TIMEOUT_MS * 10))
    }

    @Test
    fun `escalation walks the full chain then reports exhausted`() {
        // Arrange / Act
        val second = chain.advance(nowMs = Escalation.ACK_TIMEOUT_MS)
        val third = second.advance(nowMs = Escalation.ACK_TIMEOUT_MS * 2)
        val beyond = third.advance(nowMs = Escalation.ACK_TIMEOUT_MS * 3)

        // Assert
        assertEquals("caregiver-003", third.activeCaregiverId)
        assertTrue(third.isExhausted)
        assertSame(third, beyond)
    }

    @Test
    fun `escalation requires at least one caregiver`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            Escalation(caregiverIds = emptyList(), deliveredAtMs = 0)
        }
    }

    @Test
    fun `transitions return new immutable copies`() {
        // Arrange / Act
        val acknowledged = chain.acknowledge("caregiver-002")

        // Assert — original state is untouched
        assertFalse(chain.isAcknowledged)
        assertTrue(acknowledged.isAcknowledged)
    }
}
