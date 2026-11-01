package com.bykea.gpstracking.di

import android.content.Context
import android.location.LocationManager
import androidx.core.content.getSystemService
import com.bykea.gpstracking.data.source.LocationSource
import com.bykea.gpstracking.data.source.LocationSourceFactory
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [LocationSource] to the implementation that suits this device, chosen once per process. */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideLocationSource(factory: LocationSourceFactory): LocationSource = factory.create()

    @Provides
    fun provideFusedClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager =
        requireNotNull(context.getSystemService()) { "LocationManager unavailable" }
}
