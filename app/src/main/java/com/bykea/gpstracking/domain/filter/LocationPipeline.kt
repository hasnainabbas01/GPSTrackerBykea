package com.bykea.gpstracking.domain.filter

import android.location.Location
import com.bykea.gpstracking.domain.model.GnssState
import com.bykea.gpstracking.domain.model.MotionState
import com.bykea.gpstracking.domain.model.TrackedLocation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw fixes into trustworthy positions near high-rise buildings: [AccuracyGate] drops fixes
 * that admit they are poor, [JumpDetector] drops ones that are impossible, and [KalmanFilter]
 * smooths the rest. Holds per-process state and is not thread-safe, so call it from one thread.
 */
@Singleton
class LocationPipeline @Inject constructor(
    private val accuracyGate: AccuracyGate,
    private val jumpDetector: JumpDetector,
    private val kalmanFilter: KalmanFilter,
) {
    /** Returns the position to publish, or null if this fix should not change it. */
    fun process(raw: Location, motion: MotionState, gnss: GnssState): TrackedLocation? {
        val decision = accuracyGate.check(raw, gnss) ?: return null
        val fix = decision.location

        val verdict = jumpDetector.check(fix, motion)
        if (verdict == JumpVerdict.REJECTED) return null

        val estimate = kalmanFilter.update(
            measuredLatitude = fix.latitude,
            measuredLongitude = fix.longitude,
            accuracyMeters = fix.accuracy,
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos,
            motion = motion,
            reset = verdict == JumpVerdict.FORCED,
        )
        return TrackedLocation(
            latitude = estimate.latitude,
            longitude = estimate.longitude,
            accuracyMeters = estimate.accuracyMeters,
            altitude = if (fix.hasAltitude()) fix.altitude else null,
            speed = if (fix.hasSpeed()) fix.speed else null,
            bearing = if (fix.hasBearing()) fix.bearing else null,
            provider = fix.provider ?: UNKNOWN_PROVIDER,
            elapsedRealtimeNanos = fix.elapsedRealtimeNanos,
            wallClockTime = fix.time,
            lowConfidence = decision.lowConfidence,
            isCached = false,
        )
    }

    private companion object {
        const val UNKNOWN_PROVIDER = "unknown"
    }
}
