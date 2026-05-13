package com.netmon.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmon.domain.AppTrafficStats
import com.netmon.domain.DashboardUiState
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

    init {
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

        viewModelScope.launch {
            repository.observeDnsQueries().collect { queries ->
                _uiState.update { it.copy(dnsQueries = queries) }
            }
        }

        // Periodic monitoring state refresh
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(isMonitoring = VpnCaptureService.isRunning) }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun toggleMonitoring() {
        // Activity must handle VPN permission via startMonitoringWithPermission()
    }

    fun startMonitoring(activity: Activity) {
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, REQUEST_VPN_PERMISSION)
        } else {
            // Already prepared
            activity.startService(Intent(activity, VpnCaptureService::class.java))
            _uiState.update { it.copy(isMonitoring = true) }
        }
    }

    fun onVpnPermissionResult(resultCode: Int, activity: Activity) {
        if (resultCode == Activity.RESULT_OK) {
            activity.startService(Intent(activity, VpnCaptureService::class.java))
            _uiState.update { it.copy(isMonitoring = true) }
        } else {
            _uiState.update { it.copy(error = "VPN permission denied") }
        }
    }

    fun stopMonitoring(activity: Activity) {
        val intent = Intent(activity, VpnCaptureService::class.java).apply {
            action = "STOP"
        }
        activity.startService(intent)
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