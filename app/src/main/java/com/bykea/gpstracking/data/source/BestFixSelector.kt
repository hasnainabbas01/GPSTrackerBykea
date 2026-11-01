package com.bykea.gpstracking.data.source

import android.location.Location

/**
 * Merges concurrent GPS and network streams on devices without a fused provider. A network fix can
 * arrive just after a better GPS fix, so blindly taking the latest would make the position jitter.
 * Not thread-safe: use from one thread.
 */
class BestFixSelector {
    private var best: Location? = null

    /** Returns true and remembers [candidate] if it should replace the current best fix. */
    fun offer(candidate: Location): Boolean {
        if (!isBetter(candidate, best)) return false
        best = candidate
        return true
    }

    private fun isBetter(candidate: Location, current: Location?): Boolean {
        if (current == null) return true

        val ageDeltaNanos = candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos
        if (ageDeltaNanos > SIGNIFICANT_AGE_NANOS) return true
        if (ageDeltaNanos < -SIGNIFICANT_AGE_NANOS) return false

        val accuracyDelta = candidate.accuracyOrWorst() - current.accuracyOrWorst()
        if (accuracyDelta < 0) return true
        if (accuracyDelta > SIGNIFICANTLY_LESS_ACCURATE_M) return false

        val isNewer = ageDeltaNanos > 0
        return isNewer && (accuracyDelta == 0f || candidate.provider == current.provider)
    }

    // Location.accuracy reads as 0 when absent, which would otherwise look perfectly accurate.
    private fun Location.accuracyOrWorst(): Float = if (hasAccuracy()) accuracy else Float.MAX_VALUE

    private companion object {
        const val SIGNIFICANT_AGE_NANOS = 2 * 60 * 1_000_000_000L
        const val SIGNIFICANTLY_LESS_ACCURATE_M = 200f
    }
}
