package app.kinsight.core.classify

import app.kinsight.core.pose.Keypoint
import app.kinsight.core.pose.Landmark
import app.kinsight.core.pose.PoseFrame

/**
 * Deterministic golden keypoint sequences (AGENTS.md testing policy #2):
 * recorded-fall analogues replayed through the classifier. This suite grows
 * with every false positive/negative found in pilots.
 */
object GoldenSequences {
    private const val STANDING_HIP_Y = 0.55
    private const val STANDING_SHOULDER_Y = 0.30
    private const val LYING_HIP_Y = 0.85
    private const val LYING_SHOULDER_Y = 0.82

    fun trunkFrame(
        timestampMs: Long,
        hipY: Double,
        shoulderY: Double,
        confidence: Double = 0.9,
    ): PoseFrame =
        PoseFrame(
            timestampMs = timestampMs,
            keypoints =
                mapOf(
                    Landmark.LEFT_SHOULDER to Keypoint(0.45, shoulderY, confidence),
                    Landmark.RIGHT_SHOULDER to Keypoint(0.55, shoulderY, confidence),
                    Landmark.LEFT_HIP to Keypoint(0.45, hipY, confidence),
                    Landmark.RIGHT_HIP to Keypoint(0.55, hipY, confidence),
                ),
        )

    private fun standing(
        fromMs: Long,
        untilMs: Long,
        stepMs: Long = 200,
    ): List<PoseFrame> = (fromMs..untilMs step stepMs).map { trunkFrame(it, STANDING_HIP_Y, STANDING_SHOULDER_Y) }

    private fun lyingStill(
        fromMs: Long,
        untilMs: Long,
        stepMs: Long = 2_000,
    ): List<PoseFrame> = (fromMs..untilMs step stepMs).map { trunkFrame(it, LYING_HIP_Y, LYING_SHOULDER_Y) }

    /** Floor-level writhing: still horizontal, but clearly moving. */
    private fun writhingOnFloor(
        fromMs: Long,
        untilMs: Long,
        stepMs: Long = 1_000,
    ): List<PoseFrame> =
        (fromMs..untilMs step stepMs).mapIndexed { index, t ->
            val wobble = if (index % 2 == 0) 0.04 else -0.04
            trunkFrame(t, LYING_HIP_Y + wobble, LYING_SHOULDER_Y + wobble)
        }

    /** Staged fall: 1s standing, 400ms collapse, then still on the floor. */
    fun fallThenStill(immobileMs: Long): List<PoseFrame> =
        standing(fromMs = 0, untilMs = 1_000) +
            lyingStill(fromMs = 1_200, untilMs = 1_200 + immobileMs)

    /** Fall, brief stillness, then floor-level movement (caller picks split). */
    fun fallStillThenWrithing(
        immobileMs: Long,
        writhingMs: Long,
    ): List<PoseFrame> =
        fallThenStill(immobileMs) +
            writhingOnFloor(fromMs = 1_200 + immobileMs + 1_000, untilMs = 1_200 + immobileMs + writhingMs)

    /** Slow controlled lie-down on the couch over ~3s — not a fall. */
    fun couchLieDown(): List<PoseFrame> {
        val descentSteps = 6
        val descent =
            (0..descentSteps).map { step ->
                val progress = step.toDouble() / descentSteps
                trunkFrame(
                    timestampMs = 1_000 + step * 500L,
                    hipY = STANDING_HIP_Y + (LYING_HIP_Y - STANDING_HIP_Y) * progress,
                    shoulderY = STANDING_SHOULDER_Y + (LYING_SHOULDER_Y - STANDING_SHOULDER_Y) * progress,
                )
            }
        return standing(fromMs = 0, untilMs = 800) + descent +
            lyingStill(fromMs = 4_500, untilMs = 40_000)
    }

    /** Kneeling: modest hip drop, trunk stays upright. */
    fun kneelDown(): List<PoseFrame> =
        standing(fromMs = 0, untilMs = 1_000) +
            (1_200L..40_000L step 2_000).map { trunkFrame(it, hipY = 0.72, shoulderY = 0.45) }

    /** Sitting down fast: rapid hip drop but trunk remains vertical. */
    fun rapidSit(): List<PoseFrame> =
        standing(fromMs = 0, untilMs = 1_000) +
            (1_200L..40_000L step 2_000).map { trunkFrame(it, hipY = 0.85, shoulderY = 0.60) }

    /** A pet wandering: detector emits only low-confidence keypoints. */
    fun petMotion(): List<PoseFrame> =
        (0L..10_000L step 500).map {
            trunkFrame(it, hipY = 0.8, shoulderY = 0.78, confidence = 0.2)
        }
}
