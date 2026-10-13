package io.github.sgirishshiva.glyphwizard.glyph

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [GlyphDisplay] backed by the Nothing glyph service: bind, register, then push frames. */
internal class NothingGlyphDisplay(
    private val service: MatrixService,
    private val encoder: MatrixFrameEncoder,
    // [P]: M0 didn't time the bind itself; frames landed ~50 ms after register (hardware-spike-results.md)
    private val connectTimeout: Duration = 3.seconds,
) : GlyphDisplay {
    // Binder callbacks arrive on the main thread and calls on the caller's dispatcher, so session state changes under
    // one lock. Each connect() opens a numbered session: a late callback or timeout of an earlier session is ignored.
    private val lock = Any()
    private var lastSession = NO_SESSION
    private var boundSession = NO_SESSION
    private var connectedSession = NO_SESSION

    override suspend fun connect(): GlyphResult<Unit> {
        val session =
            synchronized(lock) {
                check(boundSession == NO_SESSION) { "connect() called twice without release()" }
                (++lastSession).also { boundSession = it }
            }
        return withTimeoutOrNull(connectTimeout) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { endSession(session) }
                service.bind(
                    onConnected = {
                        val result = register(session)
                        // a rebind calls onConnected again; the first answer is the only one
                        if (continuation.isActive) continuation.resume(result)
                    },
                    onDisconnected = { disconnected(session) },
                )
            }
        } ?: unavailable
    }

    override fun show(brightness: IntArray): GlyphResult<Unit> =
        if (isConnected()) service.setFrame(encoder.encode(brightness)) else unavailable

    override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> =
        if (isConnected()) service.setAutoTimeout(enabled) else unavailable

    override fun release() {
        val session = synchronized(lock) { boundSession }
        if (session == NO_SESSION) return
        // the session ends with unbind whatever close reports, so close's result is not surfaced
        try {
            service.close()
        } finally {
            endSession(session)
        }
    }

    private fun register(session: Long): GlyphResult<Unit> =
        synchronized(lock) {
            when {
                boundSession != session -> {
                    unavailable
                }

                service.register(encoder.profile.id) -> {
                    connectedSession = session
                    GlyphResult.Ok(Unit)
                }

                else -> {
                    endSessionLocked(session)
                    GlyphResult.Err(GlyphError.Rejected("register returned false"))
                }
            }
        }

    private fun disconnected(session: Long) =
        synchronized(lock) {
            if (connectedSession == session) connectedSession = NO_SESSION
        }

    private fun isConnected(): Boolean = synchronized(lock) { connectedSession != NO_SESSION }

    private fun endSession(session: Long) = synchronized(lock) { endSessionLocked(session) }

    private fun endSessionLocked(session: Long) {
        if (boundSession != session) return
        boundSession = NO_SESSION
        connectedSession = NO_SESSION
        service.unbind()
    }

    private companion object {
        const val NO_SESSION = 0L
        val unavailable = GlyphResult.Err(GlyphError.ServiceUnavailable)
    }
}
