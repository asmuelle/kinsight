package app.kinsight.core.classify

/**
 * Every threshold of the M1 heuristic classifier, named in one place
 * (AGENTS.md: no magic numbers). Tuned against the golden-sequence suite;
 * replaced by a trained LiteRT temporal classifier at M2.
 */
object FallHeuristics {
    /** Minimum normalized hip-height increase that counts as a drop. */
    const val MIN_HIP_DROP_FRACTION: Double = 0.25

    /** A drop must complete within this long to be "rapid" (a real fall). */
    const val MAX_DROP_DURATION_MS: Long = 800L

    /** Trunk vertical extent at or below this means "lying horizontal". */
    const val MAX_LYING_TRUNK_VERTICALITY: Double = 0.10

    /** Immobility needed for a HIGH-confidence fall (DESIGN.md flow 1). */
    const val HIGH_IMMOBILITY_SECONDS: Double = 30.0

    /** Immobility needed for MEDIUM confidence; below this is LOW. */
    const val MEDIUM_IMMOBILITY_SECONDS: Double = 10.0

    /** Keypoints below this confidence are treated as absent (pet motion). */
    const val MIN_KEYPOINT_CONFIDENCE: Double = 0.5

    /** Max per-frame hip/shoulder displacement still counted as immobile. */
    const val MOVEMENT_EPSILON: Double = 0.02

    /** Minimum confident frames before the window is scoreable at all. */
    const val MIN_SCOREABLE_FRAMES: Int = 4

    const val SCORE_BASE: Double = 0.5
    const val SCORE_IMMOBILITY_WEIGHT: Double = 0.5
}
