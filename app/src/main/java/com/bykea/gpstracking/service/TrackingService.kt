package com.bykea.gpstracking.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.bykea.gpstracking.data.LocationRepository
import com.bykea.gpstracking.data.cache.TrackingPrefs
import com.bykea.gpstracking.data.source.LocationSource
import com.bykea.gpstracking.device.DeviceStateMonitor
import com.bykea.gpstracking.domain.policy.TrackingConfig
import com.bykea.gpstracking.domain.policy.TrackingMode
import com.bykea.gpstracking.domain.policy.TrackingPolicy
import com.bykea.gpstracking.domain.policy.TrackingPriority
import com.bykea.gpstracking.permission.PermissionChecker
import com.bykea.gpstracking.util.AppLog
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Runs location tracking. It must be a foreground service, as Android only gives frequent location
 * updates in the background to this kind of service. It uses no wake locks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var repository: LocationRepository
    @Inject lateinit var controller: TrackingController
    @Inject lateinit var locationSource: LocationSource
    @Inject lateinit var prefs: TrackingPrefs
    @Inject lateinit var permissionChecker: PermissionChecker
    @Inject lateinit var notification: TrackingNotification
    @Inject lateinit var deviceStateMonitor: DeviceStateMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var trackingJob: Job? = null
    private val gpsInUse = MutableStateFlow(true)

    override fun onBind(intent: Intent?): IBinder? = null

    /** The intent is null when Android restarts the service after killing it. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { controller.stop() }
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            onForegroundRefused()
            return START_NOT_STICKY
        }
        notification.clearAlert()
        if (trackingJob?.isActive != true) {
            trackingJob = scope.launch { track() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        repository.endSession()
        repository.flush()
        super.onDestroy()
    }

    /**
     * Returns false if Android refuses. Android 14+ refuses without location permission, and
     * Android 12+ can refuse a start from the background.
     */
    @SuppressLint("InlinedApi")
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            TrackingNotification.NOTIFICATION_ID,
            notification.current(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: SecurityException) {
        AppLog.w(TAG, "startForeground rejected", e)
        false
    } catch (e: IllegalStateException) {
        AppLog.w(TAG, "startForeground rejected", e)
        false
    }

    private fun onForegroundRefused() {
        if (permissionChecker.hasLocationPermission()) {
            notification.postTapToResume()
        } else {
            notification.postPermissionNeeded()
        }
        stopSelf()
    }

    private suspend fun track() {
        if (!prefs.isTrackingEnabled.first()) {
            stopSelf()
            return
        }
        if (!permissionChecker.hasLocationPermission()) {
            onPermissionLost()
            return
        }
        try {
            logLastExit()
            repository.startSession()
            coroutineScope {
                launch { repository.state.collect(notification::update) }
                launch { getFirstLocation() }
                receiveLocationUpdates()
            }
        } catch (e: SecurityException) {
            AppLog.w(TAG, "Location permission revoked", e)
            onPermissionLost()
        } catch (e: ApiException) {
            AppLog.e(TAG, "Location updates failed", e)
            stopSelf()
        }
    }

    /** Asks for one location straight away, so the user doesn't wait for the first update. */
    private suspend fun getFirstLocation() {
        val location = locationSource.currentLocation(TrackingPriority.BALANCED)
        if (location != null) repository.onFreshFix(location)
    }

    /** Runs until cancelled. `flatMapLatest` stops the old request whenever the config changes. */
    private suspend fun receiveLocationUpdates() {
        trackingConfigs()
            .flatMapLatest { config -> locationSource.locationUpdates(config) }
            .collect { location ->
                deviceStateMonitor.onFix(location)
                repository.onFreshFix(location)
            }
    }

    /** Logs why the app was last killed, which helps diagnose phones that kill background apps. */
    private fun logLastExit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val exit = getSystemService<ActivityManager>()
            ?.getHistoricalProcessExitReasons(packageName, 0, EXIT_REASONS_TO_READ)
            ?.firstOrNull()
            ?: return
        AppLog.i(TAG, "Last exit: reason=${exit.reason} (${exit.description})")
    }

    /** Emits the location request settings to use, whenever the device's condition changes them. */
    private fun trackingConfigs(): Flow<TrackingConfig> =
        deviceStateMonitor.states(gpsInUse)
            .onEach { deviceState -> repository.onDeviceState(deviceState) }
            .map { deviceState -> TrackingPolicy.evaluate(deviceState) }
            .distinctUntilChanged()
            .flatMapLatest { config -> addWeakGnssRetries(config) }
            .onEach { config ->
                gpsInUse.value = config.priority == TrackingPriority.HIGH_ACCURACY
                repository.onConfigChanged(config)
            }

    /** With a weak signal, GPS is retried briefly, as that's the only way to see it recover. */
    private fun addWeakGnssRetries(config: TrackingConfig): Flow<TrackingConfig> {
        if (config.mode != TrackingMode.WEAK_GNSS) return flowOf(config)

        return flow {
            while (true) {
                emit(config)
                delay(TrackingPolicy.WEAK_GNSS_RETRY_EVERY_MS)
                emit(TrackingPolicy.WEAK_GNSS_RETRY)
                delay(TrackingPolicy.WEAK_GNSS_RETRY_FOR_MS)
            }
        }
    }

    /** Tracking stays switched on, so it resumes once the user grants permission again. */
    private fun onPermissionLost() {
        notification.postPermissionNeeded()
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.bykea.gpstracking.action.STOP_TRACKING"
        private const val TAG = "TrackingService"
        private const val EXIT_REASONS_TO_READ = 1
    }
}
