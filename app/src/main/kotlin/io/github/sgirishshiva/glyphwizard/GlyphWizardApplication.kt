package io.github.sgirishshiva.glyphwizard

import android.app.Application

class GlyphWizardApplication : Application() {
    // Android attaches the Application before any component can read this, so lazy creation sees a ready context.
    val container: AppContainer by lazy { AppContainer(this) }
}
