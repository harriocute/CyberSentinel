package com.cybersentinel.data.repository

import com.cybersentinel.data.service.HiddenAppScannerService
import com.cybersentinel.domain.repository.HiddenAppRepository
import com.cybersentinel.domain.repository.HiddenScanProgress
import kotlinx.coroutines.flow.Flow

class HiddenAppRepositoryImpl(
    private val scannerService: HiddenAppScannerService
) : HiddenAppRepository {

    override fun scanForHiddenApps(): Flow<HiddenScanProgress> =
        scannerService.scanForHiddenApps()
}
