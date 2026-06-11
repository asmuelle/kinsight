package app.kinsight.core.design

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Parses the `oklch(L% C H)` token strings in [HearthlightTokens] and
 * converts them to sRGB so Compose themes DERIVE from the tokens instead of
 * restating hex values (DESIGN.md: tokens are the single source of truth).
 *
 * Conversion: OKLCH -> OKLab -> LMS -> linear sRGB -> gamma-encoded sRGB,
 * per Björn Ottosson's published OKLab matrices.
 */
data class OklchColor(
    val lightness: Double,
    val chroma: Double,
    val hueDegrees: Double,
) {
    init {
        require(lightness in 0.0..1.0) { "lightness must be in [0,1], got $lightness" }
        require(chroma >= 0.0) { "chroma must be >= 0, got $chroma" }
    }

    /** Fully opaque ARGB int (0xFFRRGGBB), clamped into sRGB gamut. */
    fun toArgb(): Int {
        val hueRadians = hueDegrees * PI / HALF_TURN_DEGREES
        val labA = chroma * cos(hueRadians)
        val labB = chroma * sin(hueRadians)

        val longCone = (lightness + A_TO_L * labA + B_TO_L * labB).pow(CONE_EXPONENT)
        val mediumCone = (lightness + A_TO_M * labA + B_TO_M * labB).pow(CONE_EXPONENT)
        val shortCone = (lightness + A_TO_S * labA + B_TO_S * labB).pow(CONE_EXPONENT)

        val red = gammaEncode(L_TO_R * longCone + M_TO_R * mediumCone + S_TO_R * shortCone)
        val green = gammaEncode(L_TO_G * longCone + M_TO_G * mediumCone + S_TO_G * shortCone)
        val blue = gammaEncode(L_TO_B * longCone + M_TO_B * mediumCone + S_TO_B * shortCone)

        return (OPAQUE_ALPHA shl ALPHA_SHIFT) or
            (toChannel(red) shl RED_SHIFT) or
            (toChannel(green) shl GREEN_SHIFT) or
            toChannel(blue)
    }

    private fun gammaEncode(linear: Double): Double {
        val clamped = linear.coerceIn(0.0, 1.0)
        return if (clamped <= LINEAR_SEGMENT_MAX) {
            LINEAR_SLOPE * clamped
        } else {
            GAMMA_SCALE * clamped.pow(1.0 / GAMMA_EXPONENT) - GAMMA_OFFSET
        }
    }

    private fun toChannel(value: Double): Int = (value.coerceIn(0.0, 1.0) * CHANNEL_MAX).roundToInt()

    companion object {
        /** Parses `oklch(<L>% <C> <H>)` exactly as written in the tokens. */
        fun parse(token: String): OklchColor {
            val match =
                TOKEN_PATTERN.matchEntire(token.trim())
                    ?: throw IllegalArgumentException("Not an oklch(L% C H) token: '$token'")
            val (lightnessPercent, chroma, hue) = match.destructured
            return OklchColor(
                lightness = lightnessPercent.toDouble() / PERCENT,
                chroma = chroma.toDouble(),
                hueDegrees = hue.toDouble(),
            )
        }

        private val TOKEN_PATTERN =
            Regex("""oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)\s*\)""")

        private const val PERCENT = 100.0
        private const val HALF_TURN_DEGREES = 180.0
        private const val OPAQUE_ALPHA = 0xFF
        private const val CHANNEL_MAX = 255.0
        private const val CONE_EXPONENT = 3
        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8

        // OKLab -> non-linear LMS (Ottosson M2^-1).
        private const val A_TO_L = 0.3963377774
        private const val B_TO_L = 0.2158037573
        private const val A_TO_M = -0.1055613458
        private const val B_TO_M = -0.0638541728
        private const val A_TO_S = -0.0894841775
        private const val B_TO_S = -1.2914855480

        // Linear LMS -> linear sRGB (Ottosson M1^-1).
        private const val L_TO_R = 4.0767416621
        private const val M_TO_R = -3.3077115913
        private const val S_TO_R = 0.2309699292
        private const val L_TO_G = -1.2684380046
        private const val M_TO_G = 2.6097574011
        private const val S_TO_G = -0.3413193965
        private const val L_TO_B = -0.0041960863
        private const val M_TO_B = -0.7034186147
        private const val S_TO_B = 1.7076147010

        // sRGB transfer function.
        private const val LINEAR_SEGMENT_MAX = 0.0031308
        private const val LINEAR_SLOPE = 12.92
        private const val GAMMA_SCALE = 1.055
        private const val GAMMA_OFFSET = 0.055
        private const val GAMMA_EXPONENT = 2.4
    }
}
