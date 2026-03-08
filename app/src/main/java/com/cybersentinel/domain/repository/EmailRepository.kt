package com.cybersentinel.domain.repository

import com.cybersentinel.domain.model.EmailMessage
import com.cybersentinel.domain.model.PhishingAnalysis
import kotlinx.coroutines.flow.Flow

/**
 * Fetches emails from a connected account (IMAP/Gmail API).
 */
interface EmailRepository {
    suspend fun fetchRecentEmails(limit: Int = 50): List<EmailMessage>
}

/**
 * Sends email content to the AI analysis service and returns a verdict.
 */
interface PhishingAnalysisRepository {
    /**
     * Analyses a batch of emails, emitting results one-by-one as each
     * Gemini API call completes.
     */
    fun analyseEmails(emails: List<EmailMessage>): Flow<Pair<EmailMessage, PhishingAnalysis>>
}
