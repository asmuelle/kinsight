package app.kinsight.core.alerts

/**
 * Immutable escalation state machine (DESIGN.md flow 1, step 6): the alert is
 * delivered to caregivers in escalation-rank order; if unacknowledged after
 * [ackTimeoutMs], it advances to the next-ranked caregiver. Transitions are
 * pure — callers hold the current state and a clock.
 */
data class Escalation(
    val caregiverIds: List<String>,
    val deliveredAtMs: Long,
    val ackTimeoutMs: Long = ACK_TIMEOUT_MS,
    val activeIndex: Int = 0,
    val acknowledgedBy: String? = null,
) {
    init {
        require(caregiverIds.isNotEmpty()) { "Escalation needs at least one caregiver" }
        require(activeIndex in caregiverIds.indices) { "activeIndex $activeIndex out of bounds" }
    }

    val activeCaregiverId: String get() = caregiverIds[activeIndex]

    val isAcknowledged: Boolean get() = acknowledgedBy != null

    /** True when every ranked caregiver has been tried without acknowledgment. */
    val isExhausted: Boolean get() = !isAcknowledged && activeIndex == caregiverIds.lastIndex

    fun acknowledge(caregiverId: String): Escalation = copy(acknowledgedBy = caregiverId)

    fun isEscalationDue(nowMs: Long): Boolean = !isAcknowledged && !isExhausted && nowMs - deliveredAtMs >= ackTimeoutMs

    /** Advances to the next-ranked caregiver when due; otherwise returns this. */
    fun advance(nowMs: Long): Escalation =
        if (isEscalationDue(nowMs)) {
            copy(activeIndex = activeIndex + 1, deliveredAtMs = nowMs)
        } else {
            this
        }

    companion object {
        /** Unacknowledged after 5 min => next-ranked caregiver. */
        const val ACK_TIMEOUT_MS: Long = 5L * 60 * 1000
    }
}
