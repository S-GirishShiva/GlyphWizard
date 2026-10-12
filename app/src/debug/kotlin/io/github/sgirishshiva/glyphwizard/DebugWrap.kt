package io.github.sgirishshiva.glyphwizard

import android.util.Log
import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplay
import io.github.sgirishshiva.glyphwizard.glyph.MirroringGlyphDisplay

/** Debug builds mirror every frame to logcat (tag `GlyphMirror`) so frames can be checked without seeing the back. */
internal fun wrapForBuild(
    display: GlyphDisplay,
    profile: DeviceProfile,
): GlyphDisplay = MirroringGlyphDisplay(display, profile) { Log.i("GlyphMirror", it) }
