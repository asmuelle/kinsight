package app.kinsight.core.pose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PoseWindowTest {
    private fun frame(
        timestampMs: Long,
        hipY: Double = 0.55,
        shoulderY: Double = 0.30,
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

    @Test
    fun `evicts oldest frame once capacity is reached`() {
        // Arrange
        val window = PoseWindow(capacity = 3)

        // Act
        (1L..4L).forEach { window.append(frame(timestampMs = it)) }

        // Assert
        assertEquals(3, window.size)
        assertEquals(listOf(2L, 3L, 4L), window.snapshot().map { it.timestampMs })
    }

    @Test
    fun `snapshot is an immutable copy decoupled from later appends`() {
        // Arrange
        val window = PoseWindow(capacity = 5)
        window.append(frame(timestampMs = 1))

        // Act
        val snapshot = window.snapshot()
        window.append(frame(timestampMs = 2))

        // Assert — Invariant 2: scoring sees a frozen copy, never live state
        assertEquals(1, snapshot.size)
        assertEquals(2, window.size)
    }

    @Test
    fun `rejects non-positive capacity`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) { PoseWindow(capacity = 0) }
    }

    @Test
    fun `pose frame exposes hip center and trunk verticality`() {
        // Arrange
        val standing = frame(timestampMs = 0, hipY = 0.55, shoulderY = 0.30)

        // Act / Assert
        assertEquals(0.55, standing.hipCenterY()!!, 1e-9)
        assertEquals(0.30, standing.shoulderCenterY()!!, 1e-9)
        assertEquals(0.25, standing.trunkVerticality()!!, 1e-9)
    }

    @Test
    fun `geometry helpers return null when landmarks are missing`() {
        // Arrange
        val noHips = PoseFrame(timestampMs = 0, keypoints = emptyMap())

        // Act / Assert
        assertNull(noHips.hipCenterY())
        assertNull(noHips.trunkVerticality())
    }

    @Test
    fun `low-confidence keypoints do not count as a person`() {
        // Arrange
        val petBlob = frame(timestampMs = 0, confidence = 0.2)

        // Act / Assert
        assertFalse(petBlob.hasConfidentPerson(minConfidence = 0.5))
        assertTrue(frame(timestampMs = 0).hasConfidentPerson(minConfidence = 0.5))
    }
}
