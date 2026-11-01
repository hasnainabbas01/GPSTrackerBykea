package com.bykea.gpstracking.data.source

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that decides between GMS and platform code paths. The implementations are lazy: the
 * fused client is not even created on a device without Play Services.
 */
@Singleton
class LocationSourceFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fused: Lazy<FusedLocationSource>,
    private val platform: Lazy<PlatformLocationSource>,
) {
    val isGmsAvailable: Boolean by lazy {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    fun create(): LocationSource = if (isGmsAvailable) fused.get() else platform.get()
}
