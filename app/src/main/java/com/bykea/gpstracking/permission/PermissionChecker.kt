package com.bykea.gpstracking.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads what the system currently allows. Also usable outside the UI, because the service, boot
 * receiver and watchdog must verify permissions themselves: the user can revoke them at any time.
 * Permissions that do not exist on this API level count as granted.
 */
@Singleton
class PermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
) {
    fun hasLocationPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** [locationRationale] comes from the caller, since only an Activity can read it. */
    fun snapshot(locationRationale: Boolean) = PermissionSnapshot(
        hasLocation = hasLocationPermission(),
        hasFineLocation = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        locationRationale = locationRationale,
        hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS),
        hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        isLocationServicesEnabled = LocationManagerCompat.isLocationEnabled(locationManager),
        isIgnoringBatteryOptimizations = context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
