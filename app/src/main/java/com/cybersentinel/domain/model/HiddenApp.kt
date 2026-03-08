package com.cybersentinel.domain.model

/**
 * Represents a package flagged by the Hidden App Detector.
 */
data class HiddenApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val hiddenReasons: List<HiddenReason>,
    val threatLevel: HiddenThreatLevel,
    val detectionMethod: List<DetectionMethod>
)

/**
 * The specific reason an app was classified as hidden/suspicious.
 */
data class HiddenReason(
    val title: String,
    val detail: String,
    val severity: HiddenSeverity
)

enum class HiddenSeverity(val label: String) {
    INFO("Info"),
    SUSPICIOUS("Suspicious"),
    DANGEROUS("Dangerous")
}

enum class HiddenThreatLevel(val label: String, val priority: Int) {
    CLEAN("Clean", 0),
    SUSPICIOUS("Suspicious", 1),
    LIKELY_HIDDEN("Likely Hidden", 2),
    DANGEROUS("Dangerous", 3)
}

/**
 * Which detection technique flagged the app.
 */
enum class DetectionMethod(val label: String, val description: String) {
    NO_LAUNCHER_ICON(
        "No Launcher Icon",
        "App has no CATEGORY_LAUNCHER intent — won't appear in app drawer"
    ),
    COMPONENT_DISABLED(
        "Component Disabled",
        "Main activity component is explicitly disabled via PackageManager"
    ),
    EMPTY_PACKAGE_NAME(
        "Suspicious Package Name",
        "Package name uses random/obfuscated characters typical of malware"
    ),
    NO_LABEL(
        "No App Label",
        "App has no display name — hides from casual inspection"
    ),
    ACCESSIBILITY_ABUSE(
        "Accessibility Service",
        "Declares an accessibility service — can read screen content and simulate taps"
    ),
    DEVICE_ADMIN(
        "Device Admin",
        "Registered as a Device Administrator — can lock screen, wipe device, enforce policies"
    ),
    OVERLAY_PERMISSION(
        "Draw Over Apps",
        "Holds SYSTEM_ALERT_WINDOW — can display invisible overlays above other apps"
    ),
    BACKGROUND_SERVICES(
        "Persistent Background Service",
        "Declares services that run continuously even when not in use"
    ),
    SUSPICIOUS_INSTALL_SOURCE(
        "Sideloaded APK",
        "Not installed from a trusted store — may be an unverified APK"
    )
}

sealed class HiddenScanState {
    object Idle                                               : HiddenScanState()
    data class Scanning(val progress: Float, val current: String) : HiddenScanState()
    data class Success(
        val hidden: List<HiddenApp>,
        val totalScanned: Int,
        val scanDurationMs: Long
    ) : HiddenScanState()
    data class Error(val message: String)                    : HiddenScanState()
}
