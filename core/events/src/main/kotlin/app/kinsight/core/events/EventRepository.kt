package app.kinsight.core.events

/**
 * Metadata-only event store boundary (repository pattern). The M2 production
 * implementation is Room on the donor phone; M1 and all tests use
 * [InMemoryEventRepository]. Nothing image-like can pass through this API —
 * the entity types carry no binary fields at all.
 */
interface EventRepository {
    fun saveEvent(event: CandidateEvent)

    fun findEvent(id: String): CandidateEvent?

    fun saveAlert(alert: Alert)

    fun findAlert(id: String): Alert?

    /** Applies [transform] to a stored alert, persisting and returning the new copy. */
    fun updateAlert(
        id: String,
        transform: (Alert) -> Alert,
    ): Alert?

    fun alerts(): List<Alert>
}

/** Thread-safe in-memory store; values are immutable data classes. */
class InMemoryEventRepository : EventRepository {
    private val lock = Any()
    private val eventsById = LinkedHashMap<String, CandidateEvent>()
    private val alertsById = LinkedHashMap<String, Alert>()

    override fun saveEvent(event: CandidateEvent) {
        synchronized(lock) { eventsById[event.id] = event }
    }

    override fun findEvent(id: String): CandidateEvent? = synchronized(lock) { eventsById[id] }

    override fun saveAlert(alert: Alert) {
        synchronized(lock) { alertsById[alert.id] = alert }
    }

    override fun findAlert(id: String): Alert? = synchronized(lock) { alertsById[id] }

    override fun updateAlert(
        id: String,
        transform: (Alert) -> Alert,
    ): Alert? =
        synchronized(lock) {
            val current = alertsById[id] ?: return null
            val updated = transform(current)
            alertsById[id] = updated
            updated
        }

    override fun alerts(): List<Alert> = synchronized(lock) { alertsById.values.toList() }
}
