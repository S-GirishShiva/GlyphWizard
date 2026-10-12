package io.github.sgirishshiva.glyphwizard

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlyphWizardApplicationTest {
    @Test
    fun containerIsCreatedOnceAndReused() {
        val app = ApplicationProvider.getApplicationContext<GlyphWizardApplication>()
        assertSame(app.container, app.container)
    }
}
