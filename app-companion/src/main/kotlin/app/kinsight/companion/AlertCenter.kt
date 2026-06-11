package app.kinsight.companion

import app.kinsight.core.alerts.Escalation
import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.core.pairing.PairingSource
import app.kinsight.core.transport.AlertEvent
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.SealedEnvelope

/** One rendered alert on the caregiver screen — text fields only. */
data class IncomingAlert(
    val alertId: String,
    val monitorDeviceId: String,
    val eventType: String,
    val severity: String,
    val occurredAtMs: Long,
)

/** Full-screen companion states (DESIGN.md flow 1 step 6). */
sealed interface CompanionUiState {
    data object AllQuiet : CompanionUiState

    data class AlertActive(
        val alert: IncomingAlert,
        val escalation: Escalation,
    ) : CompanionUiState

    data class AlertAcknowledged(
        val alert: IncomingAlert,
        val acknowledgedBy: String,
    ) : CompanionUiState
}

/**
 * Pure state machine behind the companion screen: opens sealed envelopes
 * with the pairing key (Invariant 7 — the app only ever receives
 * ciphertext) and walks the acknowledge/escalate flow. No Android types, so
 * the whole alert-rendering path is JVM-testable.
 */
class AlertCenter(
    pairingSource: PairingSource = HardcodedPairing,
    private val caregiverIds: List<String> = listOf(HardcodedPairing.CAREGIVER_ID),
) {
    private val cipher = EnvelopeCipher(pairingSource.current())

    /**
     * Opens an envelope; tampered ciphertext or non-alert payloads leave the
     * state unchanged (errors are contained, never crash the alert surface).
     */
    fun onEnvelope(
        state: CompanionUiState,
        envelope: SealedEnvelope,
        nowMs: Long,
    ): CompanionUiState {
        val payload = runCatching { cipher.open(envelope) }.getOrNull() ?: return state
        val alertEvent = payload as? AlertEvent ?: return state
        return CompanionUiState.AlertActive(
            alert =
                IncomingAlert(
                    alertId = alertEvent.alertId,
                    monitorDeviceId = alertEvent.monitorDeviceId,
                    eventType = alertEvent.eventType,
                    severity = alertEvent.severity,
                    occurredAtMs = alertEvent.occurredAtMs,
                ),
            escalation = Escalation(caregiverIds = caregiverIds, deliveredAtMs = nowMs),
        )
    }

    fun acknowledge(
        state: CompanionUiState,
        caregiverId: String,
    ): CompanionUiState =
        when (state) {
            is CompanionUiState.AlertActive -> {
                CompanionUiState.AlertAcknowledged(alert = state.alert, acknowledgedBy = caregiverId)
            }

            is CompanionUiState.AllQuiet -> {
                state
            }

            is CompanionUiState.AlertAcknowledged -> {
                state
            }
        }
}
