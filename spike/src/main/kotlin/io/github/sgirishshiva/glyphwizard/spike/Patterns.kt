// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One frame of brightness (0..255 per LED, LED order) for a frame scheduled at [frameTime] after the start. */
typealias Pattern = (profile: DeviceProfile, frameTime: Duration) -> IntArray

private const val FULL = 255
private const val RAMP_BANDS = 8

private fun DeviceProfile.frame(brightnessAt: (x: Int, y: Int) -> Int): IntArray =
    ledPositions().map { (x, y) -> brightnessAt(x, y) }.toIntArray()

val checker: Pattern = { profile, _ -> profile.frame { x, y -> if ((x + y) % 2 == 0) FULL else 0 } }

fun all(level: Int): Pattern = { profile, _ -> IntArray(profile.ledCount) { level } }

/** Eight brightness bands from left to right. */
val ramp: Pattern = { profile, _ ->
    profile.frame { x, _ -> (x * RAMP_BANDS / profile.gridSize + 1) * FULL / RAMP_BANDS }
}

/** Lights one row per frame, moving down; counting rows in slow-motion footage gives the panel's real frame rate. */
fun rowsSweep(framePeriod: Duration): Pattern =
    { profile, frameTime ->
        val row = (frameTime / framePeriod).toLong() % profile.gridSize
        profile.frame { _, y -> if (y.toLong() == row) FULL else 0 }
    }

fun single(
    x: Int,
    y: Int,
): Pattern = { profile, _ -> profile.frame { ledX, ledY -> if (ledX == x && ledY == y) FULL else 0 } }

/** Whole-panel on/off at [hz] full cycles per second, starting on. */
fun flash(hz: Int): Pattern =
    { profile, frameTime ->
        val halfCycle = frameTime.inWholeNanoseconds * hz * 2 / 1.seconds.inWholeNanoseconds
        IntArray(profile.ledCount) { if (halfCycle % 2 == 0L) FULL else 0 }
    }

/** The first and last LED of every row: shows whether the device's frame layout matches ours. */
val cornerProbe: Pattern = { profile, _ ->
    val ends =
        profile
            .ledPositions()
            .groupBy { it.second }
            .values
            .flatMap { row ->
                listOf(row.first(), row.last())
            }.toSet()
    profile.frame { x, y -> if ((x to y) in ends) FULL else 0 }
}
