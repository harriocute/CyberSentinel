package com.cybersentinel.ui.screens.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cybersentinel.data.repository.AppAuditRepositoryImpl
import com.cybersentinel.data.service.AppPermissionAuditorService
import com.cybersentinel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppAuditViewModel(application: Application) : AndroidViewModel(application) {

    // Manual DI — swap for Hilt in production
    private val repository = AppAuditRepositoryImpl(
        AppPermissionAuditorService(application.applicationContext)
    )

    private val _auditState = MutableStateFlow<AppAuditState>(AppAuditState.Idle)
    val auditState: StateFlow<AppAuditState> = _auditState.asStateFlow()

    private val _activeFilter = MutableStateFlow(AuditFilter.ALL)
    val activeFilter: StateFlow<AuditFilter> = _activeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Filtered list derived from the raw audit result + active filter + search */
    val filteredApps: StateFlow<List<AuditedApp>> = combine(
        _auditState, _activeFilter, _searchQuery
    ) { state, filter, query ->
        val all = (state as? AppAuditState.Success)?.apps ?: return@combine emptyList()
        all
            .filter { app -> matchesFilter(app, filter) }
            .filter { app ->
                query.isBlank() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Actions ────────────────────────────────────────────────────────────

    fun startAudit() {
        viewModelScope.launch {
            _auditState.value = AppAuditState.Loading

            repository.auditInstalledApps()
                .catch { e ->
                    _auditState.value = AppAuditState.Error(e.message ?: "Audit failed")
                }
                .collect { apps ->
                    // Intermediate + final emissions both update state
                    _auditState.value = AppAuditState.Success(apps)
                }
        }
    }

    fun setFilter(filter: AuditFilter) { _activeFilter.value = filter }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun reset() { _auditState.value = AppAuditState.Idle }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun matchesFilter(app: AuditedApp, filter: AuditFilter): Boolean = when (filter) {
        AuditFilter.ALL            -> true
        AuditFilter.HIGH_RISK      -> app.riskLevel.priority >= AppRiskLevel.HIGH.priority
        AuditFilter.FLAGGED        -> app.flaggedPermissions.isNotEmpty()
        AuditFilter.USER_INSTALLED -> !app.isSystemApp
        AuditFilter.SYSTEM         -> app.isSystemApp
    }

    fun getSummary(apps: List<AuditedApp>): AuditSummary {
        return AuditSummary(
            total    = apps.size,
            critical = apps.count { it.riskLevel == AppRiskLevel.CRITICAL },
            high     = apps.count { it.riskLevel == AppRiskLevel.HIGH },
            flagged  = apps.count { it.flaggedPermissions.isNotEmpty() },
            safe     = apps.count { it.riskLevel == AppRiskLevel.SAFE }
        )
    }
}

data class AuditSummary(
    val total: Int,
    val critical: Int,
    val high: Int,
    val flagged: Int,
    val safe: Int
)
