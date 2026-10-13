package io.github.sgirishshiva.glyphwizard.glyph

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoGlyphDisplayTest {
    private val unsupported = GlyphResult.Err(GlyphError.Unsupported)

    @Test
    fun factoryReturnsTheUnsupportedDisplay() {
        val display = GlyphDisplays.create(RuntimeEnvironment.getApplication(), MatrixFrameEncoder.forPhone4aPro())
        assertSame(NoGlyphDisplay, display)
    }

    @Test
    fun everyCallIsUnsupported() =
        runTest {
            assertEquals(unsupported, NoGlyphDisplay.connect())
            assertEquals(unsupported, NoGlyphDisplay.show(IntArray(137)))
            assertEquals(unsupported, NoGlyphDisplay.setAutoTimeout(true))
        }

    @Test
    fun releaseIsSafeToRepeat() {
        NoGlyphDisplay.release()
        NoGlyphDisplay.release()
    }
}
