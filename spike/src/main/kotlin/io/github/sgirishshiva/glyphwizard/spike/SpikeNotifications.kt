// Throwaway M0 spike (spec §8). Deleted at end of M1.
package io.github.sgirishshiva.glyphwizard.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

private const val CHANNEL_ID = "spike"

/** The ongoing notification every foreground service must show; creating the channel is idempotent. */
internal fun spikeNotification(
    context: Context,
    text: String,
): Notification {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "GlyphWizard spike", NotificationManager.IMPORTANCE_LOW),
    )
    return Notification
        .Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("GlyphWizard spike")
        .setContentText(text)
        .setOngoing(true)
        .build()
}
