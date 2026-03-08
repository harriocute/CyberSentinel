package com.cybersentinel.ui.screens.wifi

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.cybersentinel.domain.model.*
import com.cybersentinel.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WifiScannerScreen(
    viewModel: WifiViewModel = viewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    val connected by viewModel.connectedNetwork.collectAsState()

    val locationPermission = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "WiFi Security",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text  = "Scan nearby networks for threats",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        // ── Connected Network Banner ────────────────────────────────────────
        connected?.let { net ->
            ConnectedNetworkBanner(network = net)
            Spacer(Modifier.height(12.dp))
        }

        // ── Main Content ────────────────────────────────────────────────────
        when (val state = scanState) {
            is WifiScanState.Idle -> IdleState(
                onScan = {
                    if (locationPermission.status.isGranted) viewModel.startScan()
                    else locationPermission.launchPermissionRequest()
                }
            )

            is WifiScanState.Scanning -> ScanningAnimation()

            is WifiScanState.Success -> {
                val summary = viewModel.getRiskSummary(state.networks)
                RiskSummaryBar(summary = summary)
                Spacer(Modifier.height(12.dp))
                NetworkList(
                    networks = state.networks,
                    onRescan = { viewModel.startScan() }
                )
            }

            is WifiScanState.Error -> ErrorState(
                message = state.message,
                onRetry = { viewModel.startScan() }
            )

            is WifiScanState.PermissionRequired -> PermissionState(
                onRequest = { locationPermission.launchPermissionRequest() }
            )
        }

        // Handle permission result
        LaunchedEffect(locationPermission.status) {
            if (!locationPermission.status.isGranted &&
                locationPermission.status is PermissionStatus.Denied) {
                viewModel.onPermissionDenied()
            }
        }
    }
}

// ── Sub-Composables ────────────────────────────────────────────────────────

@Composable
private fun ConnectedNetworkBanner(network: WifiNetwork) {
    val borderColor = riskColor(network.riskLevel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = borderColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Connected", style = MaterialTheme.typography.labelSmall)
            Text(
                text  = network.ssid,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "${network.securityType.displayName} · ${network.signalStrength} dBm",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        RiskChip(level = network.riskLevel)
    }
}

@Composable
private fun IdleState(onScan: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Pulsing radar icon
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue  = 1.08f,
                animationSpec = infiniteRepeatable(
                    tween(1200, easing = EaseInOut),
                    RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(NeonGreen.copy(alpha = 0.2f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = "Scan",
                    tint     = NeonGreen,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Ready to Scan", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap below to analyse nearby WiFi networks",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onScan,
                colors  = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape   = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.6f).height(52.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("SCAN NETWORKS", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun ScanningAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.05f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.3f * pulseAlpha), CircleShape)
                )
                // Inner ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.08f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f * pulseAlpha), CircleShape)
                )
                // Rotating radar sweep
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier
                        .size(56.dp)
                        .rotate(rotation)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text  = "SCANNING...",
                style = MaterialTheme.typography.titleLarge.copy(
                    color         = NeonGreen,
                    letterSpacing = 3.sp
                )
            )
            Spacer(Modifier.height(8.dp))
            Text("Analysing nearby networks for threats", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(4.dp)),
                color            = NeonGreen,
                trackColor       = SurfaceVariant
            )
        }
    }
}

@Composable
private fun RiskSummaryBar(summary: RiskSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryPill("CRIT",   summary.critical, DangerRed)
        SummaryPill("HIGH",   summary.high,     Color(0xFFFF6B35))
        SummaryPill("MED",    summary.medium,   WarningAmber)
        SummaryPill("SAFE",   summary.safe,     NeonGreen)
        SummaryPill("TOTAL",  summary.total,    OnSurfaceMuted)
    }
}

@Composable
private fun SummaryPill(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = count.toString(),
            style = MaterialTheme.typography.titleLarge.copy(color = color, fontWeight = FontWeight.Bold)
        )
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color.copy(alpha = 0.7f)))
    }
}

@Composable
private fun NetworkList(networks: List<WifiNetwork>, onRescan: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${networks.size} Networks Found", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("RESCAN", color = NeonBlue, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        items(networks) { network ->
            NetworkCard(network = network)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun NetworkCard(network: WifiNetwork) {
    var expanded by remember { mutableStateOf(false) }
    val accentColor = riskColor(network.riskLevel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp)) {
            // Row 1: SSID + Risk chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = wifiIcon(network.signalStrength),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text     = network.ssid,
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                RiskChip(level = network.riskLevel)
            }

            Spacer(Modifier.height(6.dp))

            // Row 2: Security type + frequency
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTag(Icons.Default.Lock, network.securityType.displayName, accentColor)
                InfoTag(Icons.Default.CellTower, "${network.frequency} MHz", OnSurfaceMuted)
                InfoTag(Icons.Default.SignalCellularAlt, "${network.signalStrength} dBm", OnSurfaceMuted)
            }

            // Expandable risk reasons
            AnimatedVisibility(visible = expanded && network.riskReasons.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp)) {
                    Divider(color = DividerColor)
                    Spacer(Modifier.height(10.dp))
                    Text("Risk Details", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    network.riskReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning, null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTag(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = color, fontSize = 11.sp))
    }
}

@Composable
private fun RiskChip(level: RiskLevel) {
    val color = riskColor(level)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text  = level.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
            )
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(64.dp))
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

@Composable
private fun PermissionState(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.LocationOff, null, tint = WarningAmber, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Location Permission Required", style = MaterialTheme.typography.titleLarge.copy(color = WarningAmber))
            Spacer(Modifier.height(8.dp))
            Text(
                "Android requires ACCESS_FINE_LOCATION to scan WiFi networks. Your location data is never stored or transmitted.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequest,
                colors  = ButtonDefaults.buttonColors(containerColor = WarningAmber)
            ) {
                Text("GRANT PERMISSION", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.SAFE     -> NeonGreen
    RiskLevel.LOW      -> Color(0xFF4ADE80)
    RiskLevel.MEDIUM   -> WarningAmber
    RiskLevel.HIGH     -> Color(0xFFFF6B35)
    RiskLevel.CRITICAL -> DangerRed
}

private fun wifiIcon(rssi: Int): ImageVector = when {
    rssi >= -60 -> Icons.Default.Wifi
    rssi >= -75 -> Icons.Default.Wifi2Bar
    else        -> Icons.Default.Wifi1Bar
}
