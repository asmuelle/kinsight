package app.kinsight.core.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HearthlightTokensTest {
    private val oklchPattern = Regex("""oklch\(\d+% 0\.\d+ \d+\)""")

    @Test
    fun `palette tokens are well-formed oklch values`() {
        // Arrange / Act / Assert
        listOf(
            HearthlightTokens.SURFACE_OAT,
            HearthlightTokens.BRAND_PINE,
            HearthlightTokens.ALERT_EMBER,
        ).forEach { token ->
            assertTrue(oklchPattern.matches(token)) { "malformed token: $token" }
        }
    }

    @Test
    fun `ember is reserved for alerts and distinct from brand and surface`() {
        // Arrange / Act / Assert — semantic color discipline (DESIGN.md)
        assertNotEquals(HearthlightTokens.ALERT_EMBER, HearthlightTokens.BRAND_PINE)
        assertNotEquals(HearthlightTokens.ALERT_EMBER, HearthlightTokens.SURFACE_OAT)
    }

    @Test
    fun `touch targets meet the elder-legible minimum`() {
        // Arrange / Act / Assert — DESIGN.md: >= 56dp everywhere
        assertTrue(HearthlightTokens.MIN_TOUCH_TARGET_DP >= 56)
    }

    @Test
    fun `typography pairing is fraunces display over hyperlegible body`() {
        // Arrange / Act / Assert
        assertEquals("Fraunces", HearthlightTokens.FONT_DISPLAY)
        assertEquals("Atkinson Hyperlegible", HearthlightTokens.FONT_BODY)
    }
}
