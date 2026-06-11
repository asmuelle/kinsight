package app.kinsight.core.pose

/** Body landmarks the M1 heuristic classifier consumes. */
enum class Landmark {
    NOSE,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
}

/**
 * One detected keypoint in normalized image coordinates: x,y in [0,1] with
 * y increasing downward (image convention), plus detector confidence.
 */
data class Keypoint(
    val x: Double,
    val y: Double,
    val confidence: Double,
)

/**
 * One pose observation. Metadata only — keypoints are derived geometry, never
 * pixels (Invariants 1-2: nothing image-like exists past the landmarker).
 */
data class PoseFrame(
    val timestampMs: Long,
    val keypoints: Map<Landmark, Keypoint>,
) {
    /** Mean y of the two hips, or null when either hip is missing. */
    fun hipCenterY(): Double? = centerY(Landmark.LEFT_HIP, Landmark.RIGHT_HIP)

    /** Mean y of the two shoulders, or null when either is missing. */
    fun shoulderCenterY(): Double? = centerY(Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER)

    /**
     * Vertical extent of the trunk (|hipY - shoulderY|): large while upright,
     * near zero when the trunk is horizontal (lying on the floor).
     */
    fun trunkVerticality(): Double? {
        val hips = hipCenterY() ?: return null
        val shoulders = shoulderCenterY() ?: return null
        return if (hips >= shoulders) hips - shoulders else shoulders - hips
    }

    /** True when shoulders and hips are all present above [minConfidence]. */
    fun hasConfidentPerson(minConfidence: Double): Boolean =
        TRUNK_LANDMARKS.all { landmark ->
            (keypoints[landmark]?.confidence ?: 0.0) >= minConfidence
        }

    private fun centerY(
        a: Landmark,
        b: Landmark,
    ): Double? {
        val first = keypoints[a] ?: return null
        val second = keypoints[b] ?: return null
        return (first.y + second.y) / 2.0
    }

    companion object {
        private val TRUNK_LANDMARKS =
            listOf(Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER, Landmark.LEFT_HIP, Landmark.RIGHT_HIP)
    }
}
