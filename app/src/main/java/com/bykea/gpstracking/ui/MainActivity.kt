package com.bykea.gpstracking.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bykea.gpstracking.permission.PermissionStep
import com.bykea.gpstracking.ui.permission.PermissionScreen
import com.bykea.gpstracking.ui.permission.PermissionViewModel
import com.bykea.gpstracking.ui.settings.BatteryOptimizationScreen
import com.bykea.gpstracking.ui.theme.GPSTrackinBykeaTheme
import com.bykea.gpstracking.ui.tracker.TrackerScreen
import com.bykea.gpstracking.ui.tracker.TrackerViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity that gates the tracker behind the permission flow. Permissions are re-checked on
 * every resume, because the user can change them in system settings while the app is open.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionViewModel: PermissionViewModel by viewModels()
    private val trackerViewModel: TrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPSTrackinBykeaTheme {
                val step by permissionViewModel.step.collectAsStateWithLifecycle()
                var showBatterySettings by rememberSaveable { mutableStateOf(false) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissionViewModel.refresh(hasLocationRationale())
                }
                when (step) {
                    PermissionStep.CHECK -> Unit
                    PermissionStep.READY -> if (showBatterySettings) {
                        BatteryOptimizationScreen(onDone = { showBatterySettings = false })
                    } else {
                        TrackerScreen(
                            viewModel = trackerViewModel,
                            onOpenBatterySettings = { showBatterySettings = true },
                        )
                    }
                    else -> PermissionScreen(
                        step = step,
                        viewModel = permissionViewModel,
                        locationRationale = ::hasLocationRationale,
                    )
                }
            }
        }
    }

    private fun hasLocationRationale(): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
}
