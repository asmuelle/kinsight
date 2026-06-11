package app.kinsight.core.alerts

import app.kinsight.core.events.Alert
import app.kinsight.core.events.CandidateEvent
import app.kinsight.core.events.EventRepository
import app.kinsight.core.events.Severity
import app.kinsight.core.transport.AlertEvent
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.RelayClient

/** Local siren boundary; the production impl is a max-volume media player. */
fun interface Siren {
    fun fire()
}

/** Connectivity probe; mocked off in airplane-mode tests (Invariant 5). */
fun interface Connectivity {
    fun isOnline(): Boolean
}

/**
 * Invariant 5 — local-first alerting, in this exact order:
 * 1. persist the candidate event (audit trail),
 * 2. fire the LOCAL siren — before any network work, works in airplane mode,
 * 3. seal the text alert and queue it; flush only if online (retry later).
 *
 * Failures on the network leg never undo steps 1-2: the siren has already
 * fired and the envelope stays queued.
 */
class AlertPipeline(
    private val repository: EventRepository,
    private val siren: Siren,
    private val cipher: EnvelopeCipher,
    private val queue: OutboundAlertQueue,
    private val relay: RelayClient,
    private val connectivity: Connectivity,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun raiseAlert(
        event: CandidateEvent,
        severity: Severity,
    ): Alert {
        repository.saveEvent(event)

        siren.fire()
        val sirenFiredAt = clock()

        val alert =
            Alert(
                id = "alert-${event.id}",
                candidateEventId = event.id,
                severity = severity,
                createdAtMs = sirenFiredAt,
                sirenFiredAtMs = sirenFiredAt,
            )
        repository.saveAlert(alert)

        queue.enqueue(
            cipher.seal(
                AlertEvent(
                    monitorDeviceId = event.deviceId,
                    alertId = alert.id,
                    eventType = event.type.name,
                    severity = severity.name,
                    occurredAtMs = event.occurredAtMs,
                ),
            ),
        )
        queue.flush(relay, connectivity)
        return alert
    }
}
