package io.github.sgirishshiva.glyphwizard

import android.app.Application

class GlyphWizardApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
