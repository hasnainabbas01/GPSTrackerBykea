package com.bykea.gpstracking.permission

/** What the system currently allows, as read on each resume. */
data class PermissionSnapshot(
    val hasLocation: Boolean,
    val hasFineLocation: Boolean,
    val locationRationale: Boolean,
    val hasNotifications: Boolean,
    val hasBackgroundLocation: Boolean,
    val isLocationServicesEnabled: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
)

/** Persisted answers, needed because the system does not remember how a user responded. */
data class PermissionMemory(
    val locationAnswered: Boolean = false,
    val locationDismissed: Boolean = false,
    val notificationsHandled: Boolean = false,
    val backgroundHandled: Boolean = false,
    val batteryOffered: Boolean = false,
)

/** The screen to show. The system dialog is launched from a step rather than being a step. */
enum class PermissionStep {
    CHECK,
    EXPLAIN_LOCATION,
    PERMANENTLY_DENIED,
    UPGRADE_TO_PRECISE,
    NOTIFICATIONS,
    EXPLAIN_BACKGROUND,
    LOCATION_SERVICES,
    BATTERY_OPTIMIZATION,
    READY,
}

/**
 * Decides which permission screen to show. The step is worked out fresh each time rather than stored,
 * so changes the user makes in system settings are always picked up.
 */
object PermissionFlow {

    fun next(
        snapshot: PermissionSnapshot,
        memory: PermissionMemory,
        preciseDismissed: Boolean,
    ): PermissionStep = when {
        !snapshot.hasLocation ->
            if (memory.locationAnswered && !snapshot.locationRationale) {
                PermissionStep.PERMANENTLY_DENIED
            } else {
                PermissionStep.EXPLAIN_LOCATION
            }
        !snapshot.hasFineLocation && !preciseDismissed -> PermissionStep.UPGRADE_TO_PRECISE
        !snapshot.hasNotifications && !memory.notificationsHandled -> PermissionStep.NOTIFICATIONS
        !snapshot.hasBackgroundLocation && !memory.backgroundHandled ->
            PermissionStep.EXPLAIN_BACKGROUND
        !snapshot.isLocationServicesEnabled -> PermissionStep.LOCATION_SERVICES
        !snapshot.isIgnoringBatteryOptimizations && !memory.batteryOffered ->
            PermissionStep.BATTERY_OPTIMIZATION
        else -> PermissionStep.READY
    }

    /**
     * Records a refused location dialog. Android can't tell us whether the user tapped outside the
     * dialog or Android blocked it for good, as both look the same. So the first time it gets one
     * more try, and if it happens again the app sends the user to App Settings.
     */
    fun afterLocationDenied(
        memory: PermissionMemory,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
    ): PermissionMemory {
        val noRationale = !rationaleBefore && !rationaleAfter
        val isFirstTime = !memory.locationAnswered && !memory.locationDismissed
        return if (noRationale && isFirstTime) {
            memory.copy(locationDismissed = true)
        } else {
            memory.copy(locationAnswered = true)
        }
    }
}
