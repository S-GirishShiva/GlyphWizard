package io.github.sgirishshiva.glyphwizard.glyph

import android.content.ComponentName
import android.content.Context
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import io.github.sgirishshiva.glyphwizard.core.DeviceProfile

/** What [NothingGlyphDisplay] needs from the Nothing glyph service; the seam that keeps SDK types out of tests. */
internal interface MatrixService {
    fun bind(
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
    )

    fun register(deviceId: String): Boolean

    fun setFrame(frame: IntArray): GlyphResult<Unit>

    fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit>

    fun close(): GlyphResult<Unit>

    fun unbind()
}

/** The only code that touches `com.nothing.*`: maps SDK calls and [GlyphException] onto [MatrixService]. */
internal class SdkMatrixService(
    context: Context,
) : MatrixService {
    private val manager = GlyphMatrixManager.getInstance(context.applicationContext)

    override fun bind(
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
    ) {
        manager.init(
            object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName) = onConnected()

                override fun onServiceDisconnected(name: ComponentName) = onDisconnected()
            },
        )
    }

    override fun register(deviceId: String): Boolean = sdkDevice(deviceId)?.let(manager::register) ?: false

    override fun setFrame(frame: IntArray): GlyphResult<Unit> = glyphCall { manager.setAppMatrixFrame(frame) }

    override fun setAutoTimeout(enabled: Boolean): GlyphResult<Unit> =
        glyphCall { manager.setGlyphMatrixTimeout(enabled) }

    override fun close(): GlyphResult<Unit> = glyphCall { manager.closeAppMatrix() }

    override fun unbind() = manager.unInit()

    private fun sdkDevice(id: String): String? =
        when (id) {
            DeviceProfile.Phone4aPro.id -> Glyph.DEVICE_25111p
            else -> null
        }

    private inline fun glyphCall(block: () -> Unit): GlyphResult<Unit> =
        try {
            block()
            GlyphResult.Ok(Unit)
        } catch (e: GlyphException) {
            GlyphResult.Err(GlyphError.Rejected(e.message ?: "glyph exception"))
        }
}
