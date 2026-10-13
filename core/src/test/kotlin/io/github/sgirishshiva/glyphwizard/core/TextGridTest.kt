package io.github.sgirishshiva.glyphwizard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextGridTest {
    private val profile = DeviceProfile.Phone4aPro

    private fun encodeAll(value: Int): String = TextGrid.encode(profile, IntArray(profile.ledCount) { value })

    /** The glyph at the first LED (4,0) when only that LED has [value]. */
    private fun glyphFor(value: Int): Char {
        val brightness = IntArray(profile.ledCount).also { it[0] = value }
        return TextGrid.encode(profile, brightness).lines()[0][4]
    }

    @Test
    fun `renders 13 lines of 13 characters`() {
        val lines = encodeAll(0).lines()
        assertEquals(13, lines.size)
        assertEquals(List(13) { 13 }, lines.map { it.length })
    }

    @Test
    fun `all off renders a dot at every LED and spaces outside the mask`() {
        val text = encodeAll(0)
        assertEquals(137, text.count { it == '·' })
        assertEquals(32, text.count { it == ' ' })
        assertEquals("    ·····    ", text.lines()[0])
    }

    @Test
    fun `brightness buckets have the documented edges`() {
        assertEquals('.', glyphFor(1))
        assertEquals('.', glyphFor(29))
        assertEquals(':', glyphFor(30))
        assertEquals('@', glyphFor(255))
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals('·', glyphFor(-5))
        assertEquals('@', glyphFor(300))
    }

    @Test
    fun `values follow led order`() {
        val brightness = IntArray(profile.ledCount).also { it[it.lastIndex] = 255 }
        assertEquals("    ····@    ", TextGrid.encode(profile, brightness).lines()[12])
    }

    @Test
    fun `wrong array size is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> { TextGrid.encode(profile, IntArray(136)) }
        assertEquals("expected 137 values, got 136", error.message)
    }
}
