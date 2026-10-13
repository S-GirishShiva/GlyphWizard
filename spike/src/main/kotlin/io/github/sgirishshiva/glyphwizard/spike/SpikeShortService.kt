// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Probes the shortService type: runs until the system's onTimeout, logging a heartbeat (spec §10, D33). */
class SpikeShortService : LifecycleService() {
    private var heartbeat: Job? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(
            NOTIFICATION_ID,
            spikeNotification(this, "shortService probe"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
        )
        Log.i(TAG, "started ${SystemClock.elapsedRealtime()}")
        if (heartbeat == null) {
            heartbeat =
                lifecycleScope.launch {
                    while (isActive) {
                        Log.i(TAG, "alive ${SystemClock.elapsedRealtime()}")
                        delay(10.seconds)
                    }
                }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        Log.i(TAG, "onTimeout ${SystemClock.elapsedRealtime()} type=$fgsType")
        stopSelf(startId)
    }

    private companion object {
        const val NOTIFICATION_ID = 2
        const val TAG = "SpikeShort"
    }
}
