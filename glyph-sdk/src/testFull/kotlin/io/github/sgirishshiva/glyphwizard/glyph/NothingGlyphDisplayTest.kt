package io.github.sgirishshiva.glyphwizard.glyph

import io.github.sgirishshiva.glyphwizard.core.DeviceProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class NothingGlyphDisplayTest {
    /** Records every call; tests fire the binder callbacks themselves. */
    private class FakeMatrixService : MatrixService {
        var registerAnswer = true
        var frameAnswer: GlyphResult<Unit> = GlyphResult.Ok(Unit)
        var closeFailure: RuntimeException? = null
        val registered = mutableListOf<String>()
        val frames = mutableListOf<IntArray>()
        val autoTimeouts = mutableListOf<Boolean>()
        var binds = 0
        var closes = 0
        var unbinds = 0
        private var onConnected: () -> Unit = {}
        private var onDisconnected: () -> Unit = {}

        fun serviceConnected() = onConnected()

        fun serviceDisconnected() = onDisconnected()

        override fun bind(
            onConnected: () -> Unit,
            onDisconnected: () -> Unit,
        ) {
            binds++
            this.onConnected = onConnected
            this.onDisconnected = onDisconnected
        }

        override fun register(deviceId: String): Boolean = registerAnswer.also { registered += deviceId }

        override fun setFrame(frame: IntArray): GlyphResult<Unit> = frameAnswer.also { frames += frame }

        override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> {
            autoTimeouts += enabled
            return GlyphResult.Ok(Unit)
        }

        override fun close(): GlyphResult<Unit> {
            closes++
            closeFailure?.let { throw it }
            return GlyphResult.Ok(Unit)
        }

        override fun unbind() {
            unbinds++
        }
    }

    private val service = FakeMatrixService()
    private val encoder = MatrixFrameEncoder.forPhone4aPro()
    private val display = NothingGlyphDisplay(service, encoder)
    private val unavailable = GlyphResult.Err(GlyphError.ServiceUnavailable)

    /** Starts connect(), lets it bind, fires onConnected, and returns connect's result. */
    private suspend fun TestScope.connectWithService(): GlyphResult<Unit> {
        val result = async { display.connect() }
        testScheduler.runCurrent()
        service.serviceConnected()
        return result.await()
    }

    @Test
    fun noAnswerFromTheServiceTimesOutAsUnavailableAndUnbinds() =
        runTest {
            val start = testScheduler.timeSource.markNow()
            assertEquals(unavailable, display.connect())
            assertEquals(3.seconds, start.elapsedNow())
            assertEquals(1, service.unbinds)
        }

    @Test
    fun connectRegistersThisDeviceAndShowSendsTheEncodedFrame() =
        runTest {
            assertEquals(GlyphResult.Ok(Unit), connectWithService())
            assertEquals(listOf(DeviceProfile.Phone4aPro.id), service.registered)

            val brightness = IntArray(DeviceProfile.Phone4aPro.ledCount) { 200 }
            assertEquals(GlyphResult.Ok(Unit), display.show(brightness))
            assertContentEquals(encoder.encode(brightness), service.frames.single())
        }

    @Test
    fun rejectedRegistrationReportsAndUnbinds() =
        runTest {
            service.registerAnswer = false
            assertEquals(GlyphResult.Err(GlyphError.Rejected("register returned false")), connectWithService())
            assertEquals(1, service.unbinds)
            assertEquals(unavailable, display.show(IntArray(137)))
        }

    @Test
    fun frameErrorsArePassedThroughUnchanged() =
        runTest {
            connectWithService()
            service.frameAnswer = GlyphResult.Err(GlyphError.Rejected("x"))
            assertEquals(GlyphResult.Err(GlyphError.Rejected("x")), display.show(IntArray(137)))
        }

    @Test
    fun callsBeforeConnectAreUnavailableAndForwardedAfter() =
        runTest {
            assertEquals(unavailable, display.show(IntArray(137)))
            assertEquals(unavailable, display.setAutoTimeout(true))
            assertEquals(emptyList(), service.autoTimeouts)

            connectWithService()
            assertEquals(GlyphResult.Ok(Unit), display.setAutoTimeout(true))
            assertEquals(listOf(true), service.autoTimeouts)
        }

    @Test
    fun aDisconnectMakesTheDisplayUnavailable() =
        runTest {
            connectWithService()
            service.serviceDisconnected()
            assertEquals(unavailable, display.show(IntArray(137)))
        }

    @Test
    fun aRebindRegistersAgainWithoutResumingConnectTwice() =
        runTest {
            assertEquals(GlyphResult.Ok(Unit), connectWithService())
            service.serviceDisconnected()
            service.serviceConnected()
            assertEquals(2, service.registered.size)
            assertEquals(GlyphResult.Ok(Unit), display.show(IntArray(137)))
        }

    @Test
    fun releaseTwiceClosesAndUnbindsOnce() =
        runTest {
            connectWithService()
            display.release()
            display.release()
            assertEquals(1, service.closes)
            assertEquals(1, service.unbinds)
        }

    @Test
    fun releaseBeforeConnectDoesNothing() {
        display.release()
        assertEquals(0, service.closes)
        assertEquals(0, service.unbinds)
    }

    @Test
    fun theDisplayCanReconnectAfterRelease() =
        runTest {
            connectWithService()
            display.release()
            assertEquals(GlyphResult.Ok(Unit), connectWithService())
            display.release()
            assertEquals(2, service.binds)
            assertEquals(2, service.unbinds)
        }

    @Test
    fun aLateCallbackAfterReleaseRegistersNothing() =
        runTest {
            connectWithService()
            display.release()
            service.serviceConnected()
            assertEquals(1, service.registered.size)
            assertEquals(unavailable, display.show(IntArray(137)))
        }

    @Test
    fun aFailingCloseStillUnbindsAndPropagates() =
        runTest {
            connectWithService()
            service.closeFailure = IllegalStateException("close failed")
            val error = assertFailsWith<IllegalStateException> { display.release() }
            assertEquals("close failed", error.message)
            assertEquals(1, service.unbinds)
        }

    @Test
    fun connectingTwiceWithoutReleaseIsAProgrammingError() =
        runTest {
            connectWithService()
            assertFailsWith<IllegalStateException> { display.connect() }
        }
}
