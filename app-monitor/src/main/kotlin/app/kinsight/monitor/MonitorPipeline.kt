package app.kinsight.monitor

import app.kinsight.core.alerts.AlertPipeline
import app.kinsight.core.capture.LumaFrame
import app.kinsight.core.capture.LumaMotionGate
import app.kinsight.core.classify.ClassifierDecision.FallCandidate
import app.kinsight.core.classify.HeuristicFallClassifier
import app.kinsight.core.events.Alert
import app.kinsight.core.events.CandidateEvent
import app.kinsight.core.events.EventType
import app.kinsight.core.events.Severity
import app.kinsight.core.pose.PoseLandmarker
import app.kinsight.core.pose.PoseWindow
import app.kinsight.core.verify.VerificationCoordinator
import app.kinsight.core.verify.VerificationOutcome

/** What one camera frame did to the monitor state (for UI + tests). */
sealed interface FrameOutcome {
    /** Motion gate closed: the pose pipeline never ran (cost tier 1). */
    data object GateClosed : FrameOutcome

    /** Gate open, no fall signature in the current pose window. */
    data object Watching : FrameOutcome

    /** A candidate fired within the cooldown of a raised alert. */
    data object CoolingDown : FrameOutcome

    /** The full path ran: siren fired, alert persisted, envelope queued. */
    data class Alerted(
        val alert: Alert,
    ) : FrameOutcome
}

/**
 * The donor-phone frame loop (DESIGN.md flow 1), pure Kotlin and fully
 * JVM-testable: luma motion gate -> pose landmarker -> in-memory pose window
 * -> heuristic temporal classifier -> fail-open verification -> alert
 * pipeline. Every stage is a core-module type; this class only sequences
 * them.
 */
class MonitorPipeline(
    private val motionGate: LumaMotionGate,
    private val poseLandmarker: PoseLandmarker,
    private val poseWindow: PoseWindow,
    private val classifier: HeuristicFallClassifier,
    private val verification: VerificationCoordinator,
    private val alertPipeline: AlertPipeline,
    private val monitorDeviceId: String,
    private val alertCooldownMs: Long = ALERT_COOLDOWN_MS,
) {
    private var lastAlertAtMs: Long? = null

    fun onFrame(frame: LumaFrame): FrameOutcome {
        if (!motionGate.process(frame)) return FrameOutcome.GateClosed

        poseLandmarker.detect(frame)?.let(poseWindow::append)
        val candidate = classifier.classify(poseWindow.snapshot()) as? FallCandidate ?: return FrameOutcome.Watching
        if (isCoolingDown(frame.timestampMs)) return FrameOutcome.CoolingDown

        val outcome = verification.decide(candidate)
        if (!outcome.shouldAlert) return FrameOutcome.Watching
        return FrameOutcome.Alerted(raiseAlert(candidate, outcome, frame.timestampMs))
    }

    private fun isCoolingDown(nowMs: Long): Boolean {
        val last = lastAlertAtMs ?: return false
        return nowMs - last < alertCooldownMs
    }

    private fun raiseAlert(
        candidate: FallCandidate,
        outcome: VerificationOutcome,
        occurredAtMs: Long,
    ): Alert {
        lastAlertAtMs = occurredAtMs
        val event =
            CandidateEvent(
                id = "evt-$monitorDeviceId-$occurredAtMs",
                deviceId = monitorDeviceId,
                type = EventType.FALL,
                classifierScore = candidate.score,
                confidence = candidate.confidence.name,
                verifierVerdict = outcome.verdict?.name,
                verifierLatencyMs = outcome.verifierLatencyMs,
                occurredAtMs = occurredAtMs,
            )
        return alertPipeline.raiseAlert(event, Severity.CRITICAL)
    }

    companion object {
        /**
         * One alert per minute per device: re-firing adds nothing for a
         * caregiver already alerted; escalation handles non-response.
         */
        const val ALERT_COOLDOWN_MS: Long = 60_000L
    }
}
