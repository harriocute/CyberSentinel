package com.cybersentinel.ui.screens.hidden

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cybersentinel.data.repository.HiddenAppRepositoryImpl
import com.cybersentinel.data.service.HiddenAppScannerService
import com.cybersentinel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HiddenAppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HiddenAppRepositoryImpl(
        HiddenAppScannerService(application.applicationContext)
    )

    private val _scanState = MutableStateFlow<HiddenScanState>(HiddenScanState.Idle)
    val scanState: StateFlow<HiddenScanState> = _scanState.asStateFlow()

    private val _activeFilter = MutableStateFlow(HiddenFilter.ALL)
    val activeFilter: StateFlow<HiddenFilter> = _activeFilter.asStateFlow()

    /** Filtered view over the success results */
    val filteredApps: StateFlow<List<HiddenApp>> = combine(_scanState, _activeFilter) { state, filter ->
        val apps = (state as? HiddenScanState.Success)?.hidden ?: return@combine emptyList()
        when (filter) {
            HiddenFilter.ALL        -> apps
            HiddenFilter.DANGEROUS  -> apps.filter { it.threatLevel == HiddenThreatLevel.DANGEROUS }
            HiddenFilter.NO_ICON    -> apps.filter { DetectionMethod.NO_LAUNCHER_ICON in it.detectionMethod }
            HiddenFilter.DEVICE_ADMIN -> apps.filter { DetectionMethod.DEVICE_ADMIN in it.detectionMethod }
            HiddenFilter.SIDELOADED -> apps.filter { DetectionMethod.SUSPICIOUS_INSTALL_SOURCE in it.detectionMethod }
            HiddenFilter.USER_ONLY  -> apps.filter { !it.isSystemApp }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var scanStartTime = 0L

    // ── Actions ────────────────────────────────────────────────────────────

    fun startScan() {
        scanStartTime = System.currentTimeMillis()
        viewModelScope.launch {
            repository.scanForHiddenApps()
                .catch { e -> _scanState.value = HiddenScanState.Error(e.message ?: "Scan failed") }
                .collect { progress ->
                    val pct = if (progress.total > 0) progress.scanned / progress.total.toFloat() else 0f

                    if (progress.scanned < progress.total) {
                        _scanState.value = HiddenScanState.Scanning(
                            progress = pct,
                            current  = progress.currentPackage
                        )
                    } else {
                        _scanState.value = HiddenScanState.Success(
                            hidden         = progress.flaggedSoFar,
                            totalScanned   = progress.total,
                            scanDurationMs = System.currentTimeMillis() - scanStartTime
                        )
                    }
                }
        }
    }

    fun setFilter(filter: HiddenFilter) { _activeFilter.value = filter }
    fun reset() { _scanState.value = HiddenScanState.Idle }
}

enum class HiddenFilter(val label: String) {
    ALL("All Findings"),
    DANGEROUS("Dangerous"),
    NO_ICON("No Icon"),
    DEVICE_ADMIN("Device Admin"),
    SIDELOADED("Sideloaded"),
    USER_ONLY("User Apps")
}
