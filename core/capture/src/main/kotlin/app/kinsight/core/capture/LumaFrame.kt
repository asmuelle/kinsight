package app.kinsight.core.capture

/**
 * A single grayscale (luma-plane) frame. Frames exist only in memory and are
 * consumed by the motion gate / pose pipeline — Invariant 2: nothing
 * image-like is ever written to disk, logged, or attached anywhere.
 */
class LumaFrame(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val timestampMs: Long,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive: ${width}x$height" }
        require(luma.size == width * height) {
            "Luma plane size ${luma.size} does not match ${width}x$height"
        }
    }

    val pixelCount: Int get() = luma.size
}
