package com.bykea.gpstracking.device.motion

import android.location.Location
import com.bykea.gpstracking.domain.model.MotionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * Works out what the user is doing from location updates alone, so it needs no extra permission and
 * behaves the same on every device.
 *
 * Getting no updates for 2 minutes also counts as still, because location requests have a minimum
 * distance: a phone that isn't moving stops receiving updates.
 */
@Singleton
class LocationBasedMotionDetector @Inject constructor() {
    private val latestFix = MutableStateFlow<Location?>(null)
    private val recentFixes = ArrayDeque<Location>()
    private var currentMotion = MotionState.UNKNOWN

    fun onFix(fix: Location) {
        latestFix.value = fix
    }

    /** `transformLatest` cancels the 2-minute wait as soon as a new location arrives. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun motionStates(): Flow<MotionState> = latestFix
        .transformLatest { fix ->
            if (fix == null) {
                emit(MotionState.UNKNOWN)
                return@transformLatest
            }
            currentMotion = classify(fix)
            emit(currentMotion)

            delay(STILL_AFTER_MS)
            currentMotion = MotionState.STILL
            emit(currentMotion)
        }
        .distinctUntilChanged()

    private fun classify(fix: Location): MotionState {
        val previousFix = recentFixes.lastOrNull()
        addToHistory(fix)

        if (hasStayedInPlace(fix)) return MotionState.STILL

        val speed = speedOf(fix, previousFix) ?: return currentMotion
        return motionForSpeed(speed) ?: currentMotion
    }

    /** Keeps only the last 15 minutes of locations. */
    private fun addToHistory(fix: Location) {
        recentFixes.addLast(fix)
        while (fix.elapsedRealtimeNanos - recentFixes.first().elapsedRealtimeNanos > HISTORY_NANOS) {
            recentFixes.removeFirst()
        }
    }

    /** True if at least 3 locations over at least 2 minutes all overlap with the newest one. */
    private fun hasStayedInPlace(fix: Location): Boolean {
        val historyNanos = fix.elapsedRealtimeNanos - recentFixes.first().elapsedRealtimeNanos
        if (recentFixes.size < MIN_FIXES) return false
        if (historyNanos < STILL_AFTER_MS * NANOS_PER_MS) return false

        return recentFixes.all { it.distanceTo(fix) <= it.accuracy + fix.accuracy }
    }

    /** Uses the reported speed, or else works it out from the distance. Null if neither is usable. */
    private fun speedOf(fix: Location, previousFix: Location?): Float? {
        if (fix.hasSpeed()) return fix.speed
        if (previousFix == null) return null

        val elapsedNanos = fix.elapsedRealtimeNanos - previousFix.elapsedRealtimeNanos
        if (elapsedNanos <= 0) return null

        // Low-accuracy locations drift while the user stands still, which must not count as walking.
        val distance = fix.distanceTo(previousFix)
        if (distance <= fix.accuracy + previousFix.accuracy) return null

        return distance / (elapsedNanos / NANOS_PER_SECOND)
    }

    /** Returns null below walking speed, which is too unreliable to change the motion state. */
    private fun motionForSpeed(speed: Float): MotionState? = when {
        speed < MIN_WALKING_MPS -> null
        speed < MAX_WALKING_MPS -> MotionState.WALKING
        speed < MAX_RUNNING_MPS -> MotionState.RUNNING
        speed < MAX_CYCLING_MPS -> MotionState.ON_BICYCLE
        else -> MotionState.IN_VEHICLE
    }

    private companion object {
        const val MIN_FIXES = 3
        const val STILL_AFTER_MS = 2 * 60 * 1000L
        const val HISTORY_NANOS = 15 * 60 * 1_000_000_000L
        const val NANOS_PER_MS = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MIN_WALKING_MPS = 0.5f
        const val MAX_WALKING_MPS = 2.5f
        const val MAX_RUNNING_MPS = 5f
        const val MAX_CYCLING_MPS = 9f
    }
}
