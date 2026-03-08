package com.cybersentinel.ui.screens.email

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun EmailAnalyserScreen(
    viewModel: EmailViewModel = viewModel()
) {
    val authState      by viewModel.authState.collectAsState()
    val analysisState  by viewModel.analysisState.collectAsState()
    val filtered       by viewModel.filteredResults.collectAsState()
    val activeFilter   by viewModel.activeFilter.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Phish Shield",
                    style    = MaterialTheme.typography.displayLarge.copy(color = NeonBlue),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text("AI-Powered Email Threat Detection", style = MaterialTheme.typography.bodyMedium)
            }
            // Animated mail icon
            val pulse = rememberInfiniteTransition(label = "mail")
            val alpha by pulse.animateFloat(
                0.4f, 1f,
                infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
                label = "ma"
            )
            Icon(Icons.Default.Shield, null, tint = NeonBlue.copy(alpha = alpha), modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))

        // ── Screen routing ───────────────────────────────────────────────────
        when (authState) {
            is EmailAuthState.Idle -> LoginScreen(
                onConnect = { email, pass, provider, apiKey, host ->
                    viewModel.connectAndAnalyse(email, pass, provider, apiKey, host)
                }
            )

            is EmailAuthState.Connecting -> ConnectingScreen()

            is EmailAuthState.Connected -> {
                // Show the analysis UI once connected
                when (val aState = analysisState) {
                    is EmailAnalysisState.Idle           -> ConnectingScreen()
                    is EmailAnalysisState.FetchingEmails -> FetchingScreen()
                    is EmailAnalysisState.AnalysingEmails -> AnalysingScreen(
                        state        = aState,
                        filtered     = filtered,
                        activeFilter = activeFilter,
                        onFilter     = viewModel::setFilter
                    )
                    is EmailAnalysisState.Complete -> ResultsScreen(
                        state        = aState,
                        filtered     = filtered,
                        summary      = viewModel.getSummary(aState.results),
                        activeFilter = activeFilter,
                        onFilter     = viewModel::setFilter,
                        onDisconnect = viewModel::disconnect
                    )
                    is EmailAnalysisState.Error -> AnalysisErrorScreen(aState.message, viewModel::retry)
                }
            }

            is EmailAuthState.Error -> AuthErrorScreen(
                message  = (authState as EmailAuthState.Error).message,
                onRetry  = viewModel::retry
            )
        }
    }
}

// ── Login / Connection Screen ──────────────────────────────────────────────

@Composable
private fun LoginScreen(
    onConnect: (email: String, pass: String, provider: String, apiKey: String, host: String) -> Unit
) {
    var emailAddress    by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var geminiKey       by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("gmail") }
    var customHost      by remember { mutableStateOf("") }
    var passVisible     by remember { mutableStateOf(false) }
    var keyVisible      by remember { mutableStateOf(false) }
    var showHelp        by remember { mutableStateOf(false) }

    val providers = listOf(
        "gmail"   to "Gmail",
        "outlook" to "Outlook / Hotmail",
        "yahoo"   to "Yahoo Mail",
        "custom"  to "Custom IMAP"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Provider selector
            Text("Email Provider", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { (key, label) ->
                    FilterChip(
                        selected  = selectedProvider == key,
                        onClick   = { selectedProvider = key },
                        label     = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonBlue.copy(0.2f),
                            selectedLabelColor     = NeonBlue,
                            containerColor         = SurfaceDark,
                            labelColor             = OnSurfaceMuted
                        ),
                        border    = FilterChipDefaults.filterChipBorder(
                            enabled             = true,
                            selected            = selectedProvider == key,
                            selectedBorderColor = NeonBlue.copy(0.6f),
                            borderColor         = DividerColor
                        )
                    )
                }
            }
        }

        if (selectedProvider == "custom") {
            item {
                CyberTextField(
                    value         = customHost,
                    onValueChange = { customHost = it },
                    label         = "IMAP Host",
                    placeholder   = "imap.yourprovider.com",
                    icon          = Icons.Default.Dns
                )
            }
        }

        item {
            CyberTextField(
                value         = emailAddress,
                onValueChange = { emailAddress = it },
                label         = "Email Address",
                placeholder   = "you@${selectedProvider}.com",
                icon          = Icons.Default.AlternateEmail,
                keyboardType  = KeyboardType.Email
            )
        }

        item {
            CyberTextField(
                value         = password,
                onValueChange = { password = it },
                label         = if (selectedProvider == "gmail") "App Password" else "Password",
                placeholder   = "••••••••••••••••",
                icon          = Icons.Default.Lock,
                isPassword    = true,
                passwordVisible = passVisible,
                onTogglePassword = { passVisible = !passVisible }
            )
        }

        item {
            // Gemini API key field
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showHelp = !showHelp }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.HelpOutline, null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                    }
                }
                AnimatedVisibility(showHelp) {
                    Card(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape  = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        border = BorderStroke(1.dp, NeonBlue.copy(0.3f))
                    ) {
                        Text(
                            "Get a free API key at aistudio.google.com → API Keys\n" +
                            "The key is used only for phishing analysis and is never stored.",
                            style    = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CyberTextField(
                    value           = geminiKey,
                    onValueChange   = { geminiKey = it },
                    label           = "AIza… API Key",
                    placeholder     = "AIzaSy...",
                    icon            = Icons.Default.VpnKey,
                    isPassword      = true,
                    passwordVisible = keyVisible,
                    onTogglePassword = { keyVisible = !keyVisible }
                )
            }
        }

        // Gmail app password tip
        if (selectedProvider == "gmail") {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(WarningAmber.copy(0.08f))
                        .border(1.dp, WarningAmber.copy(0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Gmail requires an App Password (not your regular password).\n" +
                        "Go to Google Account → Security → 2-Step Verification → App passwords.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp)
                    )
                }
            }
        }

        item {
            val isValid = emailAddress.isNotBlank() && password.isNotBlank() &&
                          geminiKey.isNotBlank() &&
                          (selectedProvider != "custom" || customHost.isNotBlank())

            Button(
                onClick  = { onConnect(emailAddress, password, selectedProvider, geminiKey, customHost) },
                enabled  = isValid,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = NeonBlue,
                    disabledContainerColor = NeonBlue.copy(0.3f)
                ),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.ConnectWithoutContact, null,
                    tint = if (isValid) Color.Black else Color.Black.copy(0.4f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONNECT & SCAN 50 EMAILS",
                    color        = if (isValid) Color.Black else Color.Black.copy(0.4f),
                    fontWeight   = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Reusable styled text field ─────────────────────────────────────────────

@Composable
private fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder   = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon   = { Icon(icon, null, tint = NeonBlue, modifier = Modifier.size(20.dp)) },
        trailingIcon  = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = OnSurfaceMuted
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = ImeAction.Next
        ),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = NeonBlue,
            unfocusedBorderColor    = DividerColor,
            cursorColor             = NeonBlue,
            focusedLabelColor       = NeonBlue,
            unfocusedLabelColor     = OnSurfaceMuted,
            focusedTextColor        = OnSurface,
            unfocusedTextColor      = OnSurface,
            focusedContainerColor   = SurfaceDark,
            unfocusedContainerColor = SurfaceDark
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Connecting / Fetching screens ─────────────────────────────────────────

@Composable
private fun ConnectingScreen() = PulsingStatusScreen(
    icon    = Icons.Default.ConnectWithoutContact,
    color   = NeonBlue,
    title   = "CONNECTING",
    subtitle = "Establishing secure IMAP connection…"
)

@Composable
private fun FetchingScreen() = PulsingStatusScreen(
    icon     = Icons.Default.Inbox,
    color    = NeonBlue,
    title    = "FETCHING EMAILS",
    subtitle = "Downloading last 50 messages…"
)

@Composable
private fun PulsingStatusScreen(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String
) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val rotation by infinite.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "r")
    val alpha    by infinite.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse), label = "a")

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(130.dp), Alignment.Center) {
                repeat(2) { i ->
                    val size = (130 - i * 30).dp
                    Box(
                        Modifier.size(size).clip(CircleShape)
                            .background(color.copy(0.04f + i * 0.03f))
                            .border(1.dp, color.copy((0.2f + i * 0.2f) * alpha), CircleShape)
                    )
                }
                Icon(icon, null, tint = color, modifier = Modifier.size(52.dp).rotate(rotation))
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.titleLarge.copy(
                color = color, letterSpacing = 2.sp))
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)),
                color      = color,
                trackColor = SurfaceVariant
            )
        }
    }
}

// ── Analysing (live streaming results) ────────────────────────────────────

@Composable
private fun AnalysingScreen(
    state: EmailAnalysisState.AnalysingEmails,
    filtered: List<Pair<EmailMessage, PhishingAnalysis>>,
    activeFilter: EmailFilter,
    onFilter: (EmailFilter) -> Unit
) {
    Column {
        // Progress header
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Analysing with Gemini AI",
                    style = MaterialTheme.typography.titleMedium.copy(color = NeonBlue)
                )
                Text(
                    state.currentSubject.take(45),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${state.current}/${state.total}",
                style = MaterialTheme.typography.titleLarge.copy(color = NeonBlue, fontWeight = FontWeight.Bold)
            )
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.current / state.total.toFloat() },
            modifier  = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).height(6.dp),
            color     = NeonBlue,
            trackColor = SurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // Live results so far
        FilterRow(activeFilter, onFilter)
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.first.id }) { (email, analysis) ->
                EmailResultCard(email, analysis)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Complete results ───────────────────────────────────────────────────────

@Composable
private fun ResultsScreen(
    state: EmailAnalysisState.Complete,
    filtered: List<Pair<EmailMessage, PhishingAnalysis>>,
    summary: EmailSummary,
    activeFilter: EmailFilter,
    onFilter: (EmailFilter) -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        // Summary banner
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryPill("PHISHING", summary.confirmed + summary.likely, DangerRed)
            SummaryPill("SUSPECT",  summary.suspicious,                 WarningAmber)
            SummaryPill("SAFE",     summary.safe,                       NeonGreen)
            SummaryPill("SCANNED",  summary.total,                      OnSurfaceMuted)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.scanDurationMs / 1000}s",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = NeonBlue, fontWeight = FontWeight.Bold)
                )
                Text("TIME", style = MaterialTheme.typography.labelSmall.copy(color = NeonBlue.copy(0.75f)))
            }
        }
        Spacer(Modifier.height(12.dp))

        FilterRow(activeFilter, onFilter)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filtered.size} emails", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDisconnect) {
                Icon(Icons.Default.Logout, null, tint = DangerRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("DISCONNECT", color = DangerRed, style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.first.id }) { (email, analysis) ->
                EmailResultCard(email, analysis)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Email Result Card ──────────────────────────────────────────────────────

@Composable
private fun EmailResultCard(email: EmailMessage, analysis: PhishingAnalysis) {
    var expanded by remember { mutableStateOf(false) }
    val accent   = verdictColor(analysis.verdict)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBackground),
        border   = BorderStroke(1.dp, accent.copy(0.45f))
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── Row 1: Verdict icon + subject + badge ──────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Verdict emoji box
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(0.15f)),
                    Alignment.Center
                ) {
                    Text(analysis.verdict.emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        email.subject.ifBlank { "(No Subject)" },
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "From: ${email.sender} <${email.senderEmail}>",
                        style    = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                VerdictBadge(analysis.verdict)
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 2: Summary + confidence ────────────────────────────────
            Text(
                analysis.summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
            )

            Spacer(Modifier.height(8.dp))

            // Confidence bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Confidence:",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { analysis.confidencePercent / 100f },
                    modifier  = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).height(5.dp),
                    color     = accent,
                    trackColor = SurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${analysis.confidencePercent}%",
                    style = MaterialTheme.typography.labelSmall.copy(color = accent)
                )
            }

            // Spoofed brand tag
            analysis.spoofedBrand?.let { brand ->
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(DangerRed.copy(0.12f))
                        .border(1.dp, DangerRed.copy(0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CorporateFare, null, tint = DangerRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Impersonating: $brand",
                        style = MaterialTheme.typography.labelSmall.copy(color = DangerRed)
                    )
                }
            }

            // ── Expanded details ───────────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    Divider(color = DividerColor)
                    Spacer(Modifier.height(10.dp))

                    // Threat indicators
                    if (analysis.threatIndicators.isNotEmpty()) {
                        Text(
                            "THREAT INDICATORS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accent, letterSpacing = 1.sp)
                        )
                        Spacer(Modifier.height(6.dp))
                        analysis.threatIndicators.forEach { indicator ->
                            ThreatIndicatorRow(indicator)
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Suspicious links
                    if (analysis.suspiciousLinks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "SUSPICIOUS LINKS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WarningAmber, letterSpacing = 1.sp)
                        )
                        Spacer(Modifier.height(6.dp))
                        analysis.suspiciousLinks.forEach { link ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(WarningAmber.copy(0.07f))
                                    .border(1.dp, WarningAmber.copy(0.25f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Link, null, tint = WarningAmber,
                                    modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    link,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Recommended action
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(NeonBlue.copy(0.08f))
                            .border(1.dp, NeonBlue.copy(0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lightbulb, null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            analysis.recommendedAction,
                            style = MaterialTheme.typography.bodyMedium.copy(color = NeonBlue, fontSize = 11.sp)
                        )
                    }

                    // Date / meta
                    Spacer(Modifier.height(8.dp))
                    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    Text(
                        "Received: ${fmt.format(Date(email.receivedAt))}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    )
                }
            }

            // Expand caret
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.Center) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = accent.copy(0.5f), modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ThreatIndicatorRow(indicator: ThreatIndicator) {
    val color = when (indicator.severity) {
        IndicatorSeverity.CRITICAL -> DangerRed
        IndicatorSeverity.HIGH     -> Color(0xFFFF6B35)
        IndicatorSeverity.MEDIUM   -> WarningAmber
        IndicatorSeverity.LOW      -> NeonBlue
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.07f))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(8.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Flag, null, tint = color,
            modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    indicator.category.label,
                    style = MaterialTheme.typography.titleMedium.copy(color = color, fontSize = 12.sp)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(color.copy(0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(indicator.severity.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = color, fontSize = 9.sp))
                }
            }
            Text(indicator.description, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp))
        }
    }
}

// ── Filter Row ─────────────────────────────────────────────────────────────

@Composable
private fun FilterRow(activeFilter: EmailFilter, onFilter: (EmailFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EmailFilter.values()) { filter ->
            FilterChip(
                selected = filter == activeFilter,
                onClick  = { onFilter(filter) },
                label    = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonBlue.copy(0.2f),
                    selectedLabelColor     = NeonBlue,
                    containerColor         = SurfaceDark,
                    labelColor             = OnSurfaceMuted
                ),
                border   = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = filter == activeFilter,
                    selectedBorderColor = NeonBlue.copy(0.6f),
                    borderColor         = DividerColor
                )
            )
        }
    }
}

// ── Error screens ──────────────────────────────────────────────────────────

@Composable
private fun AuthErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.LockOpen, null, tint = DangerRed, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Connection Failed", style = MaterialTheme.typography.titleLarge.copy(color = DangerRed))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onRetry, border = BorderStroke(1.dp, DangerRed)) {
                Text("TRY AGAIN", color = DangerRed)
            }
        }
    }
}

@Composable
private fun AnalysisErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Analysis Failed", style = MaterialTheme.typography.titleLarge.copy(color = DangerRed))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onRetry, border = BorderStroke(1.dp, DangerRed)) {
                Text("RETRY", color = DangerRed)
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun VerdictBadge(verdict: PhishingVerdict) {
    val color = verdictColor(verdict)
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(0.15f))
            .border(1.dp, color.copy(0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(verdict.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontSize = 9.sp))
    }
}

@Composable
private fun SummaryPill(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge.copy(
            color = color, fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color.copy(0.75f)))
    }
}

private fun verdictColor(verdict: PhishingVerdict): Color = when (verdict) {
    PhishingVerdict.SAFE               -> NeonGreen
    PhishingVerdict.SUSPICIOUS         -> WarningAmber
    PhishingVerdict.LIKELY_PHISHING    -> Color(0xFFFF6B35)
    PhishingVerdict.CONFIRMED_PHISHING -> DangerRed
}
