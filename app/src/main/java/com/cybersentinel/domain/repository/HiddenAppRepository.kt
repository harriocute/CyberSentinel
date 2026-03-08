package com.cybersentinel.domain.repository

import com.cybersentinel.domain.model.HiddenApp
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for the Hidden App Detector.
 */
interface HiddenAppRepository {
    /**
     * Scans all packages and returns only those flagged as hidden/suspicious.
     * Emits progress updates via a Flow of (scannedSoFar, totalApps, latestFlagged).
     */
    fun scanForHiddenApps(): Flow<HiddenScanProgress>
}

data class HiddenScanProgress(
    val scanned: Int,
    val total: Int,
    val currentPackage: String,
    val flaggedSoFar: List<HiddenApp>
)
