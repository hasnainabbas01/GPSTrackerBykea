package com.bykea.gpstracking.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bykea.gpstracking.data.LocationRepository
import com.bykea.gpstracking.device.BatteryMonitor
import com.bykea.gpstracking.domain.model.LocationState
import com.bykea.gpstracking.service.StartResult
import com.bykea.gpstracking.service.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the tracker screen renders. */
data class TrackerUiState(
    val location: LocationState = LocationState(),
    val startNotAllowed: Boolean = false,
)

/**
 * Turns repository state into screen state. The one-second clock that keeps the age text and LIVE
 * label current only runs while the screen is collecting, so it costs nothing in the background.
 */
@HiltViewModel
class TrackerViewModel @Inject constructor(
    repository: LocationRepository,
    batteryMonitor: BatteryMonitor,
    private val controller: TrackingController,
) : ViewModel() {

    private val startNotAllowed = MutableStateFlow(false)

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_INTERVAL_MS)
        }
    }

    val uiState: StateFlow<TrackerUiState> =
        combine(
            repository.state,
            batteryMonitor.states(),
            clock,
            startNotAllowed,
        ) { state, battery, now, notAllowed ->
            TrackerUiState(state.copy(battery = battery).refreshedAt(now), notAllowed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TrackerUiState())

    /** Restarts tracking that should be running, for example after permission was regranted. */
    fun onResume() {
        viewModelScope.launch {
            startNotAllowed.value = controller.resumeIfEnabled() == StartResult.NOT_ALLOWED
        }
    }

    fun start() {
        viewModelScope.launch {
            startNotAllowed.value = controller.start() == StartResult.NOT_ALLOWED
        }
    }

    fun stop() {
        startNotAllowed.value = false
        viewModelScope.launch { controller.stop() }
    }

    private companion object {
        const val CLOCK_INTERVAL_MS = 1_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
