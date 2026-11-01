package com.bykea.gpstracking.data.source

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.bykea.gpstracking.domain.policy.TrackingConfig
import com.bykea.gpstracking.domain.policy.TrackingPriority
import com.bykea.gpstracking.util.AppLog
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Preferred source where Play Services exists: the fused provider blends GPS, Wi-Fi, cell and
 * sensors and shares fixes across apps, which gives better accuracy per unit of battery than
 * anything we could do by driving the providers ourselves.
 */
// Permission is checked by callers; a revocation race surfaces as SecurityException, handled here.
@SuppressLint("MissingPermission")
class FusedLocationSource @Inject constructor(
    private val client: FusedLocationProviderClient,
) : LocationSource {

    override fun locationUpdates(config: TrackingConfig): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(config.priority.toGmsPriority(), config.intervalMs)
            .setMinUpdateIntervalMillis(config.minIntervalMs)
            .setMinUpdateDistanceMeters(config.minDistanceM)
            .setMaxUpdateDelayMillis(config.maxUpdateDelayMs)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close(it) }
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    override suspend fun currentLocation(priority: TrackingPriority): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority.toGmsPriority())
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(request, cancellation.token).await()
        } catch (e: SecurityException) {
            AppLog.w(TAG, "currentLocation: permission missing", e)
            null
        } catch (e: ApiException) {
            AppLog.w(TAG, "currentLocation failed", e)
            null
        } finally {
            // Stops the radio request if the coroutine was cancelled; a no-op once complete.
            cancellation.cancel()
        }
    }

    override suspend fun lastKnownLocation(): Location? = try {
        client.lastLocation.await()
    } catch (e: SecurityException) {
        AppLog.w(TAG, "lastKnownLocation: permission missing", e)
        null
    } catch (e: ApiException) {
        AppLog.w(TAG, "lastKnownLocation failed", e)
        null
    }

    private fun TrackingPriority.toGmsPriority(): Int = when (this) {
        TrackingPriority.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
        TrackingPriority.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        TrackingPriority.LOW_POWER -> Priority.PRIORITY_LOW_POWER
        TrackingPriority.PASSIVE -> Priority.PRIORITY_PASSIVE
    }

    private companion object {
        const val TAG = "FusedLocationSource"
    }
}
