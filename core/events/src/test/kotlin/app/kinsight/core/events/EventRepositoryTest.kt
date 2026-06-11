package app.kinsight.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventRepositoryTest {
    private val repository = InMemoryEventRepository()

    private fun event(id: String = "evt-1") =
        CandidateEvent(
            id = id,
            deviceId = "monitor-001",
            type = EventType.FALL,
            classifierScore = 0.9,
            confidence = "HIGH",
            verifierVerdict = null,
            verifierLatencyMs = null,
            occurredAtMs = 1_000,
        )

    private fun alert(id: String = "alert-1") =
        Alert(
            id = id,
            candidateEventId = "evt-1",
            severity = Severity.CRITICAL,
            createdAtMs = 1_100,
        )

    @Test
    fun `stores and retrieves candidate events`() {
        // Arrange / Act
        repository.saveEvent(event())

        // Assert
        assertEquals(EventType.FALL, repository.findEvent("evt-1")?.type)
        assertNull(repository.findEvent("missing"))
    }

    @Test
    fun `update alert returns a new immutable copy`() {
        // Arrange
        repository.saveAlert(alert())

        // Act
        val updated = repository.updateAlert("alert-1") { it.copy(acknowledgedBy = "caregiver-001") }

        // Assert
        assertEquals("caregiver-001", updated?.acknowledgedBy)
        assertNotSame(alert(), updated)
        assertEquals("caregiver-001", repository.findAlert("alert-1")?.acknowledgedBy)
    }

    @Test
    fun `update of a missing alert returns null instead of throwing`() {
        // Arrange / Act / Assert
        assertNull(repository.updateAlert("nope") { it })
    }

    @Test
    fun `candidate event rejects out-of-range classifier score`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            event().copy(classifierScore = 1.5)
        }
    }

    @Test
    fun `entities reject blank identifiers`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) { event().copy(id = " ") }
        assertThrows(IllegalArgumentException::class.java) { alert().copy(candidateEventId = "") }
    }

    @Test
    fun `alerts list preserves insertion order`() {
        // Arrange
        repository.saveAlert(alert("alert-1"))
        repository.saveAlert(alert("alert-2"))

        // Act / Assert
        assertEquals(listOf("alert-1", "alert-2"), repository.alerts().map { it.id })
    }
}
