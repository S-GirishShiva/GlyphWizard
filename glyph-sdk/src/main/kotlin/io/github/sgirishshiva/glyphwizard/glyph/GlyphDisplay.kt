package io.github.sgirishshiva.glyphwizard.glyph

/** Why a glyph call failed. Local to `:glyph-sdk` until the D46 deep-dive picks the shared result type. */
sealed interface GlyphError {
    /** This build or device has no Glyph Matrix support (the sdkFree flavour). */
    data object Unsupported : GlyphError

    /** The Nothing glyph service isn't bound or answered too late. */
    data object ServiceUnavailable : GlyphError

    data class Rejected(
        val reason: String,
    ) : GlyphError
}

sealed interface GlyphResult<out T> {
    data class Ok<out T>(
        val value: T,
    ) : GlyphResult<T>

    data class Err(
        val error: GlyphError,
    ) : GlyphResult<Nothing>
}

/** The Glyph Matrix as the app sees it; the only door to the Nothing SDK. */
interface GlyphDisplay {
    suspend fun connect(): GlyphResult<Unit>

    /**
     * Shows one frame: one 0..255 value per LED, in [io.github.sgirishshiva.glyphwizard.core.DeviceProfile.ledPositions]
     * order. Makes a synchronous binder call, so call it off the main thread.
     */
    fun show(brightness: IntArray): GlyphResult<Unit>

    /** Nothing's app-matrix auto-clear; its exact behaviour is measured in M0 (hardware-spike-results.md). */
    fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit>

    fun release()
}
