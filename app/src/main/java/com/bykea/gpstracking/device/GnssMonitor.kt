package com.bykea.gpstracking.device

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import com.bykea.gpstracking.domain.model.GnssState
import com.bykea.gpstracking.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Reports satellite signal quality, which shows when the user is indoors or between tall buildings.
 *
 * The signal only counts as weak after 30 s, so a brief dip doesn't change the tracking mode. The
 * result is kept between GPS retries, so a retry still searching for satellites isn't a recovery.
 */
@Singleton
class GnssMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
) {
    private var weakSinceMs: Long? = null
    private var isWeak = false

    /** Only listens while [gpsInUse] is true, as satellites report nothing useful otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun states(gpsInUse: Flow<Boolean>): Flow<GnssState> = gpsInUse
        .distinctUntilChanged()
        .flatMapLatest { inUse -> if (inUse) satelliteUpdates() else emptyFlow() }
        .distinctUntilChanged()

    // Without precise location permission, the catch below means there's simply no GNSS data.
    @SuppressLint("MissingPermission")
    private fun satelliteUpdates(): Flow<GnssState> = callbackFlow {
        val callback = object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                trySend(toState(status))
            }
        }
        val registered = try {
            LocationManagerCompat.registerGnssStatusCallback(
                locationManager,
                ContextCompat.getMainExecutor(context),
                callback,
            )
        } catch (e: SecurityException) {
            AppLog.w(TAG, "GNSS status needs fine location", e)
            false
        }
        awaitClose {
            if (registered) LocationManagerCompat.unregisterGnssStatusCallback(locationManager, callback)
        }
    }

    private fun toState(status: GnssStatusCompat): GnssState {
        var used = 0
        var cn0Sum = 0f
        for (index in 0 until status.satelliteCount) {
            if (status.usedInFix(index)) {
                used++
                cn0Sum += status.getCn0DbHz(index)
            }
        }
        val avgCn0 = if (used > 0) cn0Sum / used else 0f
        updateWeak(used < MIN_SATELLITES || avgCn0 < MIN_CN0_DB_HZ)
        return GnssState(
            satellitesUsedInFix = used,
            satellitesVisible = status.satelliteCount,
            // Whole numbers are enough, and stop noise from registering as a change every second.
            avgCn0DbHzOfUsed = avgCn0.roundToInt().toFloat(),
            isWeak = isWeak,
        )
    }

    private fun updateWeak(isWeakNow: Boolean) {
        if (!isWeakNow) {
            weakSinceMs = null
            isWeak = false
            return
        }
        val now = SystemClock.elapsedRealtime()
        val weakSince = weakSinceMs ?: now
        weakSinceMs = weakSince
        if (now - weakSince >= WEAK_SUSTAINED_MS) isWeak = true
    }

    private companion object {
        const val TAG = "GnssMonitor"
        const val MIN_SATELLITES = 4
        const val MIN_CN0_DB_HZ = 25f
        const val WEAK_SUSTAINED_MS = 30_000L
    }
}
