package com.cybersentinel.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.cybersentinel.domain.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Low-level WiFi scanning service.
 * Wraps Android's WifiManager with coroutine-friendly APIs.
 *
 * ⚠️ Requires ACCESS_FINE_LOCATION (+ CHANGE_WIFI_STATE on API 28+)
 */
class WifiScannerService(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Initiates a fresh scan and emits the resulting list of assessed networks.
     * Uses a BroadcastReceiver to capture SCAN_RESULTS_AVAILABLE_ACTION.
     */
    fun scanNetworks(): Flow<List<WifiNetwork>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    val results = if (success || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        @Suppress("DEPRECATION")
                        wifiManager.scanResults ?: emptyList()
                    } else {
                        @Suppress("DEPRECATION")
                        wifiManager.scanResults ?: emptyList()
                    }
                    launch {
                        send(results.map { it.toWifiNetwork() })
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)

        // Trigger the scan
        @Suppress("DEPRECATION")
        val scanStarted = wifiManager.startScan()
        if (!scanStarted) {
            // Fall back to cached results
            @Suppress("DEPRECATION")
            val cached = wifiManager.scanResults?.map { it.toWifiNetwork() } ?: emptyList()
            send(cached)
        }

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Returns the currently connected network by parsing WifiInfo.
     */
    fun getConnectedNetwork(): WifiNetwork? {
        @Suppress("DEPRECATION")
        val wifiInfo: WifiInfo = wifiManager.connectionInfo ?: return null
        val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return null
        if (ssid == "<unknown ssid>" || ssid.isBlank()) return null

        @Suppress("DEPRECATION")
        val scanResults = wifiManager.scanResults ?: emptyList()
        val matchedScan = scanResults.firstOrNull {
            it.BSSID == wifiInfo.bssid
        }

        return matchedScan?.toWifiNetwork() ?: WifiNetwork(
            ssid            = ssid,
            bssid           = wifiInfo.bssid ?: "",
            signalStrength  = wifiInfo.rssi,
            securityType    = WifiSecurityType.UNKNOWN,
            isHiddenSsid    = ssid.isBlank(),
            frequency       = wifiInfo.frequency,
            channel         = frequencyToChannel(wifiInfo.frequency),
            riskLevel       = RiskLevel.LOW,
            riskReasons     = listOf("Security type could not be determined")
        )
    }

    // ── Mapping & Risk Assessment ──────────────────────────────────────────

    private fun ScanResult.toWifiNetwork(): WifiNetwork {
        val secType   = detectSecurityType(this)
        val isHidden  = SSID.isNullOrBlank()
        val reasons   = mutableListOf<String>()
        val risk      = assessRisk(secType, isHidden, reasons)

        return WifiNetwork(
            ssid           = if (isHidden) "[Hidden Network]" else SSID,
            bssid          = BSSID ?: "",
            signalStrength = level,
            securityType   = secType,
            isHiddenSsid   = isHidden,
            frequency      = frequency,
            channel        = frequencyToChannel(frequency),
            riskLevel      = risk,
            riskReasons    = reasons.toList()
        )
    }

    /**
     * Parses the capabilities string to determine the WiFi security protocol.
     * WPA3 → SAE tag, WPA2 → WPA2/RSN, WPA1 → WPA tag, WEP, or Open.
     */
    private fun detectSecurityType(result: ScanResult): WifiSecurityType {
        val cap = result.capabilities?.uppercase() ?: return WifiSecurityType.UNKNOWN
        return when {
            cap.contains("SAE")  -> WifiSecurityType.WPA3
            cap.contains("WPA2") || cap.contains("RSN") -> WifiSecurityType.WPA2
            cap.contains("WPA")  -> WifiSecurityType.WPA
            cap.contains("WEP")  -> WifiSecurityType.WEP
            cap == "[]" || cap.isBlank() -> WifiSecurityType.OPEN
            else                 -> WifiSecurityType.UNKNOWN
        }
    }

    /**
     * Core risk assessment logic.
     *  • CRITICAL  → Open network  (no encryption whatsoever)
     *  • HIGH      → WEP          (crackable in minutes)
     *  • HIGH      → Hidden SSID  (security through obscurity + active probing risk)
     *  • MEDIUM    → WPA (v1)     (TKIP vulnerabilities)
     *  • LOW       → WPA2         (solid but not WPA3)
     *  • SAFE      → WPA3
     */
    private fun assessRisk(
        type: WifiSecurityType,
        isHidden: Boolean,
        reasons: MutableList<String>
    ): RiskLevel {
        var risk = when (type) {
            WifiSecurityType.OPEN    -> { reasons.add("No encryption – traffic is visible to anyone nearby"); RiskLevel.CRITICAL }
            WifiSecurityType.WEP     -> { reasons.add("WEP encryption is broken and trivially crackable"); RiskLevel.HIGH }
            WifiSecurityType.WPA     -> { reasons.add("WPA (TKIP) has known vulnerabilities; upgrade to WPA2/3"); RiskLevel.MEDIUM }
            WifiSecurityType.WPA2    -> RiskLevel.LOW
            WifiSecurityType.WPA3    -> RiskLevel.SAFE
            WifiSecurityType.UNKNOWN -> { reasons.add("Security type could not be determined"); RiskLevel.MEDIUM }
        }

        if (isHidden) {
            reasons.add("Hidden SSID forces clients to broadcast probe requests, revealing your device")
            // Escalate if not already HIGH or above
            if (risk.priority < RiskLevel.HIGH.priority) risk = RiskLevel.HIGH
        }

        return risk
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        else               -> 0
    }
}
