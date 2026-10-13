package io.github.sgirishshiva.glyphwizard

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplay

/**
 * Release builds use the display as is: no mirror. Shares the debug variant's signature, whose mirror needs the
 * profile, hence the unused parameter.
 */
@Suppress("UnusedParameter")
internal fun wrapForBuild(
    display: GlyphDisplay,
    profile: DeviceProfile,
): GlyphDisplay = display
