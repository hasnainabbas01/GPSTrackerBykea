package com.bykea.gpstracking.device

import android.location.Location
import com.bykea.gpstracking.device.motion.LocationBasedMotionDetector
import com.bykea.gpstracking.domain.model.DeviceState
import com.bykea.gpstracking.domain.model.GnssState
import com.bykea.gpstracking.domain.model.MotionState
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample

/**
 * Combines battery, satellite signal and motion into one [DeviceState].
 *
 * Changes wait 5 s, because restarting a location request has a cost. The first state and the moment
 * the user starts moving apply immediately.
 */
class DeviceStateMonitor @Inject constructor(
    private val batteryMonitor: BatteryMonitor,
    private val gnssMonitor: GnssMonitor,
    private val motionDetector: LocationBasedMotionDetector,
) {
    fun onFix(fix: Location) = motionDetector.onFix(fix)

    @OptIn(FlowPreview::class)
    fun states(gpsInUse: Flow<Boolean>): Flow<DeviceState> {
        // Null until the first state has been seen.
        var wasStill: Boolean? = null

        return combine(
            batteryMonitor.states(),
            sampledGnssStates(gpsInUse),
            motionDetector.motionStates(),
        ) { battery, gnss, motion ->
            DeviceState(battery, gnss, motion)
        }
            .distinctUntilChanged()
            .debounce { state ->
                val isStill = state.motion == MotionState.STILL
                val isFirstState = wasStill == null
                val startedMoving = wasStill == true && !isStill
                wasStill = isStill

                if (isFirstState || startedMoving) 0L else DEBOUNCE_MS
            }
    }

    /** GNSS changes about once a second. Without sampling, the 5 s wait above would never end. */
    @OptIn(FlowPreview::class)
    private fun sampledGnssStates(gpsInUse: Flow<Boolean>): Flow<GnssState> =
        gnssMonitor.states(gpsInUse)
            .sample(GNSS_SAMPLE_MS)
            .onStart { emit(GnssState.UNKNOWN) }

    private companion object {
        const val DEBOUNCE_MS = 5_000L
        const val GNSS_SAMPLE_MS = 15_000L
    }
}
