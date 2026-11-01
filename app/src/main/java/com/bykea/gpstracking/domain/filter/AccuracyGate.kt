package com.bykea.gpstracking.domain.filter

import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationCompat
import com.bykea.gpstracking.BuildConfig
import com.bykea.gpstracking.domain.model.GnssState
import javax.inject.Inject

/** A location that passed. [lowConfidence] means it was only let through after a long wait. */
data class GateDecision(val location: Location, val lowConfidence: Boolean)

/**
 * First filter: rejects locations with poor accuracy, and is stricter when few satellites are in use,
 * as signals often bounce off tall buildings then.
 *
 * So the user isn't left with no location, the best rejected one is let through after 60 s without
 * an accepted location, marked as low confidence.
 */
class AccuracyGate @Inject constructor() {
    private var waitStartNanos: Long? = null
    private var bestRejected: Location? = null

    /** Returns the location to continue with, which may be an earlier one, or null if none. */
    fun check(raw: Location, gnss: GnssState): GateDecision? {
        if (!BuildConfig.DEBUG && LocationCompat.isMock(raw)) return null
        if (!raw.hasAccuracy()) return null

        val now = raw.elapsedRealtimeNanos
        if (raw.accuracy <= thresholdFor(raw, gnss)) {
            restartWait(now)
            return GateDecision(raw, lowConfidence = false)
        }

        val best = betterOf(raw, bestRejected)
        bestRejected = best

        val waitStart = waitStartNanos ?: now
        waitStartNanos = waitStart
        if (now - waitStart < MAX_WAIT_NANOS) return null

        restartWait(now)
        return GateDecision(best, lowConfidence = true)
    }

    private fun betterOf(location: Location, other: Location?): Location {
        if (other == null) return location
        return if (location.accuracy < other.accuracy) location else other
    }

    private fun restartWait(startNanos: Long) {
        waitStartNanos = startNanos
        bestRejected = null
    }

    private fun thresholdFor(raw: Location, gnss: GnssState): Float {
        // UNKNOWN only means there is no satellite data yet, not that the signal is bad.
        val hasSatelliteData = gnss != GnssState.UNKNOWN
        val hasFewSatellites = gnss.satellitesUsedInFix < MIN_SATELLITES
        val isGps = raw.provider == LocationManager.GPS_PROVIDER
        return if (hasSatelliteData && hasFewSatellites && isGps) STRICT_MAX_ACCURACY_M else MAX_ACCURACY_M
    }

    private companion object {
        const val MAX_ACCURACY_M = 50f
        const val STRICT_MAX_ACCURACY_M = 30f
        const val MIN_SATELLITES = 4
        const val MAX_WAIT_NANOS = 60_000_000_000L
    }
}
