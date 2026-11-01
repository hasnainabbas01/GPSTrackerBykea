package com.bykea.gpstracking.domain.policy

/** Why the current request looks the way it does. Shown in the UI and notification. */
enum class TrackingMode { CRITICAL, SAVER, STATIONARY, WEAK_GNSS, ACTIVE }

/** Our own priority scale, so the domain layer does not depend on GMS classes. */
enum class TrackingPriority { HIGH_ACCURACY, BALANCED, LOW_POWER, PASSIVE }

/**
 * A provider-neutral description of one location request. Being a value type, an unchanged config
 * compares equal and does not trigger a costly re-subscription.
 */
data class TrackingConfig(
    val mode: TrackingMode,
    val priority: TrackingPriority,
    val intervalMs: Long,
    val minIntervalMs: Long,
    val minDistanceM: Float,
    val maxUpdateDelayMs: Long,
)
