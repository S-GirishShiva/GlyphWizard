package io.github.sgirishshiva.glyphwizard

import android.content.Context
import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplay
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplays
import io.github.sgirishshiva.glyphwizard.glyph.MatrixFrameEncoder

/** The app's composition root (D44): every dependency is constructed here and passed down by constructor. */
class AppContainer(
    context: Context,
) {
    val glyphDisplay: GlyphDisplay =
        wrapForBuild(
            GlyphDisplays.create(context.applicationContext, MatrixFrameEncoder.forPhone4aPro()),
            DeviceProfile.Phone4aPro,
        )
}
