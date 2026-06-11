package app.kinsight.core.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LumaMotionGateTest {
    private fun frame(
        value: Int,
        timestampMs: Long,
        side: Int = 8,
    ): LumaFrame = LumaFrame(side, side, ByteArray(side * side) { value.toByte() }, timestampMs)

    @Test
    fun `gate stays closed on identical frames`() {
        // Arrange
        val gate = LumaMotionGate()

        // Act
        gate.process(frame(value = 100, timestampMs = 0))
        val isOpen = gate.process(frame(value = 100, timestampMs = 100))

        // Assert
        assertFalse(isOpen)
    }

    @Test
    fun `gate opens when enough pixels change`() {
        // Arrange
        val gate = LumaMotionGate()
        gate.process(frame(value = 100, timestampMs = 0))

        // Act
        val isOpen = gate.process(frame(value = 160, timestampMs = 100))

        // Assert
        assertTrue(isOpen)
    }

    @Test
    fun `gate ignores sub-threshold pixel noise`() {
        // Arrange
        val gate = LumaMotionGate()
        gate.process(frame(value = 100, timestampMs = 0))

        // Act — delta of 5 is below DEFAULT_PIXEL_DELTA_THRESHOLD (18)
        val isOpen = gate.process(frame(value = 105, timestampMs = 100))

        // Assert
        assertFalse(isOpen)
    }

    @Test
    fun `gate closes again after the quiet period`() {
        // Arrange
        val gate = LumaMotionGate(quietMsToClose = 1_000)
        gate.process(frame(value = 100, timestampMs = 0))
        gate.process(frame(value = 200, timestampMs = 100))

        // Act
        val stillOpen = gate.isOpenAt(nowMs = 1_000)
        val closedLater = gate.isOpenAt(nowMs = 1_200)

        // Assert
        assertTrue(stillOpen)
        assertFalse(closedLater)
    }

    @Test
    fun `resolution change resets the baseline instead of diffing garbage`() {
        // Arrange
        val gate = LumaMotionGate()
        gate.process(frame(value = 100, timestampMs = 0, side = 8))

        // Act — different resolution: no diff possible, must not open or throw
        val isOpen = gate.process(frame(value = 250, timestampMs = 100, side = 4))

        // Assert
        assertFalse(isOpen)
    }

    @Test
    fun `luma frame rejects mismatched plane size`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            LumaFrame(width = 4, height = 4, luma = ByteArray(3), timestampMs = 0)
        }
    }

    @Test
    fun `thermal governor idles at 1 fps when the gate is closed`() {
        // Arrange / Act / Assert
        assertEquals(ThermalGovernor.IDLE_FPS, ThermalGovernor.targetFps(ThermalState.NOMINAL, isGateOpen = false))
        assertEquals(ThermalGovernor.IDLE_FPS, ThermalGovernor.targetFps(ThermalState.CRITICAL, isGateOpen = false))
    }

    @Test
    fun `thermal governor scales pose rate with thermal state while open`() {
        // Arrange / Act / Assert
        assertEquals(ThermalGovernor.FULL_FPS, ThermalGovernor.targetFps(ThermalState.NOMINAL, isGateOpen = true))
        assertEquals(ThermalGovernor.REDUCED_FPS, ThermalGovernor.targetFps(ThermalState.ELEVATED, isGateOpen = true))
        assertEquals(ThermalGovernor.HOT_FPS, ThermalGovernor.targetFps(ThermalState.CRITICAL, isGateOpen = true))
    }
}
