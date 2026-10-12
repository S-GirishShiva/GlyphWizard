package io.github.sgirishshiva.glyphwizard.glyph

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [GlyphDisplay] backed by the Nothing glyph service: bind, register, then push frames. */
internal class NothingGlyphDisplay(
    private val service: MatrixService,
    private val encoder: MatrixFrameEncoder,
    // provisional until the M0 hardware spike measures binding time
    private val connectTimeout: Duration = 3.seconds,
) : GlyphDisplay {
    // written by binder callbacks on the main thread, read from the caller's dispatcher
    private val bound = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)

    override suspend fun connect(): GlyphResult<Unit> {
        check(bound.compareAndSet(false, true)) { "connect() called twice without release()" }
        return withTimeoutOrNull(connectTimeout) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { unbindOnce() }
                service.bind(
                    onConnected = {
                        val registered = bound.get() && service.register(encoder.profile.id)
                        connected.set(registered)
                        if (!registered) unbindOnce()
                        // a rebind calls onConnected again; the first answer is the only one
                        if (continuation.isActive) {
                            continuation.resume(
                                if (registered) {
                                    GlyphResult.Ok(Unit)
                                } else {
                                    GlyphResult.Err(GlyphError.Rejected("register returned false"))
                                },
                            )
                        }
                    },
                    onDisconnected = { connected.set(false) },
                )
            }
        } ?: GlyphResult.Err(GlyphError.ServiceUnavailable)
    }

    override fun show(brightness: IntArray): GlyphResult<Unit> =
        if (connected.get()) service.setFrame(encoder.encode(brightness)) else unavailable

    override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> =
        if (connected.get()) service.setAutoTimeout(enabled) else unavailable

    override fun release() {
        if (!bound.get()) return
        // the session ends with unbind whatever close reports, so close's result is not surfaced
        try {
            service.close()
        } finally {
            unbindOnce()
        }
    }

    private fun unbindOnce() {
        connected.set(false)
        if (bound.compareAndSet(true, false)) service.unbind()
    }

    private companion object {
        val unavailable = GlyphResult.Err(GlyphError.ServiceUnavailable)
    }
}
