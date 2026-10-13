package io.github.sgirishshiva.glyphwizard.spike

import android.os.Bundle
import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.MatrixFrameEncoder.Layout
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.Companion.ACTION_DUMP
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.Companion.ACTION_SHORT
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.Companion.ACTION_SHOW
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.Companion.ACTION_STOP
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.Companion.ACTION_TIMEOUT
import io.github.sgirishshiva.glyphwizard.spike.SpikeCommand.PatternName
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpikeCommandTest {
    private fun extras(vararg pairs: Pair<String, Any>) =
        Bundle().apply {
            pairs.forEach { (key, value) ->
                when (value) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }

    @Test
    fun showUsesTheDocumentedDefaults() {
        assertEquals(
            SpikeCommand.Show(PatternName.Checker, 30, Layout.FullSquare, 255, 255, 10, 6, 6),
            SpikeCommand.from(ACTION_SHOW, extras("pattern" to "checker")),
        )
    }

    @Test
    fun everyShowExtraOverridesItsDefault() {
        val bundle =
            extras(
                "pattern" to "single",
                "fps" to 120,
                "layout" to "leds",
                "max" to 2047,
                "level" to 7,
                "hz" to 15,
                "x" to 4,
                "y" to 0,
            )
        assertEquals(
            SpikeCommand.Show(PatternName.Single, 120, Layout.LedsOnly, 2047, 7, 15, 4, 0),
            SpikeCommand.from(ACTION_SHOW, bundle),
        )
    }

    @Test
    fun otherActionsParse() {
        assertEquals(SpikeCommand.AutoTimeout(false), SpikeCommand.from(ACTION_TIMEOUT, extras("enabled" to false)))
        assertEquals(SpikeCommand.AutoTimeout(true), SpikeCommand.from(ACTION_TIMEOUT, null))
        assertEquals(SpikeCommand.Dump, SpikeCommand.from(ACTION_DUMP, null))
        assertEquals(SpikeCommand.Stop, SpikeCommand.from(ACTION_STOP, null))
        assertEquals(SpikeCommand.Short, SpikeCommand.from(ACTION_SHORT, null))
    }

    @Test
    fun unknownOrOutOfRangeInputIsRejected() {
        assertNull(SpikeCommand.from("elsewhere.SHOW", extras("pattern" to "checker")))
        assertNull(SpikeCommand.from(null, null))
        assertNull(SpikeCommand.from(ACTION_SHOW, null))
        assertNull(SpikeCommand.from(ACTION_SHOW, extras("pattern" to "spiral")))
        assertNull(SpikeCommand.from(ACTION_SHOW, extras("pattern" to "checker", "layout" to "hex")))
        assertNull(SpikeCommand.from(ACTION_SHOW, extras("pattern" to "checker", "fps" to 0)))
        assertNull(SpikeCommand.from(ACTION_SHOW, extras("pattern" to "checker", "fps" to 241)))
        assertNull(SpikeCommand.from(ACTION_SHOW, extras("pattern" to "checker", "max" to 0)))
    }

    @Test
    fun theReceiverListensForEveryAction() {
        val filter = SpikeCommand.intentFilter()
        assertEquals(
            setOf(ACTION_SHOW, ACTION_TIMEOUT, ACTION_DUMP, ACTION_STOP, ACTION_SHORT),
            (0..<filter.countActions()).map(filter::getAction).toSet(),
        )
    }

    @Test
    fun everyPatternNameMapsToItsPattern() {
        val profile = DeviceProfile.Phone4aPro

        fun frameFor(name: PatternName) =
            patternFor(SpikeCommand.Show(name, 120, Layout.FullSquare, 255, 9, 10, 6, 6))(profile, 1.seconds / 120)
        assertEquals(checker(profile, 0.seconds).toList(), frameFor(PatternName.Checker).toList())
        assertEquals(all(9)(profile, 0.seconds).toList(), frameFor(PatternName.All).toList())
        assertEquals(ramp(profile, 0.seconds).toList(), frameFor(PatternName.Ramp).toList())
        assertEquals(
            rowsSweep(1.seconds / 120)(profile, 1.seconds / 120).toList(),
            frameFor(PatternName.RowsSweep).toList(),
        )
        assertEquals(single(6, 6)(profile, 0.seconds).toList(), frameFor(PatternName.Single).toList())
        assertEquals(flash(10)(profile, 1.seconds / 120).toList(), frameFor(PatternName.Flash).toList())
        assertEquals(cornerProbe(profile, 0.seconds).toList(), frameFor(PatternName.CornerProbe).toList())
    }
}
