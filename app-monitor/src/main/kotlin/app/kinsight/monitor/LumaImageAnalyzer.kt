package app.kinsight.monitor

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.kinsight.core.capture.LumaFrame
import java.nio.ByteBuffer

/**
 * Extracts the Y (luma) plane from each camera frame into an in-memory
 * [LumaFrame] and hands it to the pipeline. Invariant 2: the frame is closed
 * immediately and nothing here can write to disk — there is no file API in
 * this class or anywhere downstream of it.
 */
internal class LumaImageAnalyzer(
    private val onFrame: (LumaFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            extractLuma(image)?.let(onFrame)
        } finally {
            image.close()
        }
    }

    private fun extractLuma(image: ImageProxy): LumaFrame? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val luma = ByteArray(width * height)
        copyPlane(plane.buffer, luma, width, height, plane.rowStride)
        return LumaFrame(
            width = width,
            height = height,
            luma = luma,
            timestampMs = image.imageInfo.timestamp / NANOS_PER_MS,
        )
    }

    private fun copyPlane(
        buffer: ByteBuffer,
        out: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
    ) {
        if (rowStride == width) {
            buffer.get(out, 0, minOf(buffer.remaining(), out.size))
            return
        }
        for (row in 0 until height) {
            val offset = row * rowStride
            if (offset >= buffer.limit()) return
            buffer.position(offset)
            buffer.get(out, row * width, minOf(width, buffer.remaining()))
        }
    }

    private companion object {
        const val NANOS_PER_MS: Long = 1_000_000L
    }
}
