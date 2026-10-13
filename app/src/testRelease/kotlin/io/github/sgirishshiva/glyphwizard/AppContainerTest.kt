package io.github.sgirishshiva.glyphwizard

import androidx.test.core.app.ApplicationProvider
import io.github.sgirishshiva.glyphwizard.glyph.MirroringGlyphDisplay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerTest {
    @Test
    fun releaseBuildsDoNotMirror() {
        val display = AppContainer(ApplicationProvider.getApplicationContext()).glyphDisplay
        assertFalse(display is MirroringGlyphDisplay)
    }
}
