// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Measures listener binding and rebind timing (spec §10, D28). Logs package names only, never notification content.
 * `REBIND` (adb, DUMP-guarded) unbinds the listener and then calls [requestRebind], timing the app-initiated path.
 */
class SpikeListener : NotificationListenerService() {
    private var rebindRequested = false

    private val rebindReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                rebindRequested = true
                Log.i(TAG, "requestUnbind ${SystemClock.elapsedRealtime()}")
                requestUnbind()
            }
        }

    override fun onListenerConnected() {
        Log.i(TAG, "connected ${SystemClock.elapsedRealtime()}")
        registerReceiver(
            rebindReceiver,
            IntentFilter(SpikeCommand.ACTION_REBIND),
            Manifest.permission.DUMP,
            null,
            RECEIVER_EXPORTED,
        )
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "disconnected ${SystemClock.elapsedRealtime()}")
        unregisterReceiver(rebindReceiver)
        if (rebindRequested) {
            rebindRequested = false
            Log.i(TAG, "requestRebind ${SystemClock.elapsedRealtime()}")
            requestRebind(ComponentName(this, SpikeListener::class.java))
        }
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        Log.i(TAG, "posted ${SystemClock.elapsedRealtime()} from ${notification.packageName}")
    }

    private companion object {
        const val TAG = "SpikeListener"
    }
}
