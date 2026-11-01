package com.bykea.gpstracking.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.bykea.gpstracking.domain.policy.TrackingConfig
import com.bykea.gpstracking.domain.policy.TrackingPriority
import com.bykea.gpstracking.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Source for devices without Play Services, such as Huawei. Uses the platform fused provider on
 * API 31+ if the device has one; otherwise it drives GPS and network itself and merges them with
 * [BestFixSelector], using the priority to decide how much of the costly GPS radio to use.
 */
// Permission is checked by callers; a revocation race surfaces as SecurityException, handled here.
@SuppressLint("MissingPermission")
class PlatformLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
) : LocationSource {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun locationUpdates(config: TrackingConfig): Flow<Location> = callbackFlow {
        val selector = BestFixSelector()
        val listeners = mutableListOf<LocationListenerCompat>()
        try {
            providerRequests(config).forEach { (provider, request) ->
                val listener = LocationListenerCompat { location ->
                    if (selector.offer(location)) trySend(location)
                }
                LocationManagerCompat.requestLocationUpdates(
                    locationManager, provider, request, executor, listener,
                )
                listeners += listener
            }
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose {
            listeners.forEach { LocationManagerCompat.removeUpdates(locationManager, it) }
        }
    }

    override suspend fun currentLocation(priority: TrackingPriority): Location? {
        // Asking for a passive fix must not power anything up.
        if (priority == TrackingPriority.PASSIVE) return lastKnownLocation()
        for (provider in providersFor(priority)) {
            oneShot(provider)?.let { return it }
        }
        return null
    }

    override suspend fun lastKnownLocation(): Location? {
        val selector = BestFixSelector()
        var best: Location? = null
        for (provider in locationManager.allProviders) {
            try {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (selector.offer(location)) best = location
            } catch (e: SecurityException) {
                AppLog.w(TAG, "lastKnownLocation($provider): permission missing", e)
            }
        }
        return best
    }

    private suspend fun oneShot(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, signal, executor,
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (e: SecurityException) {
                AppLog.w(TAG, "currentLocation($provider): permission missing", e)
                continuation.resume(null)
            }
        }

    private fun providerRequests(config: TrackingConfig): List<Pair<String, LocationRequestCompat>> {
        val request = config.toRequest(config.priority.toQuality())
        return providersFor(config.priority).map { provider ->
            val isSecondaryGps = config.priority == TrackingPriority.BALANCED &&
                provider == LocationManager.GPS_PROVIDER
            provider to if (isSecondaryGps) config.toSlowGpsRequest() else request
        }
    }

    /** Usable providers for [priority], most useful first. Never empty: passive is the floor. */
    private fun providersFor(priority: TrackingPriority): List<String> {
        val wanted = when {
            priority == TrackingPriority.PASSIVE -> listOf(LocationManager.PASSIVE_PROVIDER)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                isUsable(LocationManager.FUSED_PROVIDER) -> listOf(LocationManager.FUSED_PROVIDER)
            priority == TrackingPriority.HIGH_ACCURACY ->
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            priority == TrackingPriority.BALANCED ->
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            else -> listOf(LocationManager.NETWORK_PROVIDER)
        }
        return wanted.filter(::isUsable).ifEmpty { listOf(LocationManager.PASSIVE_PROVIDER) }
    }

    private fun isUsable(provider: String): Boolean =
        provider in locationManager.allProviders && locationManager.isProviderEnabled(provider)

    private fun TrackingConfig.toRequest(quality: Int): LocationRequestCompat =
        LocationRequestCompat.Builder(intervalMs)
            .setQuality(quality)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setMaxUpdateDelayMillis(maxUpdateDelayMs)
            .build()

    /** Balanced mode leans on the network provider; GPS only corrects it now and then. */
    private fun TrackingConfig.toSlowGpsRequest(): LocationRequestCompat {
        val slowInterval = maxOf(intervalMs * SLOW_GPS_FACTOR, SLOW_GPS_MIN_INTERVAL_MS)
        return copy(intervalMs = slowInterval, minIntervalMs = slowInterval)
            .toRequest(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
    }

    private fun TrackingPriority.toQuality(): Int = when (this) {
        TrackingPriority.HIGH_ACCURACY -> LocationRequestCompat.QUALITY_HIGH_ACCURACY
        TrackingPriority.BALANCED -> LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
        TrackingPriority.LOW_POWER, TrackingPriority.PASSIVE -> LocationRequestCompat.QUALITY_LOW_POWER
    }

    private companion object {
        const val TAG = "PlatformLocationSource"
        const val SLOW_GPS_FACTOR = 4L
        const val SLOW_GPS_MIN_INTERVAL_MS = 2 * 60 * 1000L
    }
}
