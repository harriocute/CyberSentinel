package com.cybersentinel.domain.model

/**
 * Represents a scanned WiFi network with its security assessment.
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,          // dBm
    val securityType: WifiSecurityType,
    val isHiddenSsid: Boolean,
    val frequency: Int,               // MHz
    val channel: Int,
    val riskLevel: RiskLevel,
    val riskReasons: List<String>
)

enum class WifiSecurityType(val displayName: String) {
    WPA3("WPA3"),
    WPA2("WPA2"),
    WPA("WPA"),
    WEP("WEP (Insecure)"),
    OPEN("Open Network"),
    UNKNOWN("Unknown")
}

enum class RiskLevel(val label: String, val priority: Int) {
    SAFE("Safe", 0),
    LOW("Low Risk", 1),
    MEDIUM("Medium Risk", 2),
    HIGH("High Risk", 3),
    CRITICAL("Critical", 4)
}

sealed class WifiScanState {
    object Idle       : WifiScanState()
    object Scanning   : WifiScanState()
    data class Success(val networks: List<WifiNetwork>) : WifiScanState()
    data class Error(val message: String)               : WifiScanState()
    object PermissionRequired                           : WifiScanState()
}
