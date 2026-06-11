package app.kinsight.core.events

/** Event categories the monitor can raise (DESIGN.md data model). */
enum class EventType { FALL, IMMOBILITY, ROUTINE_DEVIATION }

/** Alert severity: CRITICAL fires the siren; INFO is digest-only. */
enum class Severity { CRITICAL, WARNING, INFO }

/**
 * A classifier candidate, metadata only — no pixels, ever (Invariant 1).
 * Room maps this at M2; the M1 store is in-memory behind [EventRepository].
 */
data class CandidateEvent(
    val id: String,
    val deviceId: String,
    val type: EventType,
    val classifierScore: Double,
    val confidence: String,
    val verifierVerdict: String?,
    val verifierLatencyMs: Long?,
    val occurredAtMs: Long,
) {
    init {
        require(id.isNotBlank()) { "CandidateEvent.id must not be blank" }
        require(deviceId.isNotBlank()) { "CandidateEvent.deviceId must not be blank" }
        require(classifierScore in 0.0..1.0) { "classifierScore must be in [0,1], got $classifierScore" }
    }
}

/** Audit-trail alert row (trust + liability — DESIGN.md data model). */
data class Alert(
    val id: String,
    val candidateEventId: String,
    val severity: Severity,
    val createdAtMs: Long,
    val sirenFiredAtMs: Long? = null,
    val deliveredAtMs: Long? = null,
    val acknowledgedBy: String? = null,
    val escalatedAtMs: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "Alert.id must not be blank" }
        require(candidateEventId.isNotBlank()) { "Alert.candidateEventId must not be blank" }
    }
}
