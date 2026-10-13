package io.github.sgirishshiva.glyphwizard.glyph

import android.content.Context

/** Full builds drive the real Glyph Matrix through the Nothing SDK. */
object GlyphDisplays {
    fun create(
        context: Context,
        encoder: MatrixFrameEncoder,
    ): GlyphDisplay = NothingGlyphDisplay(SdkMatrixService(context), encoder)
}
