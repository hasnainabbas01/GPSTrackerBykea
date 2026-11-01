package com.bykea.gpstracking.domain.filter

import com.bykea.gpstracking.domain.model.MotionState
import javax.inject.Inject
import kotlin.math.sqrt

/** A smoothed position with its estimated accuracy. */
data class KalmanEstimate(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

/**
 * Final pipeline stage: smooths the jitter that remains in plausible fixes, weighting each by its
 * reported accuracy. A simple position filter is enough here and is cheap to run on every fix;
 * process noise follows motion, so a still user is smoothed hard and a fast one is not lagged.
 * Not thread-safe.
 */
class KalmanFilter @Inject constructor() {
    private var latitude = 0.0
    private var longitude = 0.0
    private var varianceM2 = 0.0
    private var lastNanos: Long? = null

    /** Use [reset] when the position really changed, so the old estimate is not blended in. */
    fun update(
        measuredLatitude: Double,
        measuredLongitude: Double,
        accuracyMeters: Float,
        elapsedRealtimeNanos: Long,
        motion: MotionState,
        reset: Boolean,
    ): KalmanEstimate {
        val measurementVariance = accuracyMeters.toDouble() * accuracyMeters
        val last = lastNanos
        val elapsedNanos = last?.let { elapsedRealtimeNanos - it }

        if (reset || elapsedNanos == null || elapsedNanos > RESET_GAP_NANOS) {
            latitude = measuredLatitude
            longitude = measuredLongitude
            varianceM2 = measurementVariance
        } else {
            val q = processNoise(motion)
            varianceM2 += (elapsedNanos / NANOS_PER_SECOND) * q * q
            val gain = varianceM2 / (varianceM2 + measurementVariance)
            latitude += gain * (measuredLatitude - latitude)
            longitude += gain * (measuredLongitude - longitude)
            varianceM2 *= 1 - gain
        }
        lastNanos = elapsedRealtimeNanos
        return KalmanEstimate(latitude, longitude, sqrt(varianceM2).toFloat())
    }

    private fun processNoise(motion: MotionState): Double = when (motion) {
        MotionState.STILL -> 0.5
        MotionState.WALKING, MotionState.RUNNING -> 3.0
        // Unknown motion may be fast. Following measurements closely beats lagging behind them.
        MotionState.ON_BICYCLE, MotionState.IN_VEHICLE, MotionState.UNKNOWN -> 15.0
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val RESET_GAP_NANOS = 5 * 60 * 1_000_000_000L
    }
}
