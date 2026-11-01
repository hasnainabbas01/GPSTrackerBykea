package com.bykea.gpstracking.domain.model

/**
 * A fix that passed the accuracy pipeline, free of Android types so it can be cached and shown
 * anywhere. Holds both clocks: `elapsedRealtimeNanos` for intervals because it cannot jump, and
 * `wallClockTime` for display and for ages that must survive a reboot.
 */
data class TrackedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitude: Double?,
    val speed: Float?,
    val bearing: Float?,
    val provider: String,
    val elapsedRealtimeNanos: Long,
    val wallClockTime: Long,
    val lowConfidence: Boolean,
    val isCached: Boolean,
)
