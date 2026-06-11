package app.kinsight.core.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OklchColorTest {
    @Test
    fun `parses the canonical token syntax`() {
        // Arrange / Act
        val color = OklchColor.parse("oklch(96% 0.015 85)")

        // Assert
        assertEquals(0.96, color.lightness, 1e-9)
        assertEquals(0.015, color.chroma, 1e-9)
        assertEquals(85.0, color.hueDegrees, 1e-9)
    }

    @Test
    fun `rejects strings that are not oklch tokens`() {
        assertThrows(IllegalArgumentException::class.java) { OklchColor.parse("#aabbcc") }
        assertThrows(IllegalArgumentException::class.java) { OklchColor.parse("oklch(96% 0.015)") }
    }

    @Test
    fun `full lightness with zero chroma converts to white`() {
        // Arrange / Act
        val argb = OklchColor.parse("oklch(100% 0 0)").toArgb()

        // Assert
        assertEquals(0xFFFFFFFF.toInt(), argb)
    }

    @Test
    fun `zero lightness converts to black`() {
        assertEquals(0xFF000000.toInt(), OklchColor.parse("oklch(0% 0 0)").toArgb())
    }

    @Test
    fun `every hearthlight token parses and is fully opaque`() {
        // Arrange
        val tokens =
            listOf(
                HearthlightTokens.SURFACE_OAT,
                HearthlightTokens.BRAND_PINE,
                HearthlightTokens.ALERT_EMBER,
            )

        // Act / Assert
        tokens.forEach { token ->
            val argb = OklchColor.parse(token).toArgb()
            assertEquals(0xFF, (argb ushr 24) and 0xFF) { "token $token must be opaque" }
        }
    }

    @Test
    fun `brand pine is dominated by its green channel`() {
        // Arrange / Act
        val argb = OklchColor.parse(HearthlightTokens.BRAND_PINE).toArgb()
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF

        // Assert — pine is a green; the hue must survive the conversion
        assertTrue(green > red) { "expected green > red, got r=$red g=$green" }
        assertTrue(green > blue) { "expected green > blue, got g=$green b=$blue" }
    }

    @Test
    fun `alert ember is a warm amber - red and green well above blue`() {
        // Arrange / Act
        val argb = OklchColor.parse(HearthlightTokens.ALERT_EMBER).toArgb()
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF

        // Assert
        assertTrue(red > blue && green > blue) { "expected warm hue, got r=$red g=$green b=$blue" }
    }

    @Test
    fun `surface oat is a near-white warm paper tone`() {
        // Arrange / Act
        val argb = OklchColor.parse(HearthlightTokens.SURFACE_OAT).toArgb()
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF

        // Assert — bright across all channels, slightly warm (blue lowest)
        assertTrue(minOf(red, green, blue) > 200) { "expected near-white, got r=$red g=$green b=$blue" }
        assertTrue(blue <= red && blue <= green) { "expected warm bias, got r=$red g=$green b=$blue" }
    }
}
