package com.bykea.gpstracking.ui.permission

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bykea.gpstracking.data.cache.PermissionPrefs
import com.bykea.gpstracking.permission.LocationServicesResolver
import com.bykea.gpstracking.permission.PermissionChecker
import com.bykea.gpstracking.permission.PermissionFlow
import com.bykea.gpstracking.permission.PermissionSnapshot
import com.bykea.gpstracking.permission.PermissionStep
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Feeds [PermissionFlow] with fresh system state and stored answers. The approximate-location
 * dismissal lives only here, so the precise prompt returns on every new launch of the screen.
 */
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val checker: PermissionChecker,
    private val prefs: PermissionPrefs,
    private val locationServicesResolver: LocationServicesResolver,
) : ViewModel() {

    private val snapshot = MutableStateFlow<PermissionSnapshot?>(null)
    private val preciseDismissed = MutableStateFlow(false)

    val step: StateFlow<PermissionStep> =
        combine(snapshot, prefs.memory, preciseDismissed) { current, memory, dismissed ->
            current?.let { PermissionFlow.next(it, memory, dismissed) } ?: PermissionStep.CHECK
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PermissionStep.CHECK)

    /** Re-reads everything. Called on every resume, which is what makes the app keep asking. */
    fun refresh(locationRationale: Boolean) {
        val current = checker.snapshot(locationRationale)
        snapshot.value = current
        if (current.hasLocation) {
            viewModelScope.launch { prefs.clearLocationAnswers() }
        }
    }

    fun onLocationResult(locationRationale: Boolean) {
        val rationaleBefore = snapshot.value?.locationRationale ?: false
        val isGranted = checker.hasLocationPermission()
        if (!isGranted) {
            viewModelScope.launch { prefs.recordLocationDenied(rationaleBefore, locationRationale) }
        }
        refresh(locationRationale)
    }

    fun dismissPrecise() {
        preciseDismissed.value = true
    }

    fun markNotificationsHandled() {
        viewModelScope.launch { prefs.markNotificationsHandled() }
    }

    fun markBackgroundHandled() {
        viewModelScope.launch { prefs.markBackgroundHandled() }
    }

    fun markBatteryOffered() {
        viewModelScope.launch { prefs.markBatteryOffered() }
    }

    suspend fun locationServicesResolution(): IntentSender? = locationServicesResolver.resolution()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
