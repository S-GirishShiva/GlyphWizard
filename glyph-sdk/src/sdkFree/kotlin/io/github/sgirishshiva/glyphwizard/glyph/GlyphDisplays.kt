package io.github.sgirishshiva.glyphwizard.glyph

import android.content.Context

/** sdkFree builds have no Nothing SDK, so every display is unsupported. */
object GlyphDisplays {
    // the signature is shared with the full flavour's factory, which needs both parameters
    @Suppress("UnusedParameter")
    fun create(
        context: Context,
        encoder: MatrixFrameEncoder,
    ): GlyphDisplay = NoGlyphDisplay
}

internal data object NoGlyphDisplay : GlyphDisplay {
    private val unsupported = GlyphResult.Err(GlyphError.Unsupported)

    override suspend fun connect(): GlyphResult<Unit> = unsupported

    override fun show(brightness: IntArray): GlyphResult<Unit> = unsupported

    override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> = unsupported

    override fun release() = Unit
}
