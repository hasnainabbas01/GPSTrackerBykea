package com.bykea.gpstracking.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bykea.gpstracking.R
import com.bykea.gpstracking.app.NotificationChannels
import com.bykea.gpstracking.domain.model.LocationState
import com.bykea.gpstracking.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builds and posts the tracker's notifications. Status updates are limited to one per 30 s, since
 * each one wakes the system UI and would otherwise add a real battery cost at 5 s update rates.
 */
@Singleton
class TrackingNotification @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)
    private var lastUpdateMs: Long? = null
    private var lastText: String? = null

    /** For `startForeground`, which runs again on each repeat start and must keep the status. */
    fun current(): Notification =
        status(lastText ?: context.getString(R.string.notification_starting))

    /** Posts [state] unless nothing changed or the last update was under 30 s ago. */
    fun update(state: LocationState) {
        val location = state.location ?: return
        val mode = state.mode ?: return
        val text = context.getString(
            R.string.notification_tracking,
            location.accuracyMeters.roundToInt(),
            mode.name,
        )
        val now = SystemClock.elapsedRealtime()
        val last = lastUpdateMs
        if (text == lastText || (last != null && now - last < UPDATE_THROTTLE_MS)) return
        lastUpdateMs = now
        lastText = text
        post(NOTIFICATION_ID, status(text))
    }

    fun postPermissionNeeded() {
        postAlert(R.string.alert_permission_title, R.string.alert_permission_body)
    }

    /** Android refused a background start. Opening the app resumes tracking, as it is in front. */
    fun postTapToResume() {
        postAlert(R.string.alert_resume_title, R.string.alert_resume_body)
    }

    fun clearAlert() {
        manager.cancel(ALERT_ID)
    }

    private fun postAlert(@StringRes title: Int, @StringRes body: Int) {
        val alert = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(body))
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        post(ALERT_ID, alert)
    }

    private fun status(text: String): Notification =
        NotificationCompat.Builder(context, NotificationChannels.TRACKING)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notification_stop), stopIntent())
            .build()

    // areNotificationsEnabled() covers the permission.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context,
        0,
        Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_ID = 1
        private const val ALERT_ID = 2
        private const val UPDATE_THROTTLE_MS = 30_000L
    }
}
