package com.bykea.gpstracking.permission

import android.content.Context
import android.content.IntentSender
import com.bykea.gpstracking.data.source.LocationSourceFactory
import com.bykea.gpstracking.util.AppLog
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Finds the least disruptive way to turn device location on. With Play Services that is an in-app
 * dialog; a null result means the caller must send the user to system settings instead.
 */
class LocationServicesResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sourceFactory: LocationSourceFactory,
) {
    suspend fun resolution(): IntentSender? {
        if (!sourceFactory.isGmsAvailable) return null
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, REQUEST_INTERVAL_MS).build(),
            )
            .build()
        return try {
            LocationServices.getSettingsClient(context).checkLocationSettings(request).await()
            null
        } catch (e: ResolvableApiException) {
            e.resolution.intentSender
        } catch (e: ApiException) {
            AppLog.w(TAG, "checkLocationSettings failed", e)
            null
        }
    }

    private companion object {
        const val TAG = "LocationServicesResolver"
        const val REQUEST_INTERVAL_MS = 5_000L
    }
}
