package io.github.sgirishshiva.glyphwizard.glyph

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.MatrixFrameEncoder.Layout
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatrixFrameEncoderTest {
    private val profile = DeviceProfile.Phone4aPro

    private fun encoder(
        layout: Layout,
        maxValue: Int = 255,
    ) = MatrixFrameEncoder(profile, layout, maxValue)

    private fun ledIndexed() = IntArray(profile.ledCount) { it + 1 }

    @Test
    fun ledsOnlyKeepsOneValuePerLedInOrder() {
        val frame = encoder(Layout.LedsOnly).encode(ledIndexed())
        assertEquals(137, frame.size)
        assertContentEquals(ledIndexed(), frame)
    }

    @Test
    fun fullSquareHas169CellsWithDeadCornersOff() {
        val frame = encoder(Layout.FullSquare).encode(IntArray(profile.ledCount) { 255 })
        assertEquals(169, frame.size)
        listOf(0, 12, 156, 168).forEach { assertEquals(0, frame[it], "corner cell $it") }
        assertEquals(137, frame.count { it == 255 })
    }

    @Test
    fun fullSquarePlacesEachLedAtItsGridCell() {
        val frame = encoder(Layout.FullSquare).encode(ledIndexed())
        assertEquals(1, frame[0 * 13 + 4], "first LED at (4,0)")
        assertEquals(3, frame[0 * 13 + 6], "third LED at (6,0)")
        assertEquals(137, frame[12 * 13 + 8], "last LED at (8,12)")
    }

    @Test
    fun valuesAreClampedToTheInputRange() {
        val input =
            IntArray(profile.ledCount).also {
                it[0] = -5
                it[1] = 300
            }
        val frame = encoder(Layout.LedsOnly).encode(input)
        assertEquals(0, frame[0])
        assertEquals(255, frame[1])
    }

    @Test
    fun valuesScaleToTheDeviceMaximum() {
        val input =
            IntArray(profile.ledCount).also {
                it[0] = 255
                it[1] = 128
            }
        val frame = encoder(Layout.LedsOnly, maxValue = 2047).encode(input)
        assertEquals(2047, frame[0])
        assertEquals(1027, frame[1])
    }

    @Test
    fun wrongInputSizeIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { encoder(Layout.LedsOnly).encode(IntArray(169)) }
        assertEquals("expected 137 values, got 169", error.message)
    }

    @Test
    fun phone4aProDefaultsAreTheMeasuredOnes() {
        val encoder = MatrixFrameEncoder.forPhone4aPro()
        assertEquals(DeviceProfile.Phone4aPro, encoder.profile)
        assertEquals(Layout.FullSquare, encoder.layout)
        assertEquals(4095, encoder.maxValue)
    }
}
