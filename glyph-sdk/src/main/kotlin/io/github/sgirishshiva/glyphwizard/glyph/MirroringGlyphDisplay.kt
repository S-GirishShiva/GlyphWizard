package io.github.sgirishshiva.glyphwizard.glyph

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.core.TextGrid

/** Debug decorator: forwards every call and also hands each frame, as a text grid, to [sink]. */
class MirroringGlyphDisplay(
    private val delegate: GlyphDisplay,
    private val profile: DeviceProfile,
    private val sink: (String) -> Unit,
) : GlyphDisplay by delegate {
    override fun show(brightness: IntArray): GlyphResult<Unit> {
        sink(TextGrid.encode(profile, brightness))
        return delegate.show(brightness)
    }
}
