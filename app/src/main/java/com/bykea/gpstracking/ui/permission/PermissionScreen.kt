package com.bykea.gpstracking.ui.permission

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bykea.gpstracking.R
import com.bykea.gpstracking.permission.PermissionStep
import com.bykea.gpstracking.ui.settings.BatteryOptimizationScreen
import kotlinx.coroutines.launch

/**
 * Binds each [PermissionStep] to its system action. It blocks the tracker UI while location
 * permission is missing, and only the optional steps offer a way past. [locationRationale] is a
 * function because the value must be read after a dialog returns, when it may have changed.
 */
@Composable
fun PermissionScreen(
    step: PermissionStep,
    viewModel: PermissionViewModel,
    locationRationale: () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onLocationResult(locationRationale()) }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.markNotificationsHandled() }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.markBackgroundHandled() }
    // The result needs no handling: the resume that follows re-checks device location.
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { }

    val requestLocation = {
        // Asked together so Android 12+ offers the approximate/precise choice.
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
    val openAppSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            ),
        )
    }

    when (step) {
        PermissionStep.EXPLAIN_LOCATION -> PermissionContent(
            title = R.string.permission_location_title,
            body = R.string.permission_location_body,
            action = R.string.permission_location_action,
            onAction = requestLocation,
        )
        PermissionStep.PERMANENTLY_DENIED -> PermissionContent(
            title = R.string.permission_denied_title,
            body = R.string.permission_denied_body,
            action = R.string.permission_denied_action,
            onAction = openAppSettings,
        )
        PermissionStep.UPGRADE_TO_PRECISE -> PermissionContent(
            title = R.string.permission_precise_title,
            body = R.string.permission_precise_body,
            action = R.string.permission_precise_action,
            onAction = requestLocation,
            skip = R.string.action_continue_approximate,
            onSkip = viewModel::dismissPrecise,
        )
        PermissionStep.NOTIFICATIONS -> PermissionContent(
            title = R.string.permission_notifications_title,
            body = R.string.permission_notifications_body,
            action = R.string.permission_notifications_action,
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            skip = R.string.action_not_now,
            onSkip = viewModel::markNotificationsHandled,
        )
        PermissionStep.EXPLAIN_BACKGROUND -> PermissionContent(
            title = R.string.permission_background_title,
            body = R.string.permission_background_body,
            // Android 11+ only grants this from settings; Android 10 still has a dialog.
            action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                R.string.permission_background_action
            } else {
                R.string.permission_background_action_dialog
            },
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    viewModel.markBackgroundHandled()
                    openAppSettings()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
            skip = R.string.action_not_now,
            onSkip = viewModel::markBackgroundHandled,
        )
        PermissionStep.LOCATION_SERVICES -> PermissionContent(
            title = R.string.permission_services_title,
            body = R.string.permission_services_body,
            action = R.string.permission_services_action,
            onAction = {
                scope.launch {
                    val resolution = viewModel.locationServicesResolution()
                    if (resolution != null) {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(resolution).build())
                    } else {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
            },
        )
        PermissionStep.BATTERY_OPTIMIZATION -> BatteryOptimizationScreen(viewModel::markBatteryOffered)
        PermissionStep.CHECK, PermissionStep.READY -> Unit
    }
}

@Composable
private fun PermissionContent(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
    @StringRes skip: Int? = null,
    onSkip: () -> Unit = {},
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(action))
            }
            if (skip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(skip))
                }
            }
        }
    }
}
