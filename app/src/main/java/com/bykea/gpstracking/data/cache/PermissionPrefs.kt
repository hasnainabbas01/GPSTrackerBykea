package com.bykea.gpstracking.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.bykea.gpstracking.permission.PermissionFlow
import com.bykea.gpstracking.permission.PermissionMemory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Remembers how the user answered permission prompts. Android offers no way to tell "never asked"
 * from "permanently denied", and optional prompts would otherwise reappear on every launch.
 */
@Singleton
class PermissionPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val memory: Flow<PermissionMemory> = dataStore.safeData()
        .map { prefs ->
            PermissionMemory(
                locationAnswered = prefs[Keys.LOCATION_ANSWERED] ?: false,
                locationDismissed = prefs[Keys.LOCATION_DISMISSED] ?: false,
                notificationsHandled = prefs[Keys.NOTIFICATIONS_HANDLED] ?: false,
                backgroundHandled = prefs[Keys.BACKGROUND_HANDLED] ?: false,
                batteryOffered = prefs[Keys.BATTERY_OFFERED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun recordLocationDenied(rationaleBefore: Boolean, rationaleAfter: Boolean) {
        dataStore.edit { prefs ->
            val updated = PermissionFlow.afterLocationDenied(
                memory = PermissionMemory(
                    locationAnswered = prefs[Keys.LOCATION_ANSWERED] ?: false,
                    locationDismissed = prefs[Keys.LOCATION_DISMISSED] ?: false,
                ),
                rationaleBefore = rationaleBefore,
                rationaleAfter = rationaleAfter,
            )
            prefs[Keys.LOCATION_ANSWERED] = updated.locationAnswered
            prefs[Keys.LOCATION_DISMISSED] = updated.locationDismissed
        }
    }

    /** Called while permission is held, so a later revocation starts from a normal ask. */
    suspend fun clearLocationAnswers() {
        dataStore.edit {
            it.remove(Keys.LOCATION_ANSWERED)
            it.remove(Keys.LOCATION_DISMISSED)
        }
    }

    suspend fun markNotificationsHandled() {
        dataStore.edit { it[Keys.NOTIFICATIONS_HANDLED] = true }
    }

    suspend fun markBackgroundHandled() {
        dataStore.edit { it[Keys.BACKGROUND_HANDLED] = true }
    }

    suspend fun markBatteryOffered() {
        dataStore.edit { it[Keys.BATTERY_OFFERED] = true }
    }

    private object Keys {
        val LOCATION_ANSWERED = booleanPreferencesKey("permission_location_answered")
        val LOCATION_DISMISSED = booleanPreferencesKey("permission_location_dismissed")
        val NOTIFICATIONS_HANDLED = booleanPreferencesKey("permission_notifications_handled")
        val BACKGROUND_HANDLED = booleanPreferencesKey("permission_background_handled")
        val BATTERY_OFFERED = booleanPreferencesKey("battery_optimization_offered")
    }
}
