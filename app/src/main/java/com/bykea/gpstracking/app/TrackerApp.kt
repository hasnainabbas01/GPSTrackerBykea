package com.bykea.gpstracking.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bykea.gpstracking.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt root, and the provider of a WorkManager configuration that can inject workers. `onCreate` also
 * runs for background restarts with no UI, so it must stay light.
 */
@HiltAndroidApp
class TrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        NotificationChannels.create(this)
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedRegistrationObjects().detectLeakedClosableObjects()
                .penaltyLog().build(),
        )
    }
}
