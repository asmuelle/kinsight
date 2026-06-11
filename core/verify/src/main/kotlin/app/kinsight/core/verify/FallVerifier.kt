package app.kinsight.core.verify

import app.kinsight.core.classify.ClassifierDecision.FallCandidate

/** Verdict from the on-device verifier (Gemma 3n at M2; fakes at M1). */
enum class VerifierVerdict { FALL_CONFIRMED, NOT_A_FALL, UNAVAILABLE }

/**
 * Boundary interface for the small-LLM verifier. The production
 * implementation (MediaPipe LLM Inference + Gemma 3n, M2) lives in the
 * monitor app; everything here is verifiable with deterministic fakes —
 * no model, no API key, ever.
 */
fun interface FallVerifier {
    fun verify(candidate: FallCandidate): VerifierVerdict
}
