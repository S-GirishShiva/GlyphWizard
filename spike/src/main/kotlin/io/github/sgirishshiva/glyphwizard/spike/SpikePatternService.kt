// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplay
import io.github.sgirishshiva.glyphwizard.glyph.GlyphDisplays
import io.github.sgirishshiva.glyphwizard.glyph.GlyphResult
import io.github.sgirishshiva.glyphwizard.glyph.MatrixFrameEncoder
import io.github.sgirishshiva.glyphwizard.glyph.MirroringGlyphDisplay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service that owns the playing session and the adb command receiver, so measurements keep working with
 * the screen off or the app in the background.
 */
class SpikePatternService : LifecycleService() {
    private class Playing(
        val layout: MatrixFrameEncoder.Layout,
        val maxValue: Int,
        val display: GlyphDisplay,
        val session: GlyphSession,
    )

    private val container by lazy { (application as SpikeApplication).container }
    private val commands = Channel<SpikeCommand>(Channel.UNLIMITED)

    // written by the session's frames, read by DUMP on the main thread
    private val lastGrid = AtomicReference<String?>(null)
    private var playing: Playing? = null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = submit(intent)
        }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIFICATION_ID,
            spikeNotification(this, "Playing glyph patterns"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        // only callers holding DUMP, in practice the adb shell, can send commands
        registerReceiver(receiver, SpikeCommand.intentFilter(), Manifest.permission.DUMP, null, RECEIVER_EXPORTED)
        lifecycleScope.launch { for (command in commands) handle(command) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let(::submit)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        commands.close()
        playing?.session?.stop()
        playing = null
        super.onDestroy()
    }

    private fun submit(intent: Intent) {
        val command = SpikeCommand.from(intent.action, intent.extras)
        if (command == null) {
            Log.w(STATUS, "ignored ${intent.action} ${intent.extras}")
        } else {
            commands.trySend(command)
        }
    }

    private suspend fun handle(command: SpikeCommand) {
        when (command) {
            is SpikeCommand.Show -> {
                show(command)
            }

            is SpikeCommand.Hold -> {
                hold(command)
            }

            is SpikeCommand.AutoTimeout -> {
                val result = withContext(container.dispatcher) { playing?.display?.setAutoTimeout(command.enabled) }
                Log.i(STATUS, "autoTimeout(${command.enabled}) -> ${result ?: "nothing playing"}")
            }

            SpikeCommand.Dump -> {
                Log.i(MIRROR, lastGrid.get() ?: "no frame yet")
                val session = playing?.session
                Log.i(STATUS, "lastResult=${session?.lastResult?.value} achievedFps=${session?.achievedFps?.value}")
            }

            SpikeCommand.Stop -> {
                playing?.session?.stop()?.join()
                playing = null
                stopSelf()
            }

            SpikeCommand.Short -> {
                startForegroundService(Intent(this, SpikeShortService::class.java))
            }
        }
    }

    private suspend fun show(command: SpikeCommand.Show) {
        val active = playingFor(command) ?: return
        active.session.start(patternFor(command), command.fps)
        Log.i(STATUS, "showing $command")
    }

    private suspend fun hold(command: SpikeCommand.Hold) {
        val active = playingFor(command.show) ?: return
        active.session.hold(patternFor(command.show)).join()
        Log.i(STATUS, "holding ${command.show} -> ${active.session.lastResult.value}")
    }

    /** The playing session if its display matches [command]'s layout and range, otherwise a newly connected one. */
    private suspend fun playingFor(command: SpikeCommand.Show): Playing? {
        val current = playing
        if (current != null && current.layout == command.layout && current.maxValue == command.maxValue) return current
        // the old session's closeAppMatrix must finish before the new display connects
        current?.session?.stop()?.join()
        playing = connect(command)
        return playing
    }

    private suspend fun connect(command: SpikeCommand.Show): Playing? {
        val encoder = MatrixFrameEncoder(container.profile, command.layout, command.maxValue)
        val display = MirroringGlyphDisplay(GlyphDisplays.create(this, encoder), container.profile, lastGrid::set)
        return when (val result = display.connect()) {
            is GlyphResult.Ok -> {
                val session =
                    GlyphSession(
                        display,
                        container.profile,
                        container.appScope,
                        container.dispatcher,
                        container.timeSource,
                    )
                Playing(command.layout, command.maxValue, display, session)
            }

            is GlyphResult.Err -> {
                Log.w(STATUS, "connect failed: $result")
                null
            }
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val STATUS = "SpikeStatus"
        const val MIRROR = "GlyphMirror"
    }
}
