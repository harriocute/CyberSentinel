package com.cybersentinel.data.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import com.cybersentinel.domain.model.*
import com.cybersentinel.domain.repository.HiddenScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Scans installed packages for apps that are hidden, disguised, or exhibit
 * dangerous privilege escalation patterns.
 *
 * Detection techniques:
 *  1. No CATEGORY_LAUNCHER intent → won't appear in the app drawer
 *  2. Disabled main activity component → programmatically hidden
 *  3. No app label → hidden from casual inspection
 *  4. Obfuscated / random package name
 *  5. Accessibility service declaration (spyware vector)
 *  6. Device Admin receiver registration (ransomware / stalkerware vector)
 *  7. SYSTEM_ALERT_WINDOW permission (overlay attack vector)
 *  8. Persistent background services with no UI
 *  9. Sideloaded APK (not from a trusted store)
 */
class HiddenAppScannerService(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    // ── Public API ─────────────────────────────────────────────────────────

    fun scanForHiddenApps(): Flow<HiddenScanProgress> = flow {
        val startTime  = System.currentTimeMillis()
        val packages   = getAllPackages()
        val total      = packages.size
        val flagged    = mutableListOf<HiddenApp>()

        packages.forEachIndexed { index, pkg ->
            val appName = try {
                pm.getApplicationLabel(pkg.applicationInfo ?: return@forEachIndexed).toString()
            } catch (e: Exception) { pkg.packageName }

            // Emit progress update
            emit(HiddenScanProgress(
                scanned        = index + 1,
                total          = total,
                currentPackage = appName,
                flaggedSoFar   = flagged.toList()
            ))

            val result = analysePackage(pkg) ?: return@forEachIndexed
            if (result.threatLevel != HiddenThreatLevel.CLEAN) {
                flagged.add(result)
                // Emit updated flagged list immediately
                emit(HiddenScanProgress(
                    scanned        = index + 1,
                    total          = total,
                    currentPackage = appName,
                    flaggedSoFar   = flagged.sortedByDescending { it.threatLevel.priority }
                ))
            }
        }

        // Final emit
        emit(HiddenScanProgress(
            scanned        = total,
            total          = total,
            currentPackage = "Scan complete",
            flaggedSoFar   = flagged.sortedByDescending { it.threatLevel.priority }
        ))
    }.flowOn(Dispatchers.IO)

    // ── Package Retrieval ──────────────────────────────────────────────────

    private fun getAllPackages(): List<PackageInfo> {
        val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_DISABLED_COMPONENTS

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }
    }

    // ── Per-Package Analysis ───────────────────────────────────────────────

    private fun analysePackage(pkg: PackageInfo): HiddenApp? {
        return try {
            val appInfo = pkg.applicationInfo ?: return null
            val appName = pm.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val reasons   = mutableListOf<HiddenReason>()
            val methods   = mutableListOf<DetectionMethod>()

            // ── Detection 1: No launcher icon ──────────────────────────────
            if (!hasLauncherIntent(pkg.packageName)) {
                // Exclude pure system apps (services, providers) — only flag user-space
                if (!isSystem || hasSuspiciousPermissions(pkg)) {
                    reasons.add(HiddenReason(
                        title    = "Hidden from App Drawer",
                        detail   = "No CATEGORY_LAUNCHER intent filter — the app won't appear in your home screen or app drawer.",
                        severity = if (isSystem) HiddenSeverity.INFO else HiddenSeverity.SUSPICIOUS
                    ))
                    methods.add(DetectionMethod.NO_LAUNCHER_ICON)
                }
            }

            // ── Detection 2: Main activity disabled ───────────────────────
            if (isMainActivityDisabled(pkg)) {
                reasons.add(HiddenReason(
                    title    = "Activity Component Disabled",
                    detail   = "The app's main Activity is explicitly set to DISABLED state via PackageManager, making it invisible while remaining installed.",
                    severity = HiddenSeverity.DANGEROUS
                ))
                methods.add(DetectionMethod.COMPONENT_DISABLED)
            }

            // ── Detection 3: No app label ─────────────────────────────────
            val labelStr = try { pm.getApplicationLabel(appInfo).toString().trim() } catch (e: Exception) { "" }
            if (labelStr.isBlank() || labelStr == pkg.packageName) {
                reasons.add(HiddenReason(
                    title    = "No Display Name",
                    detail   = "App has no human-readable label — it would appear as a raw package name in system settings.",
                    severity = HiddenSeverity.SUSPICIOUS
                ))
                methods.add(DetectionMethod.NO_LABEL)
            }

            // ── Detection 4: Obfuscated package name ──────────────────────
            if (isObfuscatedPackageName(pkg.packageName)) {
                reasons.add(HiddenReason(
                    title    = "Obfuscated Package Name",
                    detail   = "Package name '${pkg.packageName}' uses random/single-character segments typical of auto-generated or obfuscated malware.",
                    severity = HiddenSeverity.SUSPICIOUS
                ))
                methods.add(DetectionMethod.EMPTY_PACKAGE_NAME)
            }

            // ── Detection 5: Accessibility service ────────────────────────
            if (declaresAccessibilityService(pkg)) {
                reasons.add(HiddenReason(
                    title    = "Accessibility Service Declared",
                    detail   = "App registers an AccessibilityService. Legitimate uses exist, but this is the #1 vector for spyware to read screen content, capture passwords, and simulate touches.",
                    severity = HiddenSeverity.DANGEROUS
                ))
                methods.add(DetectionMethod.ACCESSIBILITY_ABUSE)
            }

            // ── Detection 6: Device Admin ─────────────────────────────────
            if (isDeviceAdmin(pkg.packageName)) {
                reasons.add(HiddenReason(
                    title    = "Device Administrator",
                    detail   = "App is registered as a Device Admin. This grants it power to lock the screen, wipe the device, and resist uninstallation — classic ransomware / stalkerware behaviour.",
                    severity = HiddenSeverity.DANGEROUS
                ))
                methods.add(DetectionMethod.DEVICE_ADMIN)
            }

            // ── Detection 7: Overlay permission ──────────────────────────
            if (holdsOverlayPermission(pkg)) {
                if (!isSystem) {
                    reasons.add(HiddenReason(
                        title    = "System Overlay Permission",
                        detail   = "App holds SYSTEM_ALERT_WINDOW, allowing it to draw invisible or fake UI layers on top of other apps — used for clickjacking and credential harvesting.",
                        severity = HiddenSeverity.DANGEROUS
                    ))
                    methods.add(DetectionMethod.OVERLAY_PERMISSION)
                }
            }

            // ── Detection 8: Persistent background service (no UI) ─────────
            if (!hasLauncherIntent(pkg.packageName) && hasPersistentService(pkg)) {
                reasons.add(HiddenReason(
                    title    = "Hidden Background Service",
                    detail   = "App runs persistent background services but has no visible UI — a hallmark of stalkerware and silent trackers.",
                    severity = HiddenSeverity.DANGEROUS
                ))
                methods.add(DetectionMethod.BACKGROUND_SERVICES)
            }

            // ── Detection 9: Sideloaded (not from known store) ────────────
            if (!isSystem && isSideloaded(pkg.packageName)) {
                reasons.add(HiddenReason(
                    title    = "Sideloaded APK",
                    detail   = "App was not installed from Google Play or a known app store. Sideloaded APKs bypass store-level malware scanning.",
                    severity = HiddenSeverity.SUSPICIOUS
                ))
                methods.add(DetectionMethod.SUSPICIOUS_INSTALL_SOURCE)
            }

            if (reasons.isEmpty()) return null

            val threat = computeThreatLevel(reasons)

            HiddenApp(
                packageName    = pkg.packageName,
                appName        = appName,
                versionName    = pkg.versionName ?: "?",
                isSystemApp    = isSystem,
                installedAt    = pkg.firstInstallTime,
                updatedAt      = pkg.lastUpdateTime,
                hiddenReasons  = reasons,
                threatLevel    = threat,
                detectionMethod = methods
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Detection Helpers ──────────────────────────────────────────────────

    private fun hasLauncherIntent(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        return pm.queryIntentActivities(intent, 0).isNotEmpty()
    }

    private fun isMainActivityDisabled(pkg: PackageInfo): Boolean {
        val activities = pkg.activities ?: return false
        return activities.any { activity ->
            try {
                val state = pm.getComponentEnabledSetting(
                    ComponentName(pkg.packageName, activity.name)
                )
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            } catch (e: Exception) { false }
        }
    }

    private fun isObfuscatedPackageName(packageName: String): Boolean {
        val segments = packageName.split(".")
        // Flag if any segment is a single character or looks randomly generated
        val suspiciousSegments = segments.count { seg ->
            seg.length == 1 || (seg.length <= 3 && seg.all { it.isLetter() } && seg == seg.lowercase())
        }
        return suspiciousSegments >= 2 && segments.size >= 3
    }

    private fun declaresAccessibilityService(pkg: PackageInfo): Boolean {
        val services = pkg.services ?: return false
        return services.any { svc ->
            svc.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" ||
            (svc.name?.contains("accessibility", ignoreCase = true) == true)
        }
    }

    private fun isDeviceAdmin(packageName: String): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.any { it.packageName == packageName } == true
        } catch (e: Exception) { false }
    }

    private fun holdsOverlayPermission(pkg: PackageInfo): Boolean {
        val perms = pkg.requestedPermissions ?: return false
        return "android.permission.SYSTEM_ALERT_WINDOW" in perms
    }

    private fun hasPersistentService(pkg: PackageInfo): Boolean {
        val services = pkg.services ?: return false
        return services.any { svc ->
            (svc.flags and ServiceInfo.FLAG_STOP_WITH_TASK) == 0
        }
    }

    private fun isSideloaded(packageName: String): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            installer == null || installer !in TRUSTED_INSTALLERS
        } catch (e: Exception) { false }
    }

    private fun hasSuspiciousPermissions(pkg: PackageInfo): Boolean {
        val dangerous = setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS"
        )
        return (pkg.requestedPermissions?.toSet() ?: emptySet())
            .intersect(dangerous)
            .isNotEmpty()
    }

    private fun computeThreatLevel(reasons: List<HiddenReason>): HiddenThreatLevel {
        val hasDangerous    = reasons.any { it.severity == HiddenSeverity.DANGEROUS }
        val suspiciousCount = reasons.count { it.severity == HiddenSeverity.SUSPICIOUS }
        return when {
            hasDangerous && suspiciousCount >= 1 -> HiddenThreatLevel.DANGEROUS
            hasDangerous                         -> HiddenThreatLevel.LIKELY_HIDDEN
            suspiciousCount >= 2                 -> HiddenThreatLevel.LIKELY_HIDDEN
            suspiciousCount >= 1                 -> HiddenThreatLevel.SUSPICIOUS
            else                                 -> HiddenThreatLevel.SUSPICIOUS
        }
    }

    companion object {
        private val TRUSTED_INSTALLERS = setOf(
            "com.android.vending",          // Google Play
            "com.amazon.venezia",           // Amazon Appstore
            "com.samsung.android.app.store",// Galaxy Store
            "com.huawei.appmarket",         // Huawei AppGallery
            "com.sec.android.app.samsungapps"
        )
    }
}
