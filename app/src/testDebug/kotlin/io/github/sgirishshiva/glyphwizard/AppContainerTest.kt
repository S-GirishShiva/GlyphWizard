package io.github.sgirishshiva.glyphwizard

import androidx.test.core.app.ApplicationProvider
import io.github.sgirishshiva.glyphwizard.glyph.MirroringGlyphDisplay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerTest {
    @Test
    fun debugBuildsMirrorTheGlyphDisplay() {
        val display = AppContainer(ApplicationProvider.getApplicationContext()).glyphDisplay
        assertIs<MirroringGlyphDisplay>(display)
    }
}
