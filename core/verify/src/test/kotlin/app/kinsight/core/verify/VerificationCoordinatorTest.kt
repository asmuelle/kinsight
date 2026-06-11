package app.kinsight.core.verify

import app.kinsight.core.classify.ClassifierDecision.FallCandidate
import app.kinsight.core.classify.Confidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** Invariant 3: deterministic before LLM, fail-open toward alerting. */
class VerificationCoordinatorTest {
    private val testTimeoutMs = 100L

    private fun candidate(confidence: Confidence): FallCandidate =
        FallCandidate(confidence = confidence, score = 0.7, dropDurationMs = 400, immobilitySeconds = 20.0)

    private fun hangingVerifier(calls: AtomicInteger) =
        FallVerifier { _ ->
            calls.incrementAndGet()
            Thread.sleep(testTimeoutMs * 100)
            VerifierVerdict.NOT_A_FALL
        }

    @Test
    fun `high-confidence fall alerts without ever consulting the verifier`() {
        // Arrange — a verifier that would suppress anything it sees
        val calls = AtomicInteger(0)
        val coordinator =
            VerificationCoordinator(
                verifier =
                    FallVerifier { _ ->
                        calls.incrementAndGet()
                        VerifierVerdict.NOT_A_FALL
                    },
                timeoutMs = testTimeoutMs,
            )

        // Act
        val outcome = coordinator.decide(candidate(Confidence.HIGH))

        // Assert
        assertTrue(outcome.shouldAlert)
        assertEquals(0, calls.get(), "verifier must never see a high-confidence candidate")
        assertNull(outcome.verdict)
    }

    @Test
    fun `high-confidence fall alerts even when verifier hangs`() {
        // Arrange
        val calls = AtomicInteger(0)
        val coordinator = VerificationCoordinator(hangingVerifier(calls), timeoutMs = testTimeoutMs)

        // Act
        val startedAt = System.nanoTime()
        val outcome = coordinator.decide(candidate(Confidence.HIGH))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        // Assert — immediate alert, no verifier involvement at all
        assertTrue(outcome.shouldAlert)
        assertEquals(0, calls.get())
        assertTrue(elapsedMs < testTimeoutMs) { "high-confidence path took ${elapsedMs}ms" }
    }

    @Test
    fun `medium candidate still alerts when the verifier hangs past the hard timeout`() {
        // Arrange
        val coordinator = VerificationCoordinator(hangingVerifier(AtomicInteger()), timeoutMs = testTimeoutMs)

        // Act
        val startedAt = System.nanoTime()
        val outcome = coordinator.decide(candidate(Confidence.MEDIUM))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        // Assert — fail open within budget
        assertTrue(outcome.shouldAlert)
        assertNull(outcome.verdict)
        assertTrue(elapsedMs < testTimeoutMs * 10) { "fail-open took ${elapsedMs}ms" }
    }

    @Test
    fun `medium candidate still alerts when the verifier throws`() {
        // Arrange
        val coordinator =
            VerificationCoordinator(
                verifier = FallVerifier { _ -> error("simulated OOM") },
                timeoutMs = testTimeoutMs,
            )

        // Act
        val outcome = coordinator.decide(candidate(Confidence.MEDIUM))

        // Assert
        assertTrue(outcome.shouldAlert)
    }

    @Test
    fun `verifier may suppress a low-confidence candidate with an explicit not-a-fall`() {
        // Arrange
        val coordinator =
            VerificationCoordinator(
                verifier = FallVerifier { _ -> VerifierVerdict.NOT_A_FALL },
                timeoutMs = testTimeoutMs,
            )

        // Act
        val outcome = coordinator.decide(candidate(Confidence.LOW))

        // Assert — the ONLY suppression path that exists
        assertFalse(outcome.shouldAlert)
        assertEquals(VerifierVerdict.NOT_A_FALL, outcome.verdict)
    }

    @Test
    fun `verifier verdict unavailable means alert anyway`() {
        // Arrange
        val coordinator =
            VerificationCoordinator(
                verifier = FallVerifier { _ -> VerifierVerdict.UNAVAILABLE },
                timeoutMs = testTimeoutMs,
            )

        // Act
        val outcome = coordinator.decide(candidate(Confidence.MEDIUM))

        // Assert
        assertTrue(outcome.shouldAlert)
        assertEquals(VerifierVerdict.UNAVAILABLE, outcome.verdict)
    }

    @Test
    fun `sub-4GB deterministic path alerts on medium candidates without a verifier`() {
        // Arrange
        val coordinator = VerificationCoordinator(verifier = null, timeoutMs = testTimeoutMs)

        // Act
        val outcome = coordinator.decide(candidate(Confidence.MEDIUM))

        // Assert
        assertTrue(outcome.shouldAlert)
        assertNull(outcome.verdict)
    }
}
