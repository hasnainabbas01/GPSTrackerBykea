package com.bykea.gpstracking.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** A failed disk read means "nothing cached", which must not crash a long-running tracker. */
internal fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}
