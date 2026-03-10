package com.cybersentinel.ui.screens.hidden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybersentinel.domain.model.*
import com.cybersentinel.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HiddenAppScreen(
    viewModel: HiddenAppViewModel = viewModel()
) {
    val scanState    by viewModel.scanState.collectAsState()
    val filtered     by viewModel.filteredApps.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Ghost Hunter",
                    style    = MaterialTheme.typography.displayLarge.copy(color = NeonPurple),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text("Hidden & Disguised App Detector", style = MaterialTheme.typography.bodyMedium)
            }
            // Glowing eye icon
            val pulse = rememberInfiniteTransition(label = "eye")
            val eyeAlpha by pulse.animateFloat(
                0.4f, 1f,
                infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse),
                label = "ea"
            )
            Icon(
                Icons.Default.RemoveRedEye, null,
                tint     = NeonPurple.copy(alpha = eyeAlpha),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        when (val state = scanState) {
            is HiddenScanState.Idle     -> IdleScreen(onScan = viewModel::startScan)
            is HiddenScanState.Scanning -> ScanningScreen(state)
            is HiddenScanState.Success  -> ResultsScreen(
                state        = state,
                filtered     = filtered,
                activeFilter = activeFilter,
                onFilter     = viewModel::setFilter,
                onRescan     = viewModel::startScan
            )
            is HiddenScanState.Error    -> ErrorScreen(state.message, viewModel::startScan)
        }
    }
}

// ── Idle ───────────────────────────────────────────────────────────────────

@Composable
private fun IdleScreen(onScan: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infinite = rememberInfiniteTransition(label = "idle")
            val scale by infinite.animateFloat(
                0.9f, 1.1f,
                infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse),
                label = "s"
            )

            Box(
                Modifier.size(130.dp)
                    .scale(scale = scale)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(NeonPurple.copy(0.2f), Color.Transparent))),
                Alignment.Center
            ) {
                Icon(Icons.Default.VisibilityOff, null, tint = NeonPurple, modifier = Modifier.size(64.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("Ghost Hunter", style = MaterialTheme.typography.titleLarge.copy(color = NeonPurple))
            Spacer(Modifier.height(8.dp))
            Text(
                "Detects apps hiding in the shadows:\nno icon, disabled components,\nspyware & device admin abuse",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            // Technique badges
            TechniqueGrid()

            Spacer(Modifier.height(32.dp))
            Button(
                onClick  = onScan,
                colors   = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.65f).height(52.dp)
            ) {
                Icon(Icons.Default.Search, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("HUNT GHOSTS", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun TechniqueGrid() {
    val techniques = listOf(
        Icons.Default.VisibilityOff to "No Icon",
        Icons.Default.Block         to "Disabled",
        Icons.Default.Accessibility to "Accessibility",
        Icons.Default.AdminPanelSettings to "Device Admin",
        Icons.Default.Layers        to "Overlay",
        Icons.Default.CloudOff      to "Sideloaded"
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        techniques.take(3).forEach { (icon, label) ->
            TechniquePill(icon, label)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        techniques.drop(3).forEach { (icon, label) ->
            TechniquePill(icon, label)
        }
    }
}

@Composable
private fun TechniquePill(icon: ImageVector, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(NeonPurple.copy(0.12f))
            .border(1.dp, NeonPurple.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = NeonPurple, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = NeonPurple, fontSize = 10.sp))
    }
}

// ── Scanning ───────────────────────────────────────────────────────────────

@Composable
private fun ScanningScreen(state: HiddenScanState.Scanning) {
    val infinite  = rememberInfiniteTransition(label = "scan")
    val rotation  by infinite.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "r")
    val alpha     by infinite.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse), label = "a")

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(150.dp), Alignment.Center) {
            // Rings
            repeat(3) { i ->
                val size = (150 - i * 28).dp
                Box(
                    Modifier.size(size).clip(CircleShape)
                        .background(NeonPurple.copy(0.04f + i * 0.02f))
                        .border(1.dp, NeonPurple.copy((0.2f + i * 0.15f) * alpha), CircleShape)
                )
            }
            Icon(Icons.Default.RemoveRedEye, null, tint = NeonPurple,
                modifier = Modifier.size(52.dp).rotate(rotation))
        }

        Spacer(Modifier.height(24.dp))
        Text("SCANNING FOR GHOSTS", style = MaterialTheme.typography.titleLarge.copy(
            color = NeonPurple, letterSpacing = 2.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            state.current,
            style    = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace, fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier  = Modifier.fillMaxWidth(0.65f).clip(RoundedCornerShape(4.dp)),
            color     = NeonPurple,
            trackColor = SurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${(state.progress * 100).toInt()}% scanned",
            style = MaterialTheme.typography.labelSmall.copy(color = NeonPurple.copy(0.7f))
        )
    }
}

// ── Results ────────────────────────────────────────────────────────────────

@Composable
private fun ResultsScreen(
    state: HiddenScanState.Success,
    filtered: List<HiddenApp>,
    activeFilter: HiddenFilter,
    onFilter: (HiddenFilter) -> Unit,
    onRescan: () -> Unit
) {
    Column {
        // ── Stats banner ──────────────────────────────────────────────────
        StatsBanner(state)
        Spacer(Modifier.height(12.dp))

        // ── Filter chips ──────────────────────────────────────────────────
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HiddenFilter.values()) { filter ->
                FilterChip(
                    selected = filter == activeFilter,
                    onClick  = { onFilter(filter) },
                    label    = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor  = NeonPurple.copy(0.2f),
                        selectedLabelColor      = NeonPurple,
                        containerColor          = SurfaceDark,
                        labelColor              = OnSurfaceMuted
                    ),
                    border   = FilterChipDefaults.filterChipBorder(
                        enabled             = true,
                        selected            = filter == activeFilter,
                        selectedBorderColor = NeonPurple.copy(0.6f),
                        borderColor         = DividerColor
                    )
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── List header ───────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("${filtered.size} suspicious package${if (filtered.size != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("RESCAN", color = NeonPurple, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (filtered.isEmpty()) {
            // Clean result
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VerifiedUser, null, tint = NeonGreen, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No hidden apps found", style = MaterialTheme.typography.titleLarge.copy(color = NeonGreen))
                    Text("All packages appear legitimate", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    HiddenAppCard(app)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun StatsBanner(state: HiddenScanState.Success) {
    val dangerous = state.hidden.count { it.threatLevel == HiddenThreatLevel.DANGEROUS }
    val likely    = state.hidden.count { it.threatLevel == HiddenThreatLevel.LIKELY_HIDDEN }
    val suspicious= state.hidden.count { it.threatLevel == HiddenThreatLevel.SUSPICIOUS }
    val secs      = state.scanDurationMs / 1000.0

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatPill("DANGER",    dangerous,           DangerRed)
        StatPill("HIDDEN",    likely,              NeonPurple)
        StatPill("SUSPECT",   suspicious,          WarningAmber)
        StatPill("SCANNED",   state.totalScanned,  OnSurfaceMuted)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                String.format("%.1fs", secs),
                style = MaterialTheme.typography.titleLarge.copy(color = NeonBlue, fontWeight = FontWeight.Bold)
            )
            Text("TIME", style = MaterialTheme.typography.labelSmall.copy(color = NeonBlue.copy(0.75f)))
        }
    }
}

@Composable
private fun StatPill(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge.copy(color = color, fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.75f)))
    }
}

// ── Hidden App Card ────────────────────────────────────────────────────────

@Composable
private fun HiddenAppCard(app: HiddenApp) {
    var expanded by remember { mutableStateOf(false) }
    val accent = threatColor(app.threatLevel)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBackground),
        border   = BorderStroke(1.dp, accent.copy(0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── Row 1: Icon letter + name + badge ─────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(0.15f)),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.VisibilityOff, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ThreatBadge(app.threatLevel)
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 2: Detection method chips ─────────────────────────────
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(app.detectionMethod) { method ->
                    MethodChip(method)
                }
            }

            // ── Row 3: Install date ───────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                MetaTag(Icons.Default.InstallMobile, "Installed ${fmt.format(Date(app.installedAt))}")
                if (app.isSystemApp) MetaTag(Icons.Default.PhoneAndroid, "System")
                else MetaTag(Icons.Default.GetApp, "User App")
            }

            // ── Expanded: reason details ──────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    Divider(color = DividerColor)
                    Spacer(Modifier.height(10.dp))
                    Text("DETECTION DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent, letterSpacing = 1.sp))
                    Spacer(Modifier.height(8.dp))
                    app.hiddenReasons.forEach { reason ->
                        ReasonRow(reason)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Expand caret
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.Center) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = accent.copy(0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MethodChip(method: DetectionMethod) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(NeonPurple.copy(0.1f))
            .border(1.dp, NeonPurple.copy(0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            method.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = NeonPurple, fontSize = 9.sp, letterSpacing = 0.3.sp
            )
        )
    }
}

@Composable
private fun ReasonRow(reason: HiddenReason) {
    val color = when (reason.severity) {
        HiddenSeverity.DANGEROUS  -> DangerRed
        HiddenSeverity.SUSPICIOUS -> WarningAmber
        HiddenSeverity.INFO       -> OnSurfaceMuted
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.07f))
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            when (reason.severity) {
                HiddenSeverity.DANGEROUS  -> Icons.Default.GppBad
                HiddenSeverity.SUSPICIOUS -> Icons.Default.Warning
                HiddenSeverity.INFO       -> Icons.Default.Info
            },
            null,
            tint     = color,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reason.title, style = MaterialTheme.typography.titleMedium.copy(
                    color = color, fontSize = 13.sp))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(color.copy(0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(reason.severity.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = color, fontSize = 9.sp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(reason.detail, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun ThreatBadge(level: HiddenThreatLevel) {
    val color = threatColor(level)
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(0.15f))
            .border(1.dp, color.copy(0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            level.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        )
    }
}

@Composable
private fun MetaTag(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = OnSurfaceMuted, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceMuted, fontSize = 10.sp))
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Scan Failed", style = MaterialTheme.typography.titleLarge.copy(color = DangerRed))
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

private fun threatColor(level: HiddenThreatLevel): Color = when (level) {
    HiddenThreatLevel.CLEAN         -> NeonGreen
    HiddenThreatLevel.SUSPICIOUS    -> WarningAmber
    HiddenThreatLevel.LIKELY_HIDDEN -> NeonPurple
    HiddenThreatLevel.DANGEROUS     -> DangerRed
}

private fun androidx.compose.ui.Modifier.scale(scale: Float) =
