package com.cybersentinel.domain.repository

import com.cybersentinel.domain.model.AuditedApp
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for the App Permissions Auditor.
 */
interface AppAuditRepository {
    /**
     * Scans all installed packages and returns audited results.
     * Emits progress updates via Flow — each emission is the full growing list.
     * Caller must have QUERY_ALL_PACKAGES or READ_INSTALLED_APPS available.
     */
    fun auditInstalledApps(): Flow<List<AuditedApp>>
}
