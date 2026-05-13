package com.netmon.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmon.domain.AppTrafficStats
import com.netmon.domain.DashboardUiState
import com.netmon.domain.PacketEvent
import com.netmon.domain.TrafficRepository
import com.netmon.vpn.VpnCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TrafficRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activityRef: Activity? = null

    fun setActivity(activity: Activity) {
        activityRef = activity
    }

    init {
        // Observe live traffic from FlowTracker
        viewModelScope.launch {
            repository.observeLiveTraffic().collect { apps ->
                _uiState.update { state ->
                    state.copy(
                        apps = apps,
                        activeAppCount = apps.size,
                        totalRxBytes = apps.sumOf { it.totalRxBytes },
                        totalTxBytes = apps.sumOf { it.totalTxBytes },
                        isMonitoring = VpnCaptureService.isRunning
                    )
                }
            }
        }

        // Observe DNS queries
        viewModelScope.launch {
            repository.observeDnsQueries().collect { queries ->
                _uiState.update { it.copy(dnsQueries = queries) }
            }
        }
    }

    fun toggleMonitoring() {
        if (VpnCaptureService.isRunning) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        val activity = activityRef ?: return
        val intent = VpnService.prepare(activity) ?: run {
            // Already prepared, start directly
            activity.startService(Intent(activity, VpnCaptureService::class.java))
            _uiState.update { it.copy(isMonitoring = true) }
            return
        }
        // Need VPN permission
        activity.startActivityForResult(intent, REQUEST_VPN_PERMISSION)
    }

    fun onVpnPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            activityRef?.let { activity ->
                activity.startService(Intent(activity, VpnCaptureService::class.java))
                _uiState.update { it.copy(isMonitoring = true) }
            }
        } else {
            _uiState.update { it.copy(error = "VPN permission denied") }
        }
    }

    private fun stopMonitoring() {
        activityRef?.let { activity ->
            val intent = Intent(activity, VpnCaptureService::class.java).apply {
                action = "STOP"
            }
            activity.startService(intent)
        }
        _uiState.update { it.copy(isMonitoring = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    companion object {
        const val REQUEST_VPN_PERMISSION = 100
    }
}