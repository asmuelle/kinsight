package app.kinsight.core.verify

import app.kinsight.core.classify.ClassifierDecision.FallCandidate
import app.kinsight.core.classify.Confidence
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Outcome of the verification step on the alert path. */
data class VerificationOutcome(
    val shouldAlert: Boolean,
    val verdict: VerifierVerdict?,
    val verifierLatencyMs: Long?,
)

/**
 * Invariant 3 — deterministic before LLM, fail-open toward alerting:
 * - HIGH-confidence candidates alert immediately; the verifier is never consulted.
 * - LOW/MEDIUM candidates may be suppressed only by an explicit NOT_A_FALL
 *   returned within the hard timeout.
 * - Timeout, exception, UNAVAILABLE, or a missing verifier (sub-4GB device)
 *   all mean: alert anyway.
 */
class VerificationCoordinator(
    private val verifier: FallVerifier?,
    private val timeoutMs: Long = VERIFIER_TIMEOUT_MS,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    fun decide(candidate: FallCandidate): VerificationOutcome {
        if (candidate.confidence == Confidence.HIGH) {
            return VerificationOutcome(shouldAlert = true, verdict = null, verifierLatencyMs = null)
        }
        val activeVerifier =
            verifier
                ?: return VerificationOutcome(shouldAlert = true, verdict = null, verifierLatencyMs = null)
        return verifyFailOpen(activeVerifier, candidate)
    }

    private fun verifyFailOpen(
        activeVerifier: FallVerifier,
        candidate: FallCandidate,
    ): VerificationOutcome {
        val startedAt = System.nanoTime()
        return try {
            val future = executor.submit<VerifierVerdict> { activeVerifier.verify(candidate) }
            val verdict = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            val latencyMs = elapsedMsSince(startedAt)
            VerificationOutcome(
                shouldAlert = verdict != VerifierVerdict.NOT_A_FALL,
                verdict = verdict,
                verifierLatencyMs = latencyMs,
            )
        } catch (_: TimeoutException) {
            VerificationOutcome(shouldAlert = true, verdict = null, verifierLatencyMs = elapsedMsSince(startedAt))
        } catch (_: Exception) {
            // Fail open: any verifier crash/OOM/interrupt must still alert.
            VerificationOutcome(shouldAlert = true, verdict = null, verifierLatencyMs = elapsedMsSince(startedAt))
        }
    }

    private fun elapsedMsSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MS

    companion object {
        /** Hard verifier budget (DESIGN.md: 3s, fail-open). */
        const val VERIFIER_TIMEOUT_MS: Long = 3_000L

        private const val NANOS_PER_MS: Long = 1_000_000L
    }
}
