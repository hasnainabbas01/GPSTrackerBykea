package com.bykea.gpstracking.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The user's durable intent to track. Restart paths (sticky restart, boot, watchdog) read this to
 * decide whether tracking should be running at all.
 */
@Singleton
class TrackingPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val isTrackingEnabled: Flow<Boolean> = dataStore.safeData()
        .map { it[TRACKING_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[TRACKING_ENABLED] = enabled }
    }

    private companion object {
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
    }
}
