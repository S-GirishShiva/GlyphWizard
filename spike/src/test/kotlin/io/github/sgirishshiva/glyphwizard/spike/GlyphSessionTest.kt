package io.github.sgirishshiva.glyphwizard.spike

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplay
import io.github.sgirishshiva.glyphwizard.glyph.GlyphError
import io.github.sgirishshiva.glyphwizard.glyph.GlyphResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GlyphSessionTest {
    /** Records "show:<first value>" and "release" in call order. */
    private class RecordingDisplay : GlyphDisplay {
        val events = mutableListOf<String>()
        var answer: GlyphResult<Unit> = GlyphResult.Ok(Unit)

        override suspend fun connect(): GlyphResult<Unit> = GlyphResult.Ok(Unit)

        override fun show(brightness: IntArray): GlyphResult<Unit> {
            events += "show:${brightness[0]}"
            return answer
        }

        override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> = GlyphResult.Ok(Unit)

        override fun release() {
            events += "release"
        }

        val shows get() = events.count { it.startsWith("show") }
    }

    private val display = RecordingDisplay()

    private fun TestScope.session() =
        GlyphSession(
            display,
            DeviceProfile.Phone4aPro,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            testScheduler.timeSource,
        )

    @Test
    fun playsAtTheRequestedRate() =
        runTest {
            val session = session()
            session.start(all(1), 50)
            testScheduler.advanceTimeBy(1.seconds)
            testScheduler.runCurrent()
            assertTrue(display.shows in 49..51, "shows=${display.shows}")
            assertTrue(abs(session.achievedFps.value - 50) <= 1, "fps=${session.achievedFps.value}")
        }

    @Test
    fun aNonDividingPeriodDoesNotDrift() =
        runTest {
            session().start(all(1), 120)
            testScheduler.advanceTimeBy(10.seconds)
            // 8.33 ms frames: rounding to 8 ms would give about 1250
            assertTrue(display.shows in 1199..1201, "shows=${display.shows}")
        }

    @Test
    fun stopReleasesOnceAfterTheLastFrameAndPlaysNoMore() =
        runTest {
            val session = session()
            session.start(all(1), 50)
            testScheduler.advanceTimeBy(100.milliseconds)
            val stopping = session.stop()
            testScheduler.runCurrent()
            assertTrue(stopping.isCompleted)
            assertEquals("release", display.events.last())
            assertEquals(1, display.events.count { it == "release" })
            val showsAtStop = display.shows
            testScheduler.advanceTimeBy(1.seconds)
            assertEquals(showsAtStop, display.shows)
        }

    @Test
    fun holdSendsOneFrameThenNothingAndKeepsTheDisplay() =
        runTest {
            val session = session()
            session.start(all(1), 50)
            testScheduler.advanceTimeBy(100.milliseconds)
            val holding = session.hold(all(7))
            testScheduler.runCurrent()
            assertTrue(holding.isCompleted)
            assertEquals("show:7", display.events.last())
            val eventsAtHold = display.events.size
            testScheduler.advanceTimeBy(5.seconds)
            assertEquals(eventsAtHold, display.events.size)
            assertTrue("release" !in display.events)
        }

    @Test
    fun aSecondStartReplacesTheFirstLoop() =
        runTest {
            val session = session()
            session.start(all(1), 10)
            testScheduler.runCurrent()
            session.start(all(2), 10)
            testScheduler.advanceTimeBy(1.seconds)
            val afterSwitch = display.events.drop(1)
            assertTrue(afterSwitch.all { it == "show:2" }, "events=${display.events}")
            assertTrue(afterSwitch.size in 9..11, "one loop at 10 fps, got ${afterSwitch.size}")
        }

    @Test
    fun theLatestFrameResultIsExposed() =
        runTest {
            val session = session()
            display.answer = GlyphResult.Err(GlyphError.Rejected("busy"))
            session.start(all(1), 10)
            testScheduler.runCurrent()
            assertEquals(GlyphResult.Err(GlyphError.Rejected("busy")), session.lastResult.value)
        }

    @Test
    fun anOutOfRangeFrameRateIsRejected() =
        runTest {
            val session = session()
            assertFailsWith<IllegalArgumentException> { session.start(all(1), 0) }
            assertFailsWith<IllegalArgumentException> { session.start(all(1), 241) }
        }
}
