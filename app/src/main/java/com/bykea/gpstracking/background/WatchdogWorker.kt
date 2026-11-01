package com.bykea.gpstracking.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bykea.gpstracking.data.LocationRepository
import com.bykea.gpstracking.data.cache.TrackingPrefs
import com.bykea.gpstracking.data.source.LocationSource
import com.bykea.gpstracking.domain.policy.TrackingPriority
import com.bykea.gpstracking.permission.PermissionChecker
import com.bykea.gpstracking.service.StartResult
import com.bykea.gpstracking.service.TrackingController
import com.bykea.gpstracking.service.TrackingNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Last line of defence: WorkManager survives process death and is one of the few things that still
 * runs after an OEM kills the service. Every 15 minutes it refreshes the cache and restarts tracking
 * if the service is gone. It costs almost nothing while the service is healthy.
 */
@HiltWorker
class WatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: TrackingPrefs,
    private val permissionChecker: PermissionChecker,
    private val locationSource: LocationSource,
    private val repository: LocationRepository,
    private val controller: TrackingController,
    private val notification: TrackingNotification,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!prefs.isTrackingEnabled.first() || !permissionChecker.hasLocationPermission()) {
            return Result.success()
        }
        if (repository.isSessionActive) return Result.success()

        locationSource.currentLocation(TrackingPriority.BALANCED)?.let { repository.onFreshFix(it) }
        if (controller.start() == StartResult.NOT_ALLOWED) notification.postTapToResume()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "tracking-watchdog"
    }
}
