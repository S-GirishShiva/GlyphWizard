package io.github.sgirishshiva.glyphwizard.glyph

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.core.TextGrid
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MirroringGlyphDisplayTest {
    private class RecordingDisplay : GlyphDisplay {
        val calls = mutableListOf<String>()
        var shown: IntArray? = null

        override suspend fun connect(): GlyphResult<Unit> = GlyphResult.Ok(Unit).also { calls += "connect" }

        override fun show(brightness: IntArray): GlyphResult<Unit> {
            calls += "show"
            shown = brightness
            return GlyphResult.Err(GlyphError.Rejected("from delegate"))
        }

        override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> =
            GlyphResult.Ok(Unit).also { calls += "setAutoTimeout($enabled)" }

        override fun release() {
            calls += "release"
        }
    }

    private val profile = DeviceProfile.Phone4aPro
    private val delegate = RecordingDisplay()
    private val sunk = mutableListOf<String>()
    private val display = MirroringGlyphDisplay(delegate, profile) { sunk += it }

    @Test
    fun showMirrorsTheFrameAsATextGridAndForwardsIt() {
        val frame = IntArray(profile.ledCount) { it % 256 }
        val result = display.show(frame)
        assertEquals(listOf(TextGrid.encode(profile, frame)), sunk)
        assertContentEquals(frame, delegate.shown)
        assertEquals(GlyphResult.Err(GlyphError.Rejected("from delegate")), result)
    }

    @Test
    fun otherCallsAreForwardedWithoutMirroring() =
        runTest {
            assertEquals(GlyphResult.Ok(Unit), display.connect())
            assertEquals(GlyphResult.Ok(Unit), display.setAutoTimeout(true))
            display.release()
            assertEquals(listOf("connect", "setAutoTimeout(true)", "release"), delegate.calls)
            assertEquals(emptyList(), sunk)
        }
}
