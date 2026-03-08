package com.cybersentinel.domain.repository

import com.cybersentinel.domain.model.WifiNetwork
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for WiFi scanning.
 * The data layer provides the concrete implementation.
 */
interface WifiRepository {
    /**
     * Triggers a WiFi scan and emits results as a Flow.
     * Caller must have ACCESS_FINE_LOCATION granted before calling.
     */
    fun scanNetworks(): Flow<List<WifiNetwork>>

    /**
     * Returns the currently connected network, if any.
     */
    suspend fun getConnectedNetwork(): WifiNetwork?
}
