package com.cybersentinel.data.repository

import com.cybersentinel.data.service.GeminiPhishingService
import com.cybersentinel.data.service.ImapEmailService
import com.cybersentinel.domain.model.EmailMessage
import com.cybersentinel.domain.model.PhishingAnalysis
import com.cybersentinel.domain.repository.EmailRepository
import com.cybersentinel.domain.repository.PhishingAnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class EmailRepositoryImpl(
    private val imapService: ImapEmailService,
    private val config: ImapEmailService.ImapConfig
) : EmailRepository {

    override suspend fun fetchRecentEmails(limit: Int): List<EmailMessage> =
        imapService.fetchRecentEmails(config, limit)
}

class PhishingAnalysisRepositoryImpl(
    private val geminiService: GeminiPhishingService
) : PhishingAnalysisRepository {

    override fun analyseEmails(
        emails: List<EmailMessage>
    ): Flow<Pair<EmailMessage, PhishingAnalysis>> = flow {
        for (email in emails) {
            val analysis = geminiService.analyseEmail(email)
            emit(Pair(email, analysis))
        }
    }
}
