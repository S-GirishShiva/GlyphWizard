// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** Measures listener binding and rebind timing (spec §10). Logs package names only, never notification content. */
class SpikeListener : NotificationListenerService() {
    override fun onListenerConnected() {
        Log.i(TAG, "connected ${SystemClock.elapsedRealtime()}")
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "disconnected ${SystemClock.elapsedRealtime()}")
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        Log.i(TAG, "posted ${SystemClock.elapsedRealtime()} from ${notification.packageName}")
    }

    private companion object {
        const val TAG = "SpikeListener"
    }
}
