package com.cybersentinel.domain.model

/**
 * Represents a single email fetched from the user's account.
 */
data class EmailMessage(
    val id: String,
    val subject: String,
    val sender: String,
    val senderEmail: String,
    val preview: String,       // first ~200 chars of body
    val body: String,          // full plain-text body
    val receivedAt: Long,      // epoch ms
    val isRead: Boolean
)

/**
 * The result of Gemini's phishing analysis on a single email.
 */
data class PhishingAnalysis(
    val emailId: String,
    val verdict: PhishingVerdict,
    val confidencePercent: Int,       // 0–100
    val threatIndicators: List<ThreatIndicator>,
    val summary: String,              // 1–2 sentence plain-English explanation
    val suspiciousLinks: List<String>,
    val spoofedBrand: String?,        // e.g. "PayPal", "Google", null if none
    val recommendedAction: String
)

enum class PhishingVerdict(val label: String, val emoji: String) {
    SAFE("Safe",               "✅"),
    SUSPICIOUS("Suspicious",   "⚠️"),
    LIKELY_PHISHING("Likely Phishing", "🚨"),
    CONFIRMED_PHISHING("Confirmed Phishing", "☠️")
}

data class ThreatIndicator(
    val category: IndicatorCategory,
    val description: String,
    val severity: IndicatorSeverity
)

enum class IndicatorCategory(val label: String) {
    URGENCY("Urgency/Fear"),
    IMPERSONATION("Impersonation"),
    SUSPICIOUS_LINK("Suspicious Link"),
    DATA_HARVESTING("Data Harvesting"),
    GRAMMAR("Grammar/Spelling"),
    SPOOFED_SENDER("Spoofed Sender"),
    ATTACHMENT("Suspicious Attachment"),
    REWARD_SCAM("Reward/Prize Scam")
}

enum class IndicatorSeverity(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical")
}

// ── Screen states ──────────────────────────────────────────────────────────

sealed class EmailAuthState {
    object Idle                             : EmailAuthState()
    object Connecting                       : EmailAuthState()
    data class Connected(val email: String) : EmailAuthState()
    data class Error(val message: String)   : EmailAuthState()
}

sealed class EmailAnalysisState {
    object Idle                                                      : EmailAnalysisState()
    data class FetchingEmails(val progress: Float)                   : EmailAnalysisState()
    data class AnalysingEmails(
        val current: Int,
        val total: Int,
        val currentSubject: String,
        val resultsReady: List<Pair<EmailMessage, PhishingAnalysis>>
    )                                                                : EmailAnalysisState()
    data class Complete(
        val results: List<Pair<EmailMessage, PhishingAnalysis>>,
        val totalScanned: Int,
        val scanDurationMs: Long
    )                                                                : EmailAnalysisState()
    data class Error(val message: String)                            : EmailAnalysisState()
}
