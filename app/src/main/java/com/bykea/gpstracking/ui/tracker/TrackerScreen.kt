package com.bykea.gpstracking.ui.tracker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bykea.gpstracking.R
import com.bykea.gpstracking.domain.model.LocationState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** Text-only view of the tracker. Every row merges label and value for screen readers. */
@Composable
fun TrackerScreen(viewModel: TrackerViewModel, onOpenBatterySettings: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusHeader(uiState.location)
            Spacer(Modifier.height(8.dp))
            LocationRows(uiState.location)
            Spacer(Modifier.height(16.dp))
            if (uiState.startNotAllowed) {
                Text(
                    text = stringResource(R.string.error_start_not_allowed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (uiState.location.trackingEnabled) {
                OutlinedButton(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_stop_tracking))
                }
            } else {
                Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_start_tracking))
                }
            }
            TextButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_battery_settings))
            }
        }
    }
}

@Composable
private fun StatusHeader(state: LocationState) {
    val age = ageText(state.ageMillis)
    val (text, description) = when {
        state.isLive -> stringResource(R.string.tracker_live) to
            stringResource(R.string.tracker_live_description)
        state.location != null -> stringResource(R.string.tracker_cached, age) to
            stringResource(R.string.tracker_cached_description, age)
        else -> stringResource(R.string.tracker_no_location) to
            stringResource(R.string.tracker_no_location_description)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun LocationRows(state: LocationState) {
    val unknown = stringResource(R.string.value_unknown)
    val location = state.location
    InfoRow(R.string.label_latitude, location?.let { coordinate(it.latitude) } ?: unknown)
    InfoRow(R.string.label_longitude, location?.let { coordinate(it.longitude) } ?: unknown)
    InfoRow(
        R.string.label_accuracy,
        when {
            location == null -> unknown
            location.lowConfidence -> stringResource(
                R.string.value_accuracy_low_confidence,
                location.accuracyMeters.roundToInt(),
            )
            else -> stringResource(R.string.value_accuracy, location.accuracyMeters.roundToInt())
        },
    )
    InfoRow(R.string.label_provider, location?.provider ?: unknown)
    InfoRow(R.string.label_updated, location?.let { clockTime(it.wallClockTime) } ?: unknown)
    InfoRow(R.string.label_mode, state.mode?.name ?: unknown)
    InfoRow(R.string.label_motion, state.motion.name)
    InfoRow(
        R.string.label_gnss,
        when {
            state.gnss.isWeak -> stringResource(R.string.value_gnss_weak)
            state.gnss.satellitesUsedInFix > 0 -> pluralStringResource(
                R.plurals.value_gnss_good,
                state.gnss.satellitesUsedInFix,
                state.gnss.satellitesUsedInFix,
            )
            else -> unknown
        },
    )
    InfoRow(
        R.string.label_battery,
        state.battery?.let {
            stringResource(
                if (it.isPowerSaveMode) {
                    R.string.value_battery_saver_on
                } else {
                    R.string.value_battery_saver_off
                },
                it.levelPercent,
            )
        } ?: unknown,
    )
}

@Composable
private fun InfoRow(@StringRes label: Int, value: String) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) { }) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ageText(ageMillis: Long): String {
    val seconds = ageMillis / MS_PER_SECOND
    return when {
        seconds < SECONDS_PER_MINUTE -> stringResource(R.string.age_seconds, seconds)
        seconds < SECONDS_PER_HOUR ->
            stringResource(R.string.age_minutes, seconds / SECONDS_PER_MINUTE)
        else -> stringResource(R.string.age_hours, seconds / SECONDS_PER_HOUR)
    }
}

private fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun clockTime(wallClockMs: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(wallClockMs))
