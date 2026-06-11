package app.kinsight.core.alerts

import app.kinsight.core.events.CandidateEvent
import app.kinsight.core.events.EventType
import app.kinsight.core.events.InMemoryEventRepository
import app.kinsight.core.events.Severity
import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.Heartbeat
import app.kinsight.core.transport.RelayClient
import app.kinsight.core.transport.SendResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Invariant 5: local-first alerting — siren before network, queue offline. */
class AlertPipelineTest {
    private val repository = InMemoryEventRepository()
    private val cipher = EnvelopeCipher(HardcodedPairing.current())
    private val queue = OutboundAlertQueue()
    private val callOrder = mutableListOf<String>()

    private val recordingSiren = Siren { callOrder.add("siren") }
    private val recordingRelay =
        RelayClient { _ ->
            callOrder.add("relay")
            SendResult.Delivered
        }

    private fun fallEvent() =
        CandidateEvent(
            id = "evt-1",
            deviceId = "monitor-001",
            type = EventType.FALL,
            classifierScore = 1.0,
            confidence = "HIGH",
            verifierVerdict = null,
            verifierLatencyMs = null,
            occurredAtMs = 1_000,
        )

    private fun pipeline(connectivity: Connectivity) =
        AlertPipeline(
            repository = repository,
            siren = recordingSiren,
            cipher = cipher,
            queue = queue,
            relay = recordingRelay,
            connectivity = connectivity,
            clock = { 2_000 },
        )

    @Test
    fun `siren fires strictly before any network send`() {
        // Arrange / Act
        pipeline { true }.raiseAlert(fallEvent(), Severity.CRITICAL)

        // Assert
        assertEquals(listOf("siren", "relay"), callOrder)
    }

    @Test
    fun `airplane mode still fires the siren and queues the alert`() {
        // Arrange / Act — connectivity mocked off (Invariant 5 test case)
        val alert = pipeline { false }.raiseAlert(fallEvent(), Severity.CRITICAL)

        // Assert — siren fired, nothing sent, envelope retained for retry
        assertEquals(listOf("siren"), callOrder)
        assertEquals(1, queue.pendingCount)
        assertNotNull(alert.sirenFiredAtMs)
    }

    @Test
    fun `queued alert delivers once connectivity returns`() {
        // Arrange
        pipeline { false }.raiseAlert(fallEvent(), Severity.CRITICAL)
        assertEquals(1, queue.pendingCount)

        // Act
        val delivered = queue.flush(recordingRelay) { true }

        // Assert
        assertEquals(1, delivered)
        assertEquals(0, queue.pendingCount)
    }

    @Test
    fun `failed sends stay queued for retry`() {
        // Arrange
        val failingRelay = RelayClient { _ -> SendResult.Failed("relay 5xx") }
        queue.enqueue(cipher.seal(heartbeatPayload()))

        // Act
        val delivered = queue.flush(failingRelay) { true }

        // Assert — nothing dropped until the relay confirms
        assertEquals(0, delivered)
        assertEquals(1, queue.pendingCount)
    }

    @Test
    fun `alert audit trail persists event and alert rows`() {
        // Arrange / Act
        val alert = pipeline { true }.raiseAlert(fallEvent(), Severity.CRITICAL)

        // Assert
        assertNotNull(repository.findEvent("evt-1"))
        assertEquals(alert, repository.findAlert(alert.id))
        assertTrue(alert.sirenFiredAtMs!! <= alert.createdAtMs)
    }

    private fun heartbeatPayload() =
        Heartbeat(
            monitorDeviceId = "monitor-001",
            sentAtMs = 1_000,
            thermalState = "NOMINAL",
            batteryPercent = 90,
        )
}
