package app.kinsight.core.transport

/** Result of one relay send attempt. */
sealed interface SendResult {
    data object Delivered : SendResult

    data class Failed(
        val reason: String,
    ) : SendResult
}

/**
 * The ONLY network boundary in the entire codebase (AGENTS.md dependency
 * rule): everything that leaves the house goes through this interface as a
 * [SealedEnvelope]. The production implementation (FCM HTTP v1 relay, M1+)
 * lives behind this; tests and the JVM slice use recording fakes.
 */
fun interface RelayClient {
    fun send(envelope: SealedEnvelope): SendResult
}
