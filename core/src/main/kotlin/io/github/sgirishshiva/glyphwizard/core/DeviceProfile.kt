package io.github.sgirishshiva.glyphwizard.core

/**
 * The LED layout of one Glyph Matrix: a square grid whose rows are centred runs of LEDs.
 * Provisional until the D46 architecture deep-dive.
 */
data class DeviceProfile(
    val id: String,
    val gridSize: Int,
    val rowWidths: List<Int>,
) {
    val ledCount: Int = rowWidths.sum()

    fun isLed(
        x: Int,
        y: Int,
    ): Boolean {
        val width = rowWidths.getOrNull(y) ?: return false
        val inset = (gridSize - width) / 2
        return x in inset..<inset + width
    }

    /** LED coordinates in row-major order: the order every brightness array follows. */
    fun ledPositions(): List<Pair<Int, Int>> =
        buildList {
            for (y in 0..<gridSize) {
                for (x in 0..<gridSize) {
                    if (isLed(x, y)) add(x to y)
                }
            }
        }

    companion object {
        val Phone4aPro = DeviceProfile("25111p", 13, listOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5))
    }
}
