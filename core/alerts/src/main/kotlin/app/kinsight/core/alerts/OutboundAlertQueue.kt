package app.kinsight.core.alerts

import app.kinsight.core.transport.RelayClient
import app.kinsight.core.transport.SealedEnvelope
import app.kinsight.core.transport.SendResult

/**
 * Invariant 5, network half: alerts queue while offline and survive failed
 * sends; nothing is dropped until the relay confirms delivery.
 */
class OutboundAlertQueue {
    private val lock = Any()
    private val pending = ArrayDeque<SealedEnvelope>()

    val pendingCount: Int get() = synchronized(lock) { pending.size }

    fun enqueue(envelope: SealedEnvelope) {
        synchronized(lock) { pending.addLast(envelope) }
    }

    /** Attempts delivery of everything pending; keeps whatever fails. */
    fun flush(
        relay: RelayClient,
        connectivity: Connectivity,
    ): Int {
        if (!connectivity.isOnline()) return 0
        var delivered = 0
        synchronized(lock) {
            val retained = ArrayDeque<SealedEnvelope>()
            while (pending.isNotEmpty()) {
                val envelope = pending.removeFirst()
                when (relay.send(envelope)) {
                    is SendResult.Delivered -> delivered++
                    is SendResult.Failed -> retained.addLast(envelope)
                }
            }
            pending.addAll(retained)
        }
        return delivered
    }
}
