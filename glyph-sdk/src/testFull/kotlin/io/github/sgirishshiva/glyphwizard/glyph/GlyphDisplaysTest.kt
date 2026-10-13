package io.github.sgirishshiva.glyphwizard.glyph

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlyphDisplaysTest {
    @Test
    fun fullFactoryBuildsTheNothingDisplay() {
        val display = GlyphDisplays.create(RuntimeEnvironment.getApplication(), MatrixFrameEncoder.forPhone4aPro())
        assertIs<NothingGlyphDisplay>(display)
    }
}
