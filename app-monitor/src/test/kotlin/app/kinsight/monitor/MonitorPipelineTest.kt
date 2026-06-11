package app.kinsight.monitor

import app.kinsight.core.alerts.AlertPipeline
import app.kinsight.core.alerts.Connectivity
import app.kinsight.core.alerts.OutboundAlertQueue
import app.kinsight.core.alerts.Siren
import app.kinsight.core.capture.LumaFrame
import app.kinsight.core.capture.LumaMotionGate
import app.kinsight.core.classify.HeuristicFallClassifier
import app.kinsight.core.events.InMemoryEventRepository
import app.kinsight.core.events.Severity
import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.core.pose.Keypoint
import app.kinsight.core.pose.Landmark
import app.kinsight.core.pose.PoseFrame
import app.kinsight.core.pose.PoseLandmarker
import app.kinsight.core.pose.PoseWindow
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.RelayClient
import app.kinsight.core.transport.SendResult
import app.kinsight.core.verify.VerificationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM test of the donor-phone frame loop: the same staged-fall shape as the
 * core golden sequences, driven through motion gate + window + classifier +
 * verification + alert pipeline exactly as MonitorService wires them.
 */
class MonitorPipelineTest {
    private val repository = InMemoryEventRepository()
    private val queue = OutboundAlertQueue()
    private var sirenCount = 0

    private class ScriptedLandmarker : PoseLandmarker {
        val posesByTimestamp = mutableMapOf<Long, PoseFrame>()
        var detectCalls = 0

        override fun detect(frame: LumaFrame): PoseFrame? {
            detectCalls++
            return posesByTimestamp[frame.timestampMs]
        }
    }

    private val landmarker = ScriptedLandmarker()

    private fun pipeline(): MonitorPipeline =
        MonitorPipeline(
            motionGate = LumaMotionGate(),
            poseLandmarker = landmarker,
            poseWindow = PoseWindow(),
            classifier = HeuristicFallClassifier(),
            verification = VerificationCoordinator(verifier = null),
            alertPipeline =
                AlertPipeline(
                    repository = repository,
                    siren = Siren { sirenCount++ },
                    cipher = EnvelopeCipher(HardcodedPairing.current()),
                    queue = queue,
                    relay = RelayClient { SendResult.Failed("offline at M1") },
                    connectivity = Connectivity { false },
                    clock = { 0L },
                ),
            monitorDeviceId = HardcodedPairing.MONITOR_DEVICE_ID,
        )

    /** Alternating luma fill keeps the motion gate open on every frame. */
    private fun movingFrame(
        timestampMs: Long,
        index: Int,
    ): LumaFrame {
        val fill = if (index % 2 == 0) bright else dark
        return LumaFrame(WIDTH, HEIGHT, ByteArray(WIDTH * HEIGHT) { fill }, timestampMs)
    }

    private fun stillFrame(timestampMs: Long): LumaFrame =
        LumaFrame(WIDTH, HEIGHT, ByteArray(WIDTH * HEIGHT), timestampMs)

    private fun trunkPose(
        timestampMs: Long,
        hipY: Double,
        shoulderY: Double,
    ): PoseFrame =
        PoseFrame(
            timestampMs = timestampMs,
            keypoints =
                mapOf(
                    Landmark.LEFT_SHOULDER to Keypoint(0.45, shoulderY, CONFIDENT),
                    Landmark.RIGHT_SHOULDER to Keypoint(0.55, shoulderY, CONFIDENT),
                    Landmark.LEFT_HIP to Keypoint(0.45, hipY, CONFIDENT),
                    Landmark.RIGHT_HIP to Keypoint(0.55, hipY, CONFIDENT),
                ),
        )

    /** Standing ~1s, 400ms collapse, then >30s of floor stillness. */
    private fun stagedFallTimestamps(): List<Long> {
        val standing = (0L..800L step 200L).toList()
        val lying = (1_200L..36_000L step 2_000L).toList()
        return standing + lying
    }

    private fun scriptStagedFall() {
        val standingUntil = 800L
        stagedFallTimestamps().forEach { t ->
            landmarker.posesByTimestamp[t] =
                if (t <= standingUntil) {
                    trunkPose(t, hipY = STANDING_HIP_Y, shoulderY = STANDING_SHOULDER_Y)
                } else {
                    trunkPose(t, hipY = LYING_HIP_Y, shoulderY = LYING_SHOULDER_Y)
                }
        }
    }

    @Test
    fun `staged fall raises exactly one alert with siren before queued envelope`() {
        // Arrange
        scriptStagedFall()
        val pipeline = pipeline()

        // Act
        val outcomes = stagedFallTimestamps().mapIndexed { index, t -> pipeline.onFrame(movingFrame(t, index)) }

        // Assert
        val alerted = outcomes.filterIsInstance<FrameOutcome.Alerted>()
        assertEquals(1, alerted.size)
        assertEquals(Severity.CRITICAL, alerted.single().alert.severity)
        assertEquals(1, sirenCount)
        assertEquals(1, queue.pendingCount) // offline: envelope queued, not dropped
        assertTrue(outcomes.last() is FrameOutcome.CoolingDown)
    }

    @Test
    fun `closed motion gate never invokes the pose landmarker`() {
        // Arrange
        val pipeline = pipeline()

        // Act — identical frames: zero luma change, gate stays closed
        val outcomes = (0L..2_000L step 200L).map { t -> pipeline.onFrame(stillFrame(t)) }

        // Assert
        assertTrue(outcomes.all { it is FrameOutcome.GateClosed })
        assertEquals(0, landmarker.detectCalls)
    }

    @Test
    fun `motion without a person stays in watching state`() {
        // Arrange — gate opens but the landmarker finds no pose
        val pipeline = pipeline()

        // Act
        val outcomes = (0 until 10).map { i -> pipeline.onFrame(movingFrame(i * 200L, i)) }

        // Assert
        assertTrue(outcomes.drop(1).all { it is FrameOutcome.Watching })
        assertEquals(0, sirenCount)
        assertEquals(0, queue.pendingCount)
    }

    private companion object {
        const val WIDTH = 8
        const val HEIGHT = 8
        val bright = 120.toByte()
        val dark = 0.toByte()
        const val CONFIDENT = 0.9
        const val STANDING_HIP_Y = 0.55
        const val STANDING_SHOULDER_Y = 0.30
        const val LYING_HIP_Y = 0.85
        const val LYING_SHOULDER_Y = 0.82
    }
}
