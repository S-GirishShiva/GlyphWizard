package io.github.sgirishshiva.glyphwizard.core

/**
 * Text rendering of one matrix frame: `·` for an unlit LED, `.:-=+*#%@` for brightness 1..255, and a space outside
 * the LED mask. Provisional until the D46 architecture deep-dive.
 */
object TextGrid {
    private const val LEVELS = ".:-=+*#%@"
    private const val MAX_BRIGHTNESS = 255

    /** [brightness] holds one 0..255 value per LED, in [DeviceProfile.ledPositions] order. */
    fun encode(
        profile: DeviceProfile,
        brightness: IntArray,
    ): String {
        require(brightness.size == profile.ledCount) { "expected ${profile.ledCount} values, got ${brightness.size}" }
        val values = brightness.iterator()
        return (0..<profile.gridSize).joinToString("\n") { y ->
            (0..<profile.gridSize).joinToString("") { x -> if (profile.isLed(x, y)) glyph(values.next()) else " " }
        }
    }

    private fun glyph(value: Int): String =
        when (val level = value.coerceIn(0, MAX_BRIGHTNESS)) {
            0 -> "·"

            // 1..255 maps onto indices 0..8; (254 * 9) / 255 = 8, so it never passes the last index
            else -> LEVELS[(level - 1) * LEVELS.length / MAX_BRIGHTNESS].toString()
        }
}
