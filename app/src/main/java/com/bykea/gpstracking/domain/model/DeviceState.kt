package com.bykea.gpstracking.domain.model

/** Battery facts that decide whether tracking must back off to protect the phone. */
data class BatteryState(
    val levelPercent: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
)

/** What the user is doing; a still user needs far fewer fixes, which is the largest battery win. */
enum class MotionState { STILL, WALKING, RUNNING, ON_BICYCLE, IN_VEHICLE, UNKNOWN }

/**
 * GNSS signal quality. `isWeak` signals urban canyons and indoors, where retrying GPS at full rate
 * wastes battery and multipath makes fixes less trustworthy.
 */
data class GnssState(
    val satellitesUsedInFix: Int,
    val satellitesVisible: Int,
    val avgCn0DbHzOfUsed: Float,
    val isWeak: Boolean,
) {
    companion object {
        /** No GNSS data yet, for example while no GPS-using priority is active. */
        val UNKNOWN = GnssState(
            satellitesUsedInFix = 0,
            satellitesVisible = 0,
            avgCn0DbHzOfUsed = 0f,
            isWeak = false,
        )
    }
}

/** The complete input of the tracking policy, so the policy stays a pure function. */
data class DeviceState(
    val battery: BatteryState,
    val gnss: GnssState,
    val motion: MotionState,
)
