package com.cybersentinel.data.repository

import com.cybersentinel.data.service.WifiScannerService
import com.cybersentinel.domain.model.WifiNetwork
import com.cybersentinel.domain.repository.WifiRepository
import kotlinx.coroutines.flow.Flow

/**
 * Concrete implementation of [WifiRepository].
 * Delegates to [WifiScannerService] for actual Android API calls.
 *
 * In a Hilt-based project, annotate with @Singleton and @Inject constructor.
 */
class WifiRepositoryImpl(
    private val scannerService: WifiScannerService
) : WifiRepository {

    override fun scanNetworks(): Flow<List<WifiNetwork>> =
        scannerService.scanNetworks()

    override suspend fun getConnectedNetwork(): WifiNetwork? =
        scannerService.getConnectedNetwork()
}
