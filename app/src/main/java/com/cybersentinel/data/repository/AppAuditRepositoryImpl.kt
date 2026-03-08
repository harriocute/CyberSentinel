package com.cybersentinel.data.repository

import com.cybersentinel.data.service.AppPermissionAuditorService
import com.cybersentinel.domain.model.AuditedApp
import com.cybersentinel.domain.repository.AppAuditRepository
import kotlinx.coroutines.flow.Flow

/**
 * Concrete implementation of [AppAuditRepository].
 * Delegates all PackageManager work to [AppPermissionAuditorService].
 */
class AppAuditRepositoryImpl(
    private val auditorService: AppPermissionAuditorService
) : AppAuditRepository {

    override fun auditInstalledApps(): Flow<List<AuditedApp>> =
        auditorService.auditInstalledApps()
}
