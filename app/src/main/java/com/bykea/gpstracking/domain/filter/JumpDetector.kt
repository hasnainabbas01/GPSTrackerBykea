package com.bykea.gpstracking.domain.filter

import android.location.Location
import com.bykea.gpstracking.domain.model.MotionState
import com.bykea.gpstracking.util.AppLog
import javax.inject.Inject

/** [FORCED] means the user really did move that far, so earlier smoothing no longer applies. */
enum class JumpVerdict { ACCEPTED, REJECTED, FORCED }

/**
 * Second filter: rejects locations that report good accuracy but would need an impossible speed to
 * reach, usually caused by signals bouncing off buildings.
 *
 * If 3 rejected locations in a row agree with each other, the third is accepted. Otherwise a single
 * wrong location could cause every correct one after it to be rejected.
 */
class JumpDetector @Inject constructor() {
    private var lastAccepted: Location? = null
    private val rejectedStreak = mutableListOf<Location>()

    fun check(fix: Location, motion: MotionState): JumpVerdict {
        val last = lastAccepted ?: return accept(fix, JumpVerdict.ACCEPTED)

        // Uses elapsed time, because wall-clock time can jump.
        val elapsedNanos = fix.elapsedRealtimeNanos - last.elapsedRealtimeNanos
        if (elapsedNanos <= 0) return JumpVerdict.REJECTED

        val distance = fix.distanceTo(last)
        val speed = distance / (elapsedNanos / NANOS_PER_SECOND)
        val isTooFast = speed > maxSpeed(motion)
        val isBeyondAccuracy = distance > fix.accuracy + last.accuracy
        if (!isTooFast || !isBeyondAccuracy) return accept(fix, JumpVerdict.ACCEPTED)

        AppLog.d(TAG, "Rejected jump: ${distance.toInt()} m at ${speed.toInt()} m/s, motion $motion")
        return handleRejected(fix)
    }

    private fun handleRejected(fix: Location): JumpVerdict {
        val disagrees = rejectedStreak.any { !it.agreesWith(fix) }
        if (disagrees) rejectedStreak.clear()

        rejectedStreak += fix
        if (rejectedStreak.size < FORCE_ACCEPT_AFTER) return JumpVerdict.REJECTED
        return accept(fix, JumpVerdict.FORCED)
    }

    private fun accept(fix: Location, verdict: JumpVerdict): JumpVerdict {
        lastAccepted = fix
        rejectedStreak.clear()
        return verdict
    }

    private fun Location.agreesWith(other: Location): Boolean =
        distanceTo(other) <= accuracy + other.accuracy

    private fun maxSpeed(motion: MotionState): Float = when (motion) {
        MotionState.STILL -> 3f
        MotionState.WALKING -> 4f
        MotionState.RUNNING -> 8f
        MotionState.ON_BICYCLE -> 15f
        MotionState.IN_VEHICLE, MotionState.UNKNOWN -> 60f
    }

    private companion object {
        const val TAG = "JumpDetector"
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val FORCE_ACCEPT_AFTER = 3
    }
}
