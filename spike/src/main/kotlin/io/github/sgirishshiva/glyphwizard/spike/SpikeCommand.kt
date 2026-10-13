// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.content.IntentFilter
import android.os.Bundle
import io.github.sgirishshiva.glyphwizard.glyph.MatrixFrameEncoder
import kotlin.time.Duration.Companion.seconds

/** Commands for the spike, parsed the same way from Activity intents and adb broadcasts. */
sealed interface SpikeCommand {
    data class Show(
        val pattern: PatternName,
        val fps: Int,
        val layout: MatrixFrameEncoder.Layout,
        val maxValue: Int,
        val level: Int,
        val hz: Int,
        val x: Int,
        val y: Int,
    ) : SpikeCommand

    /** Shows one frame of [show]'s pattern and then stops sending, to test how a held frame behaves. */
    data class Hold(
        val show: Show,
    ) : SpikeCommand

    data class AutoTimeout(
        val enabled: Boolean,
    ) : SpikeCommand

    data object Dump : SpikeCommand

    data object Stop : SpikeCommand

    data object Short : SpikeCommand

    enum class PatternName(
        val key: String,
    ) {
        Checker("checker"),
        All("all"),
        Ramp("ramp"),
        RowsSweep("rows"),
        Single("single"),
        Flash("flash"),
        CornerProbe("corners"),
    }

    companion object {
        const val PACKAGE = "io.github.sgirishshiva.glyphwizard.spike"
        const val ACTION_SHOW = "$PACKAGE.SHOW"
        const val ACTION_TIMEOUT = "$PACKAGE.TIMEOUT"
        const val ACTION_DUMP = "$PACKAGE.DUMP"
        const val ACTION_STOP = "$PACKAGE.STOP"
        const val ACTION_SHORT = "$PACKAGE.SHORT"
        const val ACTION_HOLD = "$PACKAGE.HOLD"

        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_FPS = "fps"
        const val EXTRA_LAYOUT = "layout"
        const val EXTRA_MAX = "max"
        const val LAYOUT_LEDS = "leds"
        const val LAYOUT_SQUARE = "square"
        const val DEFAULT_MAX = 255

        /** The 11-bit maximum some Glyph APIs use; the hardware spike checks whether the matrix accepts it. */
        const val PROBE_MAX = 2047

        private const val MAX_FPS = 240
        private const val DEFAULT_FPS = 30
        private const val DEFAULT_LEVEL = 255
        private const val DEFAULT_HZ = 10

        // the centre LED of the 13 × 13 matrix
        private const val DEFAULT_X = 6
        private const val DEFAULT_Y = 6
        private val layouts =
            mapOf(
                "leds" to MatrixFrameEncoder.Layout.LedsOnly,
                "square" to MatrixFrameEncoder.Layout.FullSquare,
            )

        fun intentFilter(): IntentFilter =
            IntentFilter().apply {
                listOf(
                    ACTION_SHOW,
                    ACTION_HOLD,
                    ACTION_TIMEOUT,
                    ACTION_DUMP,
                    ACTION_STOP,
                    ACTION_SHORT,
                ).forEach(::addAction)
            }

        /** The command for [action] and its [extras], or null if anything is unknown or out of range. */
        fun from(
            action: String?,
            extras: Bundle?,
        ): SpikeCommand? {
            val args = extras ?: Bundle.EMPTY
            return when (action) {
                ACTION_SHOW -> show(args)
                ACTION_HOLD -> show(args)?.let(::Hold)
                ACTION_TIMEOUT -> AutoTimeout(args.getBoolean("enabled", true))
                ACTION_DUMP -> Dump
                ACTION_STOP -> Stop
                ACTION_SHORT -> Short
                else -> null
            }
        }

        private fun show(args: Bundle): Show? {
            val pattern = PatternName.entries.firstOrNull { it.key == args.getString(EXTRA_PATTERN) }
            val layout = layouts[args.getString(EXTRA_LAYOUT, LAYOUT_SQUARE)]
            val fps = args.getInt(EXTRA_FPS, DEFAULT_FPS)
            val maxValue = args.getInt(EXTRA_MAX, DEFAULT_MAX)
            val inRange = fps in 1..MAX_FPS && maxValue >= 1
            if (pattern == null || layout == null || !inRange) return null
            return Show(
                pattern = pattern,
                fps = fps,
                layout = layout,
                maxValue = maxValue,
                level = args.getInt("level", DEFAULT_LEVEL),
                hz = args.getInt("hz", DEFAULT_HZ),
                x = args.getInt("x", DEFAULT_X),
                y = args.getInt("y", DEFAULT_Y),
            )
        }
    }
}

fun patternFor(show: SpikeCommand.Show): Pattern =
    when (show.pattern) {
        SpikeCommand.PatternName.Checker -> checker
        SpikeCommand.PatternName.All -> all(show.level)
        SpikeCommand.PatternName.Ramp -> ramp
        SpikeCommand.PatternName.RowsSweep -> rowsSweep(1.seconds / show.fps)
        SpikeCommand.PatternName.Single -> single(show.x, show.y)
        SpikeCommand.PatternName.Flash -> flash(show.hz)
        SpikeCommand.PatternName.CornerProbe -> cornerProbe
    }
