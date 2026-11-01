package com.bykea.gpstracking.domain.policy

import com.bykea.gpstracking.domain.model.DeviceState
import com.bykea.gpstracking.domain.model.MotionState

/**
 * Decides how hard to work for a location. A pure function of device state with no Android
 * dependencies, so every battery and accuracy trade-off is readable in one table. Battery rules come
 * first because protecting the phone outranks tracking precision.
 */
object TrackingPolicy {
    private const val SECOND_MS = 1_000L
    private const val MINUTE_MS = 60 * SECOND_MS

    private val CRITICAL = TrackingConfig(
        mode = TrackingMode.CRITICAL,
        priority = TrackingPriority.LOW_POWER,
        intervalMs = 60 * SECOND_MS,
        minIntervalMs = 60 * SECOND_MS,
        minDistanceM = 50f,
        maxUpdateDelayMs = 5 * MINUTE_MS,
    )
    private val SAVER = TrackingConfig(
        mode = TrackingMode.SAVER,
        priority = TrackingPriority.BALANCED,
        intervalMs = 30 * SECOND_MS,
        minIntervalMs = 30 * SECOND_MS,
        minDistanceM = 20f,
        maxUpdateDelayMs = 2 * MINUTE_MS,
    )
    private val STATIONARY = TrackingConfig(
        mode = TrackingMode.STATIONARY,
        priority = TrackingPriority.LOW_POWER,
        // Short, because the next fix is the only way to notice the user has started moving.
        intervalMs = MINUTE_MS,
        minIntervalMs = MINUTE_MS,
        minDistanceM = 0f,
        maxUpdateDelayMs = 0L,
    )
    private val WEAK_GNSS = TrackingConfig(
        mode = TrackingMode.WEAK_GNSS,
        priority = TrackingPriority.BALANCED,
        intervalMs = 15 * SECOND_MS,
        minIntervalMs = 15 * SECOND_MS,
        minDistanceM = 10f,
        maxUpdateDelayMs = 0L,
    )
    private val ACTIVE = TrackingConfig(
        mode = TrackingMode.ACTIVE,
        priority = TrackingPriority.HIGH_ACCURACY,
        intervalMs = 5 * SECOND_MS,
        minIntervalMs = 5 * SECOND_MS,
        minDistanceM = 5f,
        maxUpdateDelayMs = 0L,
    )

    /** The brief GPS attempt made during weak signal, to find out whether the signal came back. */
    val WEAK_GNSS_RETRY: TrackingConfig = WEAK_GNSS.copy(priority = TrackingPriority.HIGH_ACCURACY)

    /** How long weak-signal mode stays on network location between GPS attempts. */
    const val WEAK_GNSS_RETRY_EVERY_MS: Long = 2 * MINUTE_MS

    /** How long each GPS attempt lasts during weak signal. */
    const val WEAK_GNSS_RETRY_FOR_MS: Long = 30 * SECOND_MS

    fun evaluate(state: DeviceState): TrackingConfig {
        val battery = state.battery
        return when {
            battery.levelPercent <= 10 && !battery.isCharging -> CRITICAL
            (battery.levelPercent <= 20 && !battery.isCharging) || battery.isPowerSaveMode -> SAVER
            state.motion == MotionState.STILL -> STATIONARY
            state.gnss.isWeak -> WEAK_GNSS
            else -> ACTIVE
        }
    }
}
