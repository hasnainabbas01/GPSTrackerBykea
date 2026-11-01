package com.bykea.gpstracking.app

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.bykea.gpstracking.R

/**
 * Declares the app's two channels. The ongoing status is low importance so it never makes noise
 * during a long session; alerts are separate and high importance because tracking has stopped and
 * only the user can fix it. Creating an existing channel is a cheap no-op.
 */
object NotificationChannels {
    const val TRACKING = "tracking"
    const val ALERTS = "alerts"

    fun create(context: Context) {
        val tracking = NotificationChannelCompat
            .Builder(TRACKING, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.channel_tracking_name))
            .setDescription(context.getString(R.string.channel_tracking_description))
            .setShowBadge(false)
            .build()
        val alerts = NotificationChannelCompat
            .Builder(ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.channel_alerts_name))
            .setDescription(context.getString(R.string.channel_alerts_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(listOf(tracking, alerts))
    }
}
