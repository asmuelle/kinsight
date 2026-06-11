package app.kinsight.core.classify

import app.kinsight.core.classify.ClassifierDecision.FallCandidate
import app.kinsight.core.classify.ClassifierDecision.NoFall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeuristicFallClassifierTest {
    private val classifier = HeuristicFallClassifier()

    @Test
    fun `staged fall with 35s immobility scores a high-confidence candidate`() {
        // Arrange
        val window = GoldenSequences.fallThenStill(immobileMs = 35_000)

        // Act
        val decision = classifier.classify(window)

        // Assert
        val candidate = assertInstanceOf(FallCandidate::class.java, decision)
        assertEquals(Confidence.HIGH, candidate.confidence)
        assertEquals(1.0, candidate.score, 1e-9)
        assertTrue(candidate.dropDurationMs <= FallHeuristics.MAX_DROP_DURATION_MS)
        assertTrue(candidate.immobilitySeconds >= FallHeuristics.HIGH_IMMOBILITY_SECONDS)
    }

    @Test
    fun `fall with 16s stillness then floor movement is medium confidence`() {
        // Arrange
        val window = GoldenSequences.fallStillThenWrithing(immobileMs = 16_000, writhingMs = 26_000)

        // Act
        val decision = classifier.classify(window)

        // Assert
        val candidate = assertInstanceOf(FallCandidate::class.java, decision)
        assertEquals(Confidence.MEDIUM, candidate.confidence)
    }

    @Test
    fun `fall with only 4s stillness is a low-confidence candidate`() {
        // Arrange
        val window = GoldenSequences.fallStillThenWrithing(immobileMs = 4_000, writhingMs = 14_000)

        // Act
        val decision = classifier.classify(window)

        // Assert
        val candidate = assertInstanceOf(FallCandidate::class.java, decision)
        assertEquals(Confidence.LOW, candidate.confidence)
    }

    @Test
    fun `slow couch lie-down never becomes a candidate`() {
        // Arrange / Act
        val decision = classifier.classify(GoldenSequences.couchLieDown())

        // Assert — no rapid drop, so lying still for minutes is fine
        assertEquals(NoFall, decision)
    }

    @Test
    fun `kneeling is not a fall`() {
        // Arrange / Act
        val decision = classifier.classify(GoldenSequences.kneelDown())

        // Assert
        assertEquals(NoFall, decision)
    }

    @Test
    fun `sitting down fast stays upright and is not a fall`() {
        // Arrange / Act
        val decision = classifier.classify(GoldenSequences.rapidSit())

        // Assert — rapid hip drop alone is insufficient without a horizontal trunk
        assertEquals(NoFall, decision)
    }

    @Test
    fun `pet motion with low-confidence keypoints is ignored`() {
        // Arrange / Act
        val decision = classifier.classify(GoldenSequences.petMotion())

        // Assert
        assertEquals(NoFall, decision)
    }

    @Test
    fun `empty window is not scoreable`() {
        // Arrange / Act / Assert
        assertEquals(NoFall, classifier.classify(emptyList()))
    }
}
