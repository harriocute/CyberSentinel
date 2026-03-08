package com.cybersentinel.ui.screens.wifi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cybersentinel.data.repository.WifiRepositoryImpl
import com.cybersentinel.data.service.WifiScannerService
import com.cybersentinel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    // Manual DI (replace with Hilt in production)
    private val repository = WifiRepositoryImpl(
        WifiScannerService(application.applicationContext)
    )

    private val _scanState = MutableStateFlow<WifiScanState>(WifiScanState.Idle)
    val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()

    private val _connectedNetwork = MutableStateFlow<WifiNetwork?>(null)
    val connectedNetwork: StateFlow<WifiNetwork?> = _connectedNetwork.asStateFlow()

    // ── Actions ────────────────────────────────────────────────────────────

    fun startScan() {
        viewModelScope.launch {
            _scanState.value = WifiScanState.Scanning

            // Also grab connected network at scan time
            _connectedNetwork.value = repository.getConnectedNetwork()

            repository.scanNetworks()
                .catch { e ->
                    _scanState.value = WifiScanState.Error(
                        e.message ?: "Unknown scan error"
                    )
                }
                .collect { networks ->
                    val sorted = networks.sortedByDescending { it.riskLevel.priority }
                    _scanState.value = WifiScanState.Success(sorted)
                }
        }
    }

    fun onPermissionDenied() {
        _scanState.value = WifiScanState.PermissionRequired
    }

    fun reset() {
        _scanState.value = WifiScanState.Idle
    }

    // ── Derived helpers ────────────────────────────────────────────────────

    fun getRiskSummary(networks: List<WifiNetwork>): RiskSummary {
        val critical = networks.count { it.riskLevel == RiskLevel.CRITICAL }
        val high     = networks.count { it.riskLevel == RiskLevel.HIGH }
        val medium   = networks.count { it.riskLevel == RiskLevel.MEDIUM }
        val safe     = networks.count { it.riskLevel == RiskLevel.SAFE || it.riskLevel == RiskLevel.LOW }
        return RiskSummary(critical, high, medium, safe, networks.size)
    }
}

data class RiskSummary(
    val critical: Int,
    val high: Int,
    val medium: Int,
    val safe: Int,
    val total: Int
)
