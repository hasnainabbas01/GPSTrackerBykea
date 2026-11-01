package com.bykea.gpstracking.data.source

import android.location.Location
import com.bykea.gpstracking.domain.policy.TrackingConfig
import com.bykea.gpstracking.domain.policy.TrackingPriority
import kotlinx.coroutines.flow.Flow

/** Hides whether fixes come from Play Services or the platform, so the same tracker runs on both. */
interface LocationSource {
    /**
     * Cold stream of raw fixes; collecting registers the request and cancelling removes it. Fails
     * with [SecurityException] if permission is missing so the service can stop cleanly.
     */
    fun locationUpdates(config: TrackingConfig): Flow<Location>

    /** A one-shot fresh fix, or null if none could be obtained. */
    suspend fun currentLocation(priority: TrackingPriority): Location?

    /** The best fix the system already holds, at no power cost, or null. */
    suspend fun lastKnownLocation(): Location?
}
