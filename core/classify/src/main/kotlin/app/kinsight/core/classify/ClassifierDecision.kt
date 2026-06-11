package app.kinsight.core.classify

/**
 * Classifier confidence band. Per Invariant 3, HIGH-confidence candidates
 * alert unconditionally; only LOW/MEDIUM may be offered to a verifier.
 */
enum class Confidence { LOW, MEDIUM, HIGH }

/** Output of the temporal classifier over a pose window. */
sealed interface ClassifierDecision {
    /** No fall signature found in the window. */
    data object NoFall : ClassifierDecision

    /** A fall signature: rapid hip drop + horizontal trunk (+ immobility). */
    data class FallCandidate(
        val confidence: Confidence,
        val score: Double,
        val dropDurationMs: Long,
        val immobilitySeconds: Double,
    ) : ClassifierDecision {
        init {
            require(score in 0.0..1.0) { "score must be in [0,1], got $score" }
            require(dropDurationMs >= 0) { "dropDurationMs must be >= 0" }
            require(immobilitySeconds >= 0.0) { "immobilitySeconds must be >= 0" }
        }
    }
}
