package com.cybersentinel.domain.model

/**
 * Represents a single installed app and the result of its permission audit.
 */
data class AuditedApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val installedAt: Long,           // epoch ms
    val permissions: List<String>,   // all granted permissions
    val flaggedPermissions: List<FlaggedPermission>,
    val riskLevel: AppRiskLevel,
    val appCategory: AppCategory,
    val iconLoadKey: String = packageName  // used by Coil/Glide to load icon
)

/**
 * A permission that is suspicious given the app's category.
 */
data class FlaggedPermission(
    val permission: String,
    val shortName: String,           // e.g. "RECORD_AUDIO"
    val reason: String,              // human-readable explanation
    val severity: PermissionSeverity
)

enum class PermissionSeverity(val label: String) {
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium")
}

enum class AppRiskLevel(val label: String, val priority: Int) {
    SAFE("Safe", 0),
    LOW("Low Risk", 1),
    MEDIUM("Medium Risk", 2),
    HIGH("High Risk", 3),
    CRITICAL("Critical", 4)
}

/**
 * Broad category inferred from the app name / package name heuristics.
 * Used by the Permission Mismatch engine.
 */
enum class AppCategory(val displayName: String) {
    UTILITY("Utility"),         // flashlight, calculator, clock, timer, compass
    PRODUCTIVITY("Productivity"),
    BROWSER("Browser"),
    MEDIA("Media"),
    COMMUNICATION("Communication"),
    FINANCE("Finance"),
    HEALTH("Health & Fitness"),
    GAME("Game"),
    SYSTEM("System"),
    UNKNOWN("Unknown")
}

sealed class AppAuditState {
    object Idle                                             : AppAuditState()
    object Loading                                         : AppAuditState()
    data class Success(val apps: List<AuditedApp>)         : AppAuditState()
    data class Error(val message: String)                  : AppAuditState()
    object PermissionRequired                              : AppAuditState()
}

// ── Filter options for the UI ─────────────────────────────────────────────

enum class AuditFilter(val label: String) {
    ALL("All Apps"),
    HIGH_RISK("High Risk"),
    FLAGGED("Flagged Only"),
    USER_INSTALLED("User Installed"),
    SYSTEM("System Apps")
}
