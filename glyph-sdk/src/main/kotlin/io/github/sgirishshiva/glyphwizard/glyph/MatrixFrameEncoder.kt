package io.github.sgirishshiva.glyphwizard.glyph

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile

/** Turns LED-ordered brightness into the int array the device expects. */
class MatrixFrameEncoder(
    val profile: DeviceProfile,
    val layout: Layout,
    val maxValue: Int,
) {
    enum class Layout {
        /** One value per LED, in LED order. */
        LedsOnly,

        /** One value per grid cell, row-major, with 0 outside the LED mask. */
        FullSquare,
    }

    private val squareIndices: IntArray = profile.ledPositions().map { (x, y) -> y * profile.gridSize + x }.toIntArray()

    fun encode(brightness: IntArray): IntArray {
        require(brightness.size == profile.ledCount) { "expected ${profile.ledCount} values, got ${brightness.size}" }
        val scaled = IntArray(brightness.size) { toDeviceValue(brightness[it]) }
        return when (layout) {
            Layout.LedsOnly -> {
                scaled
            }

            Layout.FullSquare -> {
                IntArray(profile.gridSize * profile.gridSize).also { square ->
                    squareIndices.forEachIndexed { led, cell -> square[cell] = scaled[led] }
                }
            }
        }
    }

    // rounds up: the 4a Pro driver floors value × 255 / 4095 to its PWM step, so rounding down would land every stored
    // level just below its step (stored 1 would stay dark); rounding up puts stored s exactly on step s
    private fun toDeviceValue(stored: Int): Int = (stored.coerceIn(0, MAX_INPUT) * maxValue + MAX_INPUT - 1) / MAX_INPUT

    companion object {
        private const val MAX_INPUT = 255

        // the LED driver is 12-bit: values pass through unchanged and keep getting brighter up to 4095, despite the
        // SDK README's "capped at 255" (M0 hardware spike, D56)
        private const val PHONE_4A_PRO_MAX = 4095

        /** Measured on the device (hardware-spike-results.md): 13 × 13 grid frame, full 12-bit LED range. */
        fun forPhone4aPro(): MatrixFrameEncoder =
            MatrixFrameEncoder(DeviceProfile.Phone4aPro, Layout.FullSquare, PHONE_4A_PRO_MAX)
    }
}
