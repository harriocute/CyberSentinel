package com.cybersentinel.data.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.cybersentinel.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Queries [PackageManager] for all installed apps and runs the
 * Permission Mismatch risk engine against each one.
 *
 * Permission Mismatch Logic:
 *  A "mismatch" occurs when a low-privilege category app (Utility, e.g. Flashlight
 *  or Calculator) holds a high-value sensor/data permission that has no reasonable
 *  justification for that app category.
 */
class AppPermissionAuditorService(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    // ── Public API ─────────────────────────────────────────────────────────

    fun auditInstalledApps(): Flow<List<AuditedApp>> = flow {
        val packages = getInstalledPackages()
        val results = mutableListOf<AuditedApp>()

        for (pkg in packages) {
            val audited = auditPackage(pkg) ?: continue
            results.add(audited)
            // Emit after every 10 apps so the UI can show progress
            if (results.size % 10 == 0) emit(results.toList())
        }

        // Final emit with all results sorted by risk (highest first)
        emit(results.sortedByDescending { it.riskLevel.priority })
    }.flowOn(Dispatchers.IO)

    // ── Package Retrieval ──────────────────────────────────────────────────

    private fun getInstalledPackages(): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    }

    // ── Per-Package Audit ──────────────────────────────────────────────────

    private fun auditPackage(pkg: PackageInfo): AuditedApp? {
        return try {
            val appInfo    = pkg.applicationInfo ?: return null
            val appName    = pm.getApplicationLabel(appInfo).toString()
            val isSystem   = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val permissions = pkg.requestedPermissions?.toList() ?: emptyList()
            val category   = inferCategory(appName, pkg.packageName)
            val flagged    = detectMismatches(appName, pkg.packageName, category, permissions)
            val riskLevel  = computeRiskLevel(flagged)

            AuditedApp(
                packageName       = pkg.packageName,
                appName           = appName,
                versionName       = pkg.versionName ?: "?",
                isSystemApp       = isSystem,
                installedAt       = pkg.firstInstallTime,
                permissions       = permissions,
                flaggedPermissions = flagged,
                riskLevel         = riskLevel,
                appCategory       = category
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Category Inference ─────────────────────────────────────────────────

    /**
     * Infers category from app name and package name using keyword matching.
     * In production, supplement this with PackageManager's ApplicationInfo.category
     * (available API 26+) for higher accuracy.
     */
    private fun inferCategory(appName: String, packageName: String): AppCategory {
        val combined = "$appName $packageName".lowercase()
        return when {
            UTILITY_KEYWORDS.any    { combined.contains(it) } -> AppCategory.UTILITY
            BROWSER_KEYWORDS.any    { combined.contains(it) } -> AppCategory.BROWSER
            FINANCE_KEYWORDS.any    { combined.contains(it) } -> AppCategory.FINANCE
            HEALTH_KEYWORDS.any     { combined.contains(it) } -> AppCategory.HEALTH
            COMM_KEYWORDS.any       { combined.contains(it) } -> AppCategory.COMMUNICATION
            MEDIA_KEYWORDS.any      { combined.contains(it) } -> AppCategory.MEDIA
            GAME_KEYWORDS.any       { combined.contains(it) } -> AppCategory.GAME
            PRODUCTIVITY_KEYWORDS.any { combined.contains(it) } -> AppCategory.PRODUCTIVITY
            SYSTEM_PACKAGES.any     { packageName.startsWith(it) } -> AppCategory.SYSTEM
            else                                               -> AppCategory.UNKNOWN
        }
    }

    // ── Permission Mismatch Engine ─────────────────────────────────────────

    /**
     * Core risk logic: checks each permission against the app's inferred category.
     * Mismatches are only raised when a permission is *unexpected* for the category.
     */
    private fun detectMismatches(
        appName: String,
        packageName: String,
        category: AppCategory,
        permissions: List<String>
    ): List<FlaggedPermission> {

        val flagged = mutableListOf<FlaggedPermission>()

        for (permission in permissions) {
            val short = permission.substringAfterLast(".")
            val rule  = MISMATCH_RULES[permission] ?: continue

            // Check if this category is in the rule's "suspicious categories"
            if (category in rule.suspiciousFor || category == AppCategory.UTILITY) {
                if (category !in rule.expectedFor) {
                    flagged.add(
                        FlaggedPermission(
                            permission = permission,
                            shortName  = short,
                            reason     = rule.reason.replace("{APP}", appName),
                            severity   = rule.severity
                        )
                    )
                }
            }
        }

        return flagged
    }

    private fun computeRiskLevel(flagged: List<FlaggedPermission>): AppRiskLevel {
        if (flagged.isEmpty()) return AppRiskLevel.SAFE
        val hasCritical = flagged.any { it.severity == PermissionSeverity.CRITICAL }
        val hasHigh     = flagged.any { it.severity == PermissionSeverity.HIGH }
        val count       = flagged.size
        return when {
            hasCritical || count >= 4 -> AppRiskLevel.CRITICAL
            hasHigh     || count >= 3 -> AppRiskLevel.HIGH
            count >= 2               -> AppRiskLevel.MEDIUM
            else                     -> AppRiskLevel.LOW
        }
    }

    // ── Rule & Keyword Tables ──────────────────────────────────────────────

    private data class MismatchRule(
        val reason: String,
        val severity: PermissionSeverity,
        val suspiciousFor: Set<AppCategory>,
        val expectedFor: Set<AppCategory>
    )

    private val MISMATCH_RULES: Map<String, MismatchRule> = mapOf(

        "android.permission.RECORD_AUDIO" to MismatchRule(
            reason       = "{APP} can record microphone audio — unusual for this app type",
            severity     = PermissionSeverity.CRITICAL,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.PRODUCTIVITY, AppCategory.FINANCE, AppCategory.GAME),
            expectedFor   = setOf(AppCategory.COMMUNICATION, AppCategory.MEDIA)
        ),

        "android.permission.READ_SMS" to MismatchRule(
            reason       = "{APP} can read your SMS messages — a common data-theft vector",
            severity     = PermissionSeverity.CRITICAL,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.MEDIA, AppCategory.GAME, AppCategory.PRODUCTIVITY, AppCategory.HEALTH),
            expectedFor   = setOf(AppCategory.COMMUNICATION)
        ),

        "android.permission.RECEIVE_SMS" to MismatchRule(
            reason       = "{APP} can intercept incoming SMS messages including OTP codes",
            severity     = PermissionSeverity.CRITICAL,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.MEDIA, AppCategory.GAME, AppCategory.PRODUCTIVITY),
            expectedFor   = setOf(AppCategory.COMMUNICATION)
        ),

        "android.permission.ACCESS_FINE_LOCATION" to MismatchRule(
            reason       = "{APP} has precise GPS location access — no clear reason for this category",
            severity     = PermissionSeverity.HIGH,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.FINANCE),
            expectedFor   = setOf(AppCategory.COMMUNICATION, AppCategory.BROWSER, AppCategory.HEALTH)
        ),

        "android.permission.READ_CONTACTS" to MismatchRule(
            reason       = "{APP} can read your full contacts list",
            severity     = PermissionSeverity.HIGH,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.MEDIA, AppCategory.GAME, AppCategory.FINANCE),
            expectedFor   = setOf(AppCategory.COMMUNICATION, AppCategory.PRODUCTIVITY)
        ),

        "android.permission.READ_CALL_LOG" to MismatchRule(
            reason       = "{APP} can access your call history — highly sensitive",
            severity     = PermissionSeverity.CRITICAL,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.MEDIA, AppCategory.FINANCE),
            expectedFor   = setOf(AppCategory.COMMUNICATION)
        ),

        "android.permission.CAMERA" to MismatchRule(
            reason       = "{APP} can activate your camera — unexpected for this app type",
            severity     = PermissionSeverity.HIGH,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.FINANCE, AppCategory.GAME),
            expectedFor   = setOf(AppCategory.COMMUNICATION, AppCategory.MEDIA, AppCategory.PRODUCTIVITY)
        ),

        "android.permission.READ_EXTERNAL_STORAGE" to MismatchRule(
            reason       = "{APP} can read all files on your device storage",
            severity     = PermissionSeverity.MEDIUM,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.HEALTH),
            expectedFor   = setOf(AppCategory.MEDIA, AppCategory.PRODUCTIVITY, AppCategory.COMMUNICATION)
        ),

        "android.permission.PROCESS_OUTGOING_CALLS" to MismatchRule(
            reason       = "{APP} can intercept or redirect outgoing phone calls",
            severity     = PermissionSeverity.CRITICAL,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.MEDIA, AppCategory.FINANCE),
            expectedFor   = setOf(AppCategory.COMMUNICATION)
        ),

        "android.permission.GET_ACCOUNTS" to MismatchRule(
            reason       = "{APP} can enumerate all Google/email accounts on the device",
            severity     = PermissionSeverity.MEDIUM,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.MEDIA),
            expectedFor   = setOf(AppCategory.COMMUNICATION, AppCategory.PRODUCTIVITY, AppCategory.FINANCE)
        ),

        "android.permission.USE_BIOMETRIC" to MismatchRule(
            reason       = "{APP} requests biometric (fingerprint/face) access",
            severity     = PermissionSeverity.HIGH,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.MEDIA),
            expectedFor   = setOf(AppCategory.FINANCE, AppCategory.PRODUCTIVITY, AppCategory.HEALTH)
        ),

        "android.permission.SEND_SMS" to MismatchRule(
            reason       = "{APP} can send SMS messages — could incur charges or spread malware",
            severity     = PermissionSeverity.CRITICAL,
            suspiciousFor = setOf(AppCategory.UTILITY, AppCategory.GAME, AppCategory.MEDIA, AppCategory.FINANCE, AppCategory.HEALTH),
            expectedFor   = setOf(AppCategory.COMMUNICATION)
        ),
    )

    companion object {
        private val UTILITY_KEYWORDS = listOf(
            "flashlight", "torch", "calculator", "calc", "compass",
            "clock", "timer", "alarm", "stopwatch", "battery",
            "cleaner", "booster", "vpn", "qr", "barcode", "scanner",
            "ruler", "converter", "unit", "translate", "notepad", "notes"
        )
        private val BROWSER_KEYWORDS   = listOf("browser", "chrome", "firefox", "opera", "edge", "surf")
        private val FINANCE_KEYWORDS   = listOf("bank", "pay", "wallet", "finance", "invest", "crypto", "stock")
        private val HEALTH_KEYWORDS    = listOf("health", "fitness", "workout", "step", "calorie", "meditat")
        private val COMM_KEYWORDS      = listOf("chat", "message", "whatsapp", "telegram", "signal", "call", "sms", "mail", "email")
        private val MEDIA_KEYWORDS     = listOf("music", "player", "video", "stream", "podcast", "photo", "camera", "gallery")
        private val GAME_KEYWORDS      = listOf("game", "play", "arcade", "puzzle", "clash", "legend", "war", "race")
        private val PRODUCTIVITY_KEYWORDS = listOf("office", "word", "excel", "sheet", "doc", "pdf", "task", "todo", "project")
        private val SYSTEM_PACKAGES    = listOf("com.android.", "com.google.android.", "android.")
    }
}
