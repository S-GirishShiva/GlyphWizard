package io.github.sgirishshiva.glyphwizard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceProfileTest {
    private val profile = DeviceProfile.Phone4aPro

    @Test
    fun `phone 4a pro has 137 LEDs`() {
        assertEquals(137, profile.ledCount)
    }

    @Test
    fun `corners of the square are not LEDs`() {
        assertFalse(profile.isLed(0, 0))
        assertFalse(profile.isLed(12, 0))
        assertFalse(profile.isLed(0, 12))
        assertFalse(profile.isLed(12, 12))
    }

    @Test
    fun `top row covers columns 4 to 8 only`() {
        assertTrue((4..8).all { profile.isLed(it, 0) })
        assertFalse(profile.isLed(3, 0))
        assertFalse(profile.isLed(9, 0))
    }

    @Test
    fun `middle row is full`() {
        assertTrue((0..<13).all { profile.isLed(it, 6) })
    }

    @Test
    fun `coordinates outside the grid are not LEDs`() {
        assertFalse(profile.isLed(-1, 6))
        assertFalse(profile.isLed(13, 6))
        assertFalse(profile.isLed(6, -1))
        assertFalse(profile.isLed(6, 13))
    }

    @Test
    fun `led positions are row-major and match the LED count`() {
        val positions = profile.ledPositions()
        assertEquals(137, positions.size)
        assertEquals(4 to 0, positions.first())
        assertEquals(8 to 12, positions.last())
        assertEquals(positions.sortedWith(compareBy({ it.second }, { it.first })), positions)
    }
}
