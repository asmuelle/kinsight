package app.kinsight.core.pose

/**
 * In-memory ring buffer of recent [PoseFrame]s scored by the temporal
 * classifier.
 *
 * Invariant 2 (no frame persistence): this is the ONLY place pose history
 * lives. There is deliberately no serialization, file, or export API on this
 * type — history is bounded by [capacity] and evicted oldest-first.
 */
class PoseWindow(
    private val capacity: Int = DEFAULT_CAPACITY_FRAMES,
) {
    init {
        require(capacity > 0) { "PoseWindow capacity must be positive, got $capacity" }
    }

    private val frames = ArrayDeque<PoseFrame>(capacity)

    val size: Int get() = frames.size

    fun append(frame: PoseFrame) {
        if (frames.size == capacity) frames.removeFirst()
        frames.addLast(frame)
    }

    /** Immutable copy for scoring — callers can never mutate window state. */
    fun snapshot(): List<PoseFrame> = frames.toList()

    fun clear() = frames.clear()

    companion object {
        /** ~60s of history at 10 fps — enough for drop + 30s immobility. */
        const val DEFAULT_CAPACITY_FRAMES: Int = 600
    }
}
