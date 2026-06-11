package app.kinsight.core.classify

import app.kinsight.core.classify.ClassifierDecision.FallCandidate
import app.kinsight.core.classify.ClassifierDecision.NoFall
import app.kinsight.core.pose.PoseFrame
import kotlin.math.abs
import kotlin.math.min

/**
 * M1 deterministic temporal fall classifier (DESIGN.md flow 1, step 3):
 * rapid hip-height drop + horizontal trunk + immobility.
 *
 * This is THE decision-maker on the alert path (Invariant 3). It never
 * consults a model; a verifier may only suppress its LOW/MEDIUM candidates.
 */
class HeuristicFallClassifier {
    fun classify(window: List<PoseFrame>): ClassifierDecision {
        val frames = window.filter { it.hasConfidentPerson(FallHeuristics.MIN_KEYPOINT_CONFIDENCE) }
        if (frames.size < FallHeuristics.MIN_SCOREABLE_FRAMES) return NoFall

        val drop = findRapidHipDrop(frames) ?: return NoFall
        val postDrop = frames.subList(drop.endIndex, frames.size)
        if (!isLyingHorizontal(postDrop)) return NoFall

        val immobilitySeconds = immobileSecondsFrom(postDrop)
        return FallCandidate(
            confidence = confidenceFor(immobilitySeconds),
            score = scoreFor(immobilitySeconds),
            dropDurationMs = drop.durationMs,
            immobilitySeconds = immobilitySeconds,
        )
    }

    private data class HipDrop(
        val endIndex: Int,
        val durationMs: Long,
    )

    /** Finds the first hip drop of >= MIN_HIP_DROP_FRACTION completing within MAX_DROP_DURATION_MS. */
    private fun findRapidHipDrop(frames: List<PoseFrame>): HipDrop? =
        frames.indices.firstNotNullOfOrNull { start -> dropStartingAt(frames, start) }

    private fun dropStartingAt(
        frames: List<PoseFrame>,
        start: Int,
    ): HipDrop? {
        val startY = frames[start].hipCenterY() ?: return null
        for (end in start + 1 until frames.size) {
            val elapsed = frames[end].timestampMs - frames[start].timestampMs
            if (elapsed > FallHeuristics.MAX_DROP_DURATION_MS) return null
            val endY = frames[end].hipCenterY() ?: continue
            if (endY - startY >= FallHeuristics.MIN_HIP_DROP_FRACTION) {
                return HipDrop(endIndex = end, durationMs = elapsed)
            }
        }
        return null
    }

    private fun isLyingHorizontal(postDrop: List<PoseFrame>): Boolean {
        val verticalities = postDrop.mapNotNull { it.trunkVerticality() }
        if (verticalities.isEmpty()) return false
        val mean = verticalities.sum() / verticalities.size
        return mean <= FallHeuristics.MAX_LYING_TRUNK_VERTICALITY
    }

    /** Continuous immobile time from the drop end across [postDrop]. */
    private fun immobileSecondsFrom(postDrop: List<PoseFrame>): Double {
        if (postDrop.size < 2) return 0.0
        var immobileUntil = postDrop.first().timestampMs
        for (i in 1 until postDrop.size) {
            if (displacement(postDrop[i - 1], postDrop[i]) > FallHeuristics.MOVEMENT_EPSILON) break
            immobileUntil = postDrop[i].timestampMs
        }
        return (immobileUntil - postDrop.first().timestampMs) / MILLIS_PER_SECOND
    }

    private fun displacement(
        before: PoseFrame,
        after: PoseFrame,
    ): Double {
        val hipDelta = deltaOrNull(before.hipCenterY(), after.hipCenterY())
        val shoulderDelta = deltaOrNull(before.shoulderCenterY(), after.shoulderCenterY())
        return maxOf(hipDelta ?: Double.MAX_VALUE, shoulderDelta ?: Double.MAX_VALUE)
    }

    private fun deltaOrNull(
        before: Double?,
        after: Double?,
    ): Double? {
        if (before == null || after == null) return null
        return abs(after - before)
    }

    private fun confidenceFor(immobilitySeconds: Double): Confidence =
        when {
            immobilitySeconds >= FallHeuristics.HIGH_IMMOBILITY_SECONDS -> Confidence.HIGH
            immobilitySeconds >= FallHeuristics.MEDIUM_IMMOBILITY_SECONDS -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

    private fun scoreFor(immobilitySeconds: Double): Double {
        val immobilityFraction = min(immobilitySeconds / FallHeuristics.HIGH_IMMOBILITY_SECONDS, 1.0)
        return FallHeuristics.SCORE_BASE + FallHeuristics.SCORE_IMMOBILITY_WEIGHT * immobilityFraction
    }

    private companion object {
        const val MILLIS_PER_SECOND: Double = 1000.0
    }
}
