package app.kinsight.core.pose

import app.kinsight.core.capture.LumaFrame

/**
 * Boundary interface for pose detection. The production implementation wraps
 * MediaPipe Pose Landmarker (LiteRT) inside the monitor app; tests and the
 * JVM slice use fixture-driven fakes — the pipeline is verifiable without any
 * ML runtime (deterministic mocks, no model download).
 */
fun interface PoseLandmarker {
    /** Detects a pose in [frame], or null when no person is visible. */
    fun detect(frame: LumaFrame): PoseFrame?
}
