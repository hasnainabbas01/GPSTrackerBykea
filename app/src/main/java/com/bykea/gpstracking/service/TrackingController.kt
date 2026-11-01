package com.bykea.gpstracking.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bykea.gpstracking.background.WatchdogWorker
import com.bykea.gpstracking.data.cache.TrackingPrefs
import com.bykea.gpstracking.di.IoDispatcher
import com.bykea.gpstracking.permission.PermissionChecker
import com.bykea.gpstracking.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What happened when tracking was requested, so callers can choose a fallback. */
enum class StartResult { STARTED, NOT_ALLOWED, PERMISSION_MISSING }

/**
 * The only way to start or stop tracking, used by the UI, notification and restart paths. Keeps the
 * persisted intent, the running service and the watchdog in step: the flag is what restart paths
 * obey. Starting is safe to repeat.
 */
class TrackingController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: TrackingPrefs,
    private val permissionChecker: PermissionChecker,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun start(): StartResult {
        if (!permissionChecker.hasLocationPermission()) return StartResult.PERMISSION_MISSING
        prefs.setTrackingEnabled(true)
        // Off the main thread, as the first WorkManager use opens its database.
        withContext(ioDispatcher) { scheduleWatchdog() }
        return try {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            StartResult.STARTED
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException on API 31+, a subclass of this.
            AppLog.w(TAG, "Foreground service start not allowed", e)
            StartResult.NOT_ALLOWED
        }
    }

    /** Restores tracking the user left on. Null means tracking is off, so nothing was attempted. */
    suspend fun resumeIfEnabled(): StartResult? =
        if (prefs.isTrackingEnabled.first()) start() else null

    suspend fun stop() {
        prefs.setTrackingEnabled(false)
        withContext(ioDispatcher) {
            WorkManager.getInstance(context).cancelUniqueWork(WatchdogWorker.UNIQUE_NAME)
        }
        context.stopService(Intent(context, TrackingService::class.java))
    }

    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(
            WATCHDOG_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WatchdogWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val TAG = "TrackingController"
        const val WATCHDOG_INTERVAL_MINUTES = 15L
    }
}
