package com.bykea.gpstracking.data.cache

import android.location.Location
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bykea.gpstracking.domain.model.TrackedLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The last good location, so the app has something to show straight after launch. Writes are
 * throttled: at a 5 s update rate, persisting every fix would wake the flash storage for no visible
 * benefit.
 */
@Singleton
class LocationCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val saveLock = Mutex()
    private var lastSaved: TrackedLocation? = null

    /** The last persisted fix, always marked as cached. */
    val lastLocation: Flow<TrackedLocation?> = dataStore.safeData()
        .map { it.toTrackedLocation() }
        .distinctUntilChanged()

    /**
     * Persists [location] if 15 s passed or it moved more than 20 m since the last write. Use
     * [force] when tracking stops, so the final position is not lost to the throttle.
     */
    suspend fun saveLocation(location: TrackedLocation, force: Boolean = false) {
        saveLock.withLock {
            if (!force && !shouldPersist(location)) return
            dataStore.edit { it.putLocation(location) }
            lastSaved = location
        }
    }

    private fun shouldPersist(location: TrackedLocation): Boolean {
        val previous = lastSaved ?: return true
        val elapsedNanos = location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
        return elapsedNanos >= SAVE_INTERVAL_NANOS || distanceMeters(previous, location) > SAVE_DISTANCE_M
    }

    private fun distanceMeters(from: TrackedLocation, to: TrackedLocation): Float {
        val result = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, result)
        return result[0]
    }

    private fun MutablePreferences.putLocation(location: TrackedLocation) {
        this[Keys.LATITUDE] = location.latitude
        this[Keys.LONGITUDE] = location.longitude
        this[Keys.ACCURACY] = location.accuracyMeters
        this[Keys.PROVIDER] = location.provider
        this[Keys.ELAPSED_NANOS] = location.elapsedRealtimeNanos
        this[Keys.WALL_CLOCK] = location.wallClockTime
        this[Keys.LOW_CONFIDENCE] = location.lowConfidence
        putOrRemove(Keys.ALTITUDE, location.altitude)
        putOrRemove(Keys.SPEED, location.speed)
        putOrRemove(Keys.BEARING, location.bearing)
    }

    private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value else remove(key)
    }

    private fun Preferences.toTrackedLocation(): TrackedLocation? = TrackedLocation(
        latitude = this[Keys.LATITUDE] ?: return null,
        longitude = this[Keys.LONGITUDE] ?: return null,
        accuracyMeters = this[Keys.ACCURACY] ?: return null,
        altitude = this[Keys.ALTITUDE],
        speed = this[Keys.SPEED],
        bearing = this[Keys.BEARING],
        provider = this[Keys.PROVIDER] ?: return null,
        elapsedRealtimeNanos = this[Keys.ELAPSED_NANOS] ?: return null,
        wallClockTime = this[Keys.WALL_CLOCK] ?: return null,
        lowConfidence = this[Keys.LOW_CONFIDENCE] ?: false,
        isCached = true,
    )

    private object Keys {
        val LATITUDE = doublePreferencesKey("location_latitude")
        val LONGITUDE = doublePreferencesKey("location_longitude")
        val ACCURACY = floatPreferencesKey("location_accuracy")
        val ALTITUDE = doublePreferencesKey("location_altitude")
        val SPEED = floatPreferencesKey("location_speed")
        val BEARING = floatPreferencesKey("location_bearing")
        val PROVIDER = stringPreferencesKey("location_provider")
        val ELAPSED_NANOS = longPreferencesKey("location_elapsed_nanos")
        val WALL_CLOCK = longPreferencesKey("location_wall_clock")
        val LOW_CONFIDENCE = booleanPreferencesKey("location_low_confidence")
    }

    private companion object {
        const val SAVE_INTERVAL_NANOS = 15_000_000_000L
        const val SAVE_DISTANCE_M = 20f
    }
}
