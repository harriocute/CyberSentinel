package com.cybersentinel.ui.screens.email

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cybersentinel.data.repository.EmailRepositoryImpl
import com.cybersentinel.data.repository.PhishingAnalysisRepositoryImpl
import com.cybersentinel.data.service.GeminiPhishingService
import com.cybersentinel.data.service.ImapEmailService
import com.cybersentinel.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EmailViewModel(application: Application) : AndroidViewModel(application) {

    // ── Connection state ───────────────────────────────────────────────────
    private val _authState = MutableStateFlow<EmailAuthState>(EmailAuthState.Idle)
    val authState: StateFlow<EmailAuthState> = _authState.asStateFlow()

    // ── Analysis state ─────────────────────────────────────────────────────
    private val _analysisState = MutableStateFlow<EmailAnalysisState>(EmailAnalysisState.Idle)
    val analysisState: StateFlow<EmailAnalysisState> = _analysisState.asStateFlow()

    // ── Active filter ──────────────────────────────────────────────────────
    private val _activeFilter = MutableStateFlow(EmailFilter.ALL)
    val activeFilter: StateFlow<EmailFilter> = _activeFilter.asStateFlow()

    /** Derived filtered list */
    val filteredResults: StateFlow<List<Pair<EmailMessage, PhishingAnalysis>>> = combine(
        _analysisState, _activeFilter
    ) { state, filter ->
        val all = when (state) {
            is EmailAnalysisState.AnalysingEmails -> state.resultsReady
            is EmailAnalysisState.Complete        -> state.results
            else                                  -> emptyList()
        }
        all.filter { (_, analysis) ->
            when (filter) {
                EmailFilter.ALL       -> true
                EmailFilter.PHISHING  -> analysis.verdict == PhishingVerdict.CONFIRMED_PHISHING ||
                                         analysis.verdict == PhishingVerdict.LIKELY_PHISHING
                EmailFilter.SUSPICIOUS -> analysis.verdict == PhishingVerdict.SUSPICIOUS
                EmailFilter.SAFE      -> analysis.verdict == PhishingVerdict.SAFE
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Repositories (instantiated lazily after credentials are provided)
    private var emailRepository: EmailRepositoryImpl? = null
    private var phishingRepository: PhishingAnalysisRepositoryImpl? = null

    private var scanStartTime = 0L

    // ── Actions ────────────────────────────────────────────────────────────

    /**
     * Connects to the email account and immediately starts the analysis.
     * @param emailAddress  User's email address
     * @param password      App Password (Gmail) or account password
     * @param provider      "gmail", "outlook", "yahoo", or "custom"
     * @param geminiApiKey  User's Gemini API key
     * @param customHost    Required when provider == "custom"
     */
    fun connectAndAnalyse(
        emailAddress: String,
        password: String,
        provider: String,
        geminiApiKey: String,
        customHost: String = ""
    ) {
        viewModelScope.launch {
            _authState.value = EmailAuthState.Connecting

            try {
                val config = when (provider) {
                    "gmail"   -> ImapEmailService.gmailConfig(emailAddress, password)
                    "outlook" -> ImapEmailService.outlookConfig(emailAddress, password)
                    "yahoo"   -> ImapEmailService.yahooConfig(emailAddress, password)
                    else      -> ImapEmailService.ImapConfig(
                        host     = customHost,
                        username = emailAddress,
                        password = password
                    )
                }

                emailRepository     = EmailRepositoryImpl(ImapEmailService(), config)
                phishingRepository  = PhishingAnalysisRepositoryImpl(GeminiPhishingService(geminiApiKey))

                _authState.value = EmailAuthState.Connected(emailAddress)
                startAnalysis()

            } catch (e: Exception) {
                _authState.value = EmailAuthState.Error(
                    when {
                        e.message?.contains("authentication failed", ignoreCase = true) == true ->
                            "Authentication failed. Check your credentials or App Password."
                        e.message?.contains("connection refused", ignoreCase = true) == true ->
                            "Could not connect to mail server. Check host/port settings."
                        else -> e.message ?: "Connection failed"
                    }
                )
            }
        }
    }

    private fun startAnalysis() {
        val emailRepo    = emailRepository ?: return
        val analysisRepo = phishingRepository ?: return
        scanStartTime = System.currentTimeMillis()

        viewModelScope.launch {
            // Step 1: Fetch emails
            _analysisState.value = EmailAnalysisState.FetchingEmails(0f)
            val emails = emailRepo.fetchRecentEmails(50)
            val total  = emails.size

            if (emails.isEmpty()) {
                _analysisState.value = EmailAnalysisState.Complete(
                    results        = emptyList(),
                    totalScanned   = 0,
                    scanDurationMs = System.currentTimeMillis() - scanStartTime
                )
                return@launch
            }

            // Step 2: Analyse with Gemini
            val results = mutableListOf<Pair<EmailMessage, PhishingAnalysis>>()
            var current = 0

            analysisRepo.analyseEmails(emails)
                .catch { e ->
                    _analysisState.value = EmailAnalysisState.Error(
                        "AI analysis failed: ${e.message ?: "Unknown error"}"
                    )
                }
                .collect { pair ->
                    current++
                    results.add(pair)

                    if (current < total) {
                        _analysisState.value = EmailAnalysisState.AnalysingEmails(
                            current        = current,
                            total          = total,
                            currentSubject = pair.first.subject,
                            resultsReady   = results
                                .sortedByDescending { it.second.verdict.ordinal }
                        )
                    } else {
                        _analysisState.value = EmailAnalysisState.Complete(
                            results        = results.sortedByDescending { it.second.verdict.ordinal },
                            totalScanned   = total,
                            scanDurationMs = System.currentTimeMillis() - scanStartTime
                        )
                    }
                }
        }
    }

    fun setFilter(filter: EmailFilter) { _activeFilter.value = filter }

    fun disconnect() {
        emailRepository    = null
        phishingRepository = null
        _authState.value   = EmailAuthState.Idle
        _analysisState.value = EmailAnalysisState.Idle
        _activeFilter.value  = EmailFilter.ALL
    }

    fun retry() {
        _authState.value = EmailAuthState.Idle
        _analysisState.value = EmailAnalysisState.Idle
    }

    fun getSummary(results: List<Pair<EmailMessage, PhishingAnalysis>>): EmailSummary {
        return EmailSummary(
            total     = results.size,
            confirmed = results.count { it.second.verdict == PhishingVerdict.CONFIRMED_PHISHING },
            likely    = results.count { it.second.verdict == PhishingVerdict.LIKELY_PHISHING },
            suspicious = results.count { it.second.verdict == PhishingVerdict.SUSPICIOUS },
            safe      = results.count { it.second.verdict == PhishingVerdict.SAFE }
        )
    }
}

enum class EmailFilter(val label: String) {
    ALL("All"),
    PHISHING("Phishing"),
    SUSPICIOUS("Suspicious"),
    SAFE("Safe")
}

data class EmailSummary(
    val total: Int,
    val confirmed: Int,
    val likely: Int,
    val suspicious: Int,
    val safe: Int
)
