// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplay
import io.github.sgirishshiva.glyphwizard.glyph.GlyphResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/** Plays a [Pattern] on a [GlyphDisplay] at a fixed frame rate. */
class GlyphSession(
    private val display: GlyphDisplay,
    private val profile: DeviceProfile,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    val achievedFps: StateFlow<Double>
        field = MutableStateFlow(0.0)
    val lastResult: StateFlow<GlyphResult<Unit>?>
        field = MutableStateFlow<GlyphResult<Unit>?>(null)

    // touched only from the main thread (service callbacks), so it needs no synchronisation
    private var loop: Job? = null

    fun start(
        pattern: Pattern,
        fps: Int,
    ) {
        require(fps in 1..MAX_FPS) { "fps must be in 1..$MAX_FPS, got $fps" }
        loop?.cancel()
        loop =
            scope.launch(dispatcher) {
                val period = 1.seconds / fps
                val start = timeSource.markNow()
                var frames = 0L
                while (isActive) {
                    val frameTime = period * frames.toDouble()
                    // absolute deadlines: a late frame doesn't push every later one back
                    delay((start + frameTime) - timeSource.markNow())
                    lastResult.value = display.show(pattern(profile, frameTime))
                    frames++
                    val elapsed = start.elapsedNow()
                    if (elapsed.isPositive()) achievedFps.value = frames / elapsed.toDouble(DurationUnit.SECONDS)
                }
            }
    }

    /** Stops playing and releases the display once any in-flight frame has finished. */
    fun stop(): Job {
        val running = loop
        loop = null
        // show() is a blocking binder call, so release only after it returns
        return scope.launch(dispatcher) {
            running?.cancelAndJoin()
            display.release()
        }
    }

    private companion object {
        const val MAX_FPS = 240
    }
}
