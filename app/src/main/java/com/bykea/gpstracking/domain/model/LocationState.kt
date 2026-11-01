package com.bykea.gpstracking.domain.model

import com.bykea.gpstracking.domain.policy.TrackingMode

/**
 * Everything the screen and notification need to show. [isLive] tells a current location apart from
 * an old saved one. [mode] and [battery] are null until they are known.
 */
data class LocationState(
    val location: TrackedLocation? = null,
    val isLive: Boolean = false,
    val ageMillis: Long = 0L,
    val mode: TrackingMode? = null,
    val motion: MotionState = MotionState.UNKNOWN,
    val gnss: GnssState = GnssState.UNKNOWN,
    val battery: BatteryState? = null,
    val trackingEnabled: Boolean = false,
    val intervalMs: Long? = null,
    val sessionStartWallClock: Long? = null,
) {
    /** Returns a copy with the location's age and LIVE status worked out for the given time. */
    fun refreshedAt(nowWallClock: Long): LocationState {
        val age = calculateAge(nowWallClock)
        val live = calculateIsLive(age)
        return copy(
            location = location?.copy(isCached = !live),
            isLive = live,
            ageMillis = age,
        )
    }

    private fun calculateAge(nowWallClock: Long): Long {
        if (location == null) return 0L
        return (nowWallClock - location.wallClockTime).coerceAtLeast(0L)
    }

    /** Live means tracking is on, and the location is from this session and is still recent. */
    private fun calculateIsLive(age: Long): Boolean {
        if (!trackingEnabled) return false
        if (location == null || intervalMs == null || sessionStartWallClock == null) return false

        val isFromThisSession = location.wallClockTime >= sessionStartWallClock
        val isRecent = age <= 2 * intervalMs + STALE_GRACE_MS
        return isFromThisSession && isRecent
    }

    private companion object {
        const val STALE_GRACE_MS = 30_000L
    }
}
