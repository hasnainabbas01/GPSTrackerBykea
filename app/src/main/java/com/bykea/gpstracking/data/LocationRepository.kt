package com.bykea.gpstracking.data

import android.location.Location
import com.bykea.gpstracking.data.cache.LocationCache
import com.bykea.gpstracking.data.cache.TrackingPrefs
import com.bykea.gpstracking.di.ApplicationScope
import com.bykea.gpstracking.domain.filter.LocationPipeline
import com.bykea.gpstracking.domain.model.DeviceState
import com.bykea.gpstracking.domain.model.LocationState
import com.bykea.gpstracking.domain.model.TrackedLocation
import com.bykea.gpstracking.domain.policy.TrackingConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the current tracking state for the service and the UI.
 *
 * The location starts as the saved one and is replaced by each new location that passes the filters.
 * Mode, motion and GNSS are kept in memory only, as they mean nothing after the app has been killed.
 */
@Singleton
class LocationRepository @Inject constructor(
    private val cache: LocationCache,
    prefs: TrackingPrefs,
    private val pipeline: LocationPipeline,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val newestLocation = MutableStateFlow<TrackedLocation?>(null)
    private val sessionState = MutableStateFlow(LocationState())

    private val locationToShow = combine(cache.lastLocation, newestLocation) { saved, newest ->
        pickNewer(saved, newest)
    }

    /** Updates whenever the location, the session or the on/off flag changes. */
    val state: StateFlow<LocationState> = combine(
        locationToShow,
        sessionState,
        prefs.isTrackingEnabled,
    ) { location, session, trackingEnabled ->
        val state = session.copy(location = location, trackingEnabled = trackingEnabled)
        state.refreshedAt(System.currentTimeMillis())
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocationState())

    /** True while the service is tracking. The watchdog uses this to know the service is alive. */
    val isSessionActive: Boolean
        get() = sessionState.value.sessionStartWallClock != null

    fun startSession() {
        sessionState.value = LocationState(sessionStartWallClock = System.currentTimeMillis())
    }

    fun endSession() {
        sessionState.value = LocationState()
    }

    fun onConfigChanged(config: TrackingConfig) {
        sessionState.update { it.copy(mode = config.mode, intervalMs = config.intervalMs) }
    }

    fun onDeviceState(deviceState: DeviceState) {
        sessionState.update { it.copy(motion = deviceState.motion, gnss = deviceState.gnss) }
    }

    /** Runs [raw] through the filters, then shows and saves it if it passes. */
    suspend fun onFreshFix(raw: Location) {
        val session = sessionState.value
        val filtered = pipeline.process(raw, session.motion, session.gnss) ?: return
        newestLocation.value = filtered
        cache.saveLocation(filtered)
    }

    /** Saves the newest location when tracking ends. Uses the app's scope to outlive the service. */
    fun flush() {
        val location = newestLocation.value ?: return
        scope.launch { cache.saveLocation(location, force = true) }
    }

    private fun pickNewer(saved: TrackedLocation?, newest: TrackedLocation?): TrackedLocation? {
        if (newest == null) return saved
        if (saved == null) return newest
        return if (newest.wallClockTime >= saved.wallClockTime) newest else saved
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
