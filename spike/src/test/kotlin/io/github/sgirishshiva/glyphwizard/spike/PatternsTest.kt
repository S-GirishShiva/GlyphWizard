package io.github.sgirishshiva.glyphwizard.spike

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PatternsTest {
    private val profile = DeviceProfile.Phone4aPro
    private val positions = profile.ledPositions()

    private fun Pattern.at(frameTime: Duration = Duration.ZERO) = this(profile, frameTime)

    private fun IntArray.litCount() = count { it > 0 }

    @Test
    fun everyPatternGivesOneInRangeValuePerLed() {
        val patterns = listOf(checker, all(128), ramp, rowsSweep(20.milliseconds), single(6, 6), flash(10), cornerProbe)
        listOf(Duration.ZERO, 37.milliseconds, 2.seconds).forEach { time ->
            patterns.forEach { pattern ->
                val frame = pattern.at(time)
                assertEquals(137, frame.size)
                assertTrue(frame.all { it in 0..255 })
            }
        }
    }

    @Test
    fun checkerAlternatesBetweenNeighboursInARow() {
        val frame = checker.at()
        positions.zipWithNext().forEachIndexed { i, (a, b) ->
            if (a.second == b.second) assertTrue(frame[i] != frame[i + 1], "LEDs $i and ${i + 1} share a value")
        }
    }

    @Test
    fun allLightsEveryLedAtTheLevel() {
        assertTrue(all(42).at().all { it == 42 })
    }

    @Test
    fun rampRisesAcrossEightBands() {
        val frame = ramp.at()
        val middleRow = positions.indices.filter { positions[it].second == 6 }.map { frame[it] }
        assertEquals(middleRow.sorted(), middleRow)
        assertEquals(8, middleRow.distinct().size)
        assertEquals(255, middleRow.last())
    }

    @Test
    fun rowsSweepAdvancesExactlyOneRowPerFrame() {
        val period = 1.seconds / 120 // 8.33 ms: whole-millisecond arithmetic would repeat a row here
        // late frames too: an 8 ms approximation stays in step for the first two dozen frames, then drifts
        listOf(0, 1, 2, 12, 13, 14, 24, 119, 1199).forEach { frameIndex ->
            val frame = rowsSweep(period).at(period * frameIndex)
            val litRows =
                positions.indices
                    .filter { frame[it] > 0 }
                    .map { positions[it].second }
                    .distinct()
            assertEquals(listOf(frameIndex % 13), litRows, "frame $frameIndex")
        }
    }

    @Test
    fun singleLightsExactlyThatLed() {
        val frame = single(6, 6).at()
        assertEquals(1, frame.litCount())
        assertEquals(6 to 6, positions[frame.indexOfFirst { it > 0 }])
    }

    @Test
    fun flashTogglesEveryHalfCycle() {
        val flash10 = flash(10)
        assertEquals(137, flash10.at(0.milliseconds).litCount())
        assertEquals(137, flash10.at(49.milliseconds).litCount())
        assertEquals(0, flash10.at(50.milliseconds).litCount())
        assertEquals(137, flash10.at(100.milliseconds).litCount())
    }

    @Test
    fun cornerProbeLightsBothEndsOfEveryRow() {
        val frame = cornerProbe.at()
        assertEquals(26, frame.litCount())
        assertEquals(255, frame[positions.indexOf(4 to 0)])
        assertEquals(255, frame[positions.indexOf(8 to 0)])
        assertEquals(0, frame[positions.indexOf(5 to 0)])
    }
}
