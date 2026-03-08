package com.cybersentinel.ui.screens.apps

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybersentinel.domain.model.*
import com.cybersentinel.ui.theme.*

@Composable
fun AppAuditScreen(
    viewModel: AppAuditViewModel = viewModel()
) {
    val auditState  by viewModel.auditState.collectAsState()
    val filtered    by viewModel.filteredApps.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val searchQuery  by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ──────────────────────────────────────────────────────
        Text(
            "App Auditor",
            style    = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Permission Mismatch Detection",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        when (val state = auditState) {
            is AppAuditState.Idle    -> IdleScreen(onStart = viewModel::startAudit)

            is AppAuditState.Loading -> {
                // Show partial results while loading continues
                val partial = filtered
                if (partial.isEmpty()) {
                    ScanningScreen()
                } else {
                    // Progressive results
                    AuditContent(
                        apps          = partial,
                        summary       = viewModel.getSummary(partial),
                        activeFilter  = activeFilter,
                        searchQuery   = searchQuery,
                        isLoading     = true,
                        onFilterChange = viewModel::setFilter,
                        onSearch      = viewModel::setSearchQuery,
                        onRescan      = viewModel::startAudit
                    )
                }
            }

            is AppAuditState.Success -> AuditContent(
                apps           = filtered,
                summary        = viewModel.getSummary(state.apps),
                activeFilter   = activeFilter,
                searchQuery    = searchQuery,
                isLoading      = false,
                onFilterChange = viewModel::setFilter,
                onSearch       = viewModel::setSearchQuery,
                onRescan       = viewModel::startAudit
            )

            is AppAuditState.Error   -> ErrorScreen(state.message, viewModel::startAudit)
            else                     -> Unit
        }
    }
}

// ── Idle ───────────────────────────────────────────────────────────────────

@Composable
private fun IdleScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infinite = rememberInfiniteTransition(label = "idle")
            val scale by infinite.animateFloat(
                initialValue = 0.93f, targetValue = 1.07f,
                animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse),
                label = "s"
            )

            Box(
                Modifier.size(130.dp).scale(scale).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(NeonBlue.copy(0.18f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, null, tint = NeonBlue, modifier = Modifier.size(64.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("Permission Auditor", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Scans all installed apps for suspicious\npermissions that don't match their purpose",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onStart,
                colors  = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                shape   = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.65f).height(52.dp)
            ) {
                Icon(Icons.Default.ManageSearch, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("AUDIT ALL APPS", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

// ── Scanning animation ─────────────────────────────────────────────────────

@Composable
private fun ScanningScreen() {
    val infinite   = rememberInfiniteTransition(label = "scan")
    val rotation   by infinite.animateFloat(0f, 360f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "r")
    val alpha      by infinite.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse), label = "a")

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp), Alignment.Center) {
                Box(Modifier.size(140.dp).clip(CircleShape)
                    .background(NeonBlue.copy(0.05f))
                    .border(1.dp, NeonBlue.copy(0.3f * alpha), CircleShape))
                Box(Modifier.size(96.dp).clip(CircleShape)
                    .background(NeonBlue.copy(0.08f))
                    .border(1.dp, NeonBlue.copy(0.6f * alpha), CircleShape))
                Icon(Icons.Default.Shield, null, tint = NeonBlue,
                    modifier = Modifier.size(52.dp).rotate(rotation))
            }
            Spacer(Modifier.height(24.dp))
            Text("AUDITING APPS...", style = MaterialTheme.typography.titleLarge.copy(
                color = NeonBlue, letterSpacing = 3.sp))
            Spacer(Modifier.height(8.dp))
            Text("Analysing permissions for all installed packages", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)),
                color      = NeonBlue,
                trackColor = SurfaceVariant
            )
        }
    }
}

// ── Full results view ──────────────────────────────────────────────────────

@Composable
private fun AuditContent(
    apps: List<AuditedApp>,
    summary: AuditSummary,
    activeFilter: AuditFilter,
    searchQuery: String,
    isLoading: Boolean,
    onFilterChange: (AuditFilter) -> Unit,
    onSearch: (String) -> Unit,
    onRescan: () -> Unit
) {
    Column {
        // Summary bar
        AuditSummaryBar(summary)
        Spacer(Modifier.height(12.dp))

        // Search field
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = onSearch,
            placeholder   = { Text("Search apps…", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = OnSurfaceMuted) },
            trailingIcon  = {
                if (searchQuery.isNotBlank())
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Default.Clear, null, tint = OnSurfaceMuted)
                    }
            },
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = NeonBlue,
                unfocusedBorderColor = DividerColor,
                cursorColor          = NeonBlue,
                focusedTextColor     = OnSurface,
                unfocusedTextColor   = OnSurface,
                focusedContainerColor   = SurfaceDark,
                unfocusedContainerColor = SurfaceDark
            ),
            modifier      = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(Modifier.height(10.dp))

        // Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AuditFilter.values()) { filter ->
                FilterChip(
                    selected  = filter == activeFilter,
                    onClick   = { onFilterChange(filter) },
                    label     = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    colors    = FilterChipDefaults.filterChipColors(
                        selectedContainerColor    = NeonBlue.copy(alpha = 0.2f),
                        selectedLabelColor        = NeonBlue,
                        selectedLeadingIconColor  = NeonBlue,
                        containerColor            = SurfaceDark,
                        labelColor                = OnSurfaceMuted
                    ),
                    border    = FilterChipDefaults.filterChipBorder(
                        enabled               = true,
                        selected              = filter == activeFilter,
                        selectedBorderColor   = NeonBlue.copy(alpha = 0.6f),
                        borderColor           = DividerColor
                    )
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Results header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${apps.size} apps${if (isLoading) " (scanning…)" else ""}",
                style = MaterialTheme.typography.titleMedium
            )
            if (!isLoading) {
                TextButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("RESCAN", color = NeonBlue, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // App list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps, key = { it.packageName }) { app ->
                AppCard(app)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Summary bar ────────────────────────────────────────────────────────────

@Composable
private fun AuditSummaryBar(summary: AuditSummary) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryPill("CRITICAL", summary.critical, DangerRed)
        SummaryPill("HIGH",     summary.high,     Color(0xFFFF6B35))
        SummaryPill("FLAGGED",  summary.flagged,  WarningAmber)
        SummaryPill("SAFE",     summary.safe,     NeonGreen)
        SummaryPill("TOTAL",    summary.total,    OnSurfaceMuted)
    }
}

@Composable
private fun SummaryPill(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge.copy(color = color, fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.75f)))
    }
}

// ── App Card ───────────────────────────────────────────────────────────────

@Composable
private fun AppCard(app: AuditedApp) {
    var expanded by remember { mutableStateOf(false) }
    val accentColor = appRiskColor(app.riskLevel)
    val hasMismatches = app.flaggedPermissions.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth().clickable { if (hasMismatches) expanded = !expanded },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBackground),
        border   = BorderStroke(1.dp, if (hasMismatches) accentColor.copy(0.45f) else DividerColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── Row 1: App name + risk chip ──────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // App icon placeholder (first letter)
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    Alignment.Center
                ) {
                    Text(
                        app.appName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge.copy(color = accentColor)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        app.packageName,
                        style    = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                RiskBadge(app.riskLevel)
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 2: Meta tags ─────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetaTag(
                    icon  = Icons.Default.Category,
                    label = app.appCategory.displayName,
                    color = NeonBlue.copy(0.8f)
                )
                MetaTag(
                    icon  = if (app.isSystemApp) Icons.Default.PhoneAndroid else Icons.Default.GetApp,
                    label = if (app.isSystemApp) "System" else "User",
                    color = OnSurfaceMuted
                )
                MetaTag(
                    icon  = Icons.Default.VpnKey,
                    label = "${app.permissions.size} perms",
                    color = OnSurfaceMuted
                )
                if (hasMismatches) {
                    MetaTag(
                        icon  = Icons.Default.Warning,
                        label = "${app.flaggedPermissions.size} flagged",
                        color = accentColor
                    )
                }
            }

            // ── Expandable: Flagged permissions detail ────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    Divider(color = DividerColor)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "FLAGGED PERMISSIONS",
                        style = MaterialTheme.typography.labelSmall.copy(color = NeonBlue, letterSpacing = 1.sp)
                    )
                    Spacer(Modifier.height(8.dp))

                    app.flaggedPermissions.forEach { fp ->
                        FlaggedPermissionRow(fp)
                        Spacer(Modifier.height(6.dp))
                    }

                    // All permissions collapsed view
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All ${app.permissions.size} permissions: " +
                            app.permissions
                                .map { it.substringAfterLast(".") }
                                .take(8)
                                .joinToString(", ") +
                            if (app.permissions.size > 8) " …" else "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp
                        )
                    )
                }
            }

            // Expand hint
            if (hasMismatches) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        tint     = accentColor.copy(0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlaggedPermissionRow(fp: FlaggedPermission) {
    val color = when (fp.severity) {
        PermissionSeverity.CRITICAL -> DangerRed
        PermissionSeverity.HIGH     -> Color(0xFFFF6B35)
        PermissionSeverity.MEDIUM   -> WarningAmber
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.08f))
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.GppBad, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fp.shortName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.width(8.dp))
                SeverityChip(fp.severity)
            }
            Text(fp.reason, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun SeverityChip(severity: PermissionSeverity) {
    val color = when (severity) {
        PermissionSeverity.CRITICAL -> DangerRed
        PermissionSeverity.HIGH     -> Color(0xFFFF6B35)
        PermissionSeverity.MEDIUM   -> WarningAmber
    }
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            severity.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(color = color, letterSpacing = 0.5.sp)
        )
    }
}

@Composable
private fun RiskBadge(level: AppRiskLevel) {
    val color = appRiskColor(level)
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(0.15f))
            .border(1.dp, color.copy(0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            level.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun MetaTag(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = color, fontSize = 10.sp))
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Audit Failed", style = MaterialTheme.typography.titleLarge.copy(color = DangerRed))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onRetry, border = BorderStroke(1.dp, DangerRed)) {
                Text("RETRY", color = DangerRed)
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun appRiskColor(level: AppRiskLevel): Color = when (level) {
    AppRiskLevel.SAFE     -> NeonGreen
    AppRiskLevel.LOW      -> Color(0xFF4ADE80)
    AppRiskLevel.MEDIUM   -> WarningAmber
    AppRiskLevel.HIGH     -> Color(0xFFFF6B35)
    AppRiskLevel.CRITICAL -> DangerRed
}

// Kotlin extension to allow scale modifier inline (mirrors WifiScannerScreen usage)
private fun androidx.compose.ui.Modifier.scale(scale: Float) =
    this.then(androidx.compose.ui.draw.scale(scale))
