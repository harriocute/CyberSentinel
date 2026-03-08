package com.cybersentinel.data.service

import com.cybersentinel.domain.model.EmailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Fetches emails via IMAP.
 *
 * Supports:
 *  - Gmail  (imap.gmail.com:993, SSL)
 *  - Outlook/Hotmail (outlook.office365.com:993)
 *  - Yahoo  (imap.mail.yahoo.com:993)
 *  - Generic IMAP servers
 *
 * Authentication:
 *  - Standard username/password (App Password for Gmail)
 *  - For a production app, replace with OAuth2 via Google Sign-In SDK
 *    and pass the access token as the password with XOAUTH2 mechanism.
 *
 * ⚠️  Gmail users must enable "App Passwords" in their Google Account
 *    (requires 2FA enabled) or use OAuth2.
 */
class ImapEmailService {

    data class ImapConfig(
        val host: String,
        val port: Int = 993,
        val username: String,
        val password: String,          // App Password or OAuth2 token
        val useOAuth2: Boolean = false,
        val folder: String = "INBOX"
    )

    companion object {
        fun gmailConfig(email: String, appPassword: String) = ImapConfig(
            host     = "imap.gmail.com",
            port     = 993,
            username = email,
            password = appPassword
        )

        fun outlookConfig(email: String, password: String) = ImapConfig(
            host     = "outlook.office365.com",
            port     = 993,
            username = email,
            password = password
        )

        fun yahooConfig(email: String, appPassword: String) = ImapConfig(
            host     = "imap.mail.yahoo.com",
            port     = 993,
            username = email,
            password = appPassword
        )
    }

    // ── Public API ─────────────────────────────────────────────────────────

    suspend fun fetchRecentEmails(config: ImapConfig, limit: Int = 50): List<EmailMessage> =
        withContext(Dispatchers.IO) {
            val store = connectToStore(config)
            try {
                val inbox = store.getFolder(config.folder)
                inbox.open(Folder.READ_ONLY)

                val total      = inbox.messageCount
                val startIndex = maxOf(1, total - limit + 1)
                val messages   = inbox.getMessages(startIndex, total)

                // Fetch headers + body in bulk (avoids per-message round trips)
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add(FetchProfile.Item.FLAGS)
                }
                inbox.fetch(messages, fp)

                messages.mapNotNull { msg ->
                    try { msg.toEmailMessage() } catch (e: Exception) { null }
                }.reversed() // Newest first
            } finally {
                try { store.close() } catch (e: Exception) { /* ignore */ }
            }
        }

    // ── IMAP connection ────────────────────────────────────────────────────

    private fun connectToStore(config: ImapConfig): Store {
        val props = Properties().apply {
            put("mail.imap.host",            config.host)
            put("mail.imap.port",            config.port.toString())
            put("mail.imap.ssl.enable",      "true")
            put("mail.imap.ssl.trust",       "*")
            put("mail.imap.starttls.enable", "true")
            put("mail.imap.auth.mechanisms", if (config.useOAuth2) "XOAUTH2" else "PLAIN LOGIN")
            put("mail.imap.timeout",         "15000")
            put("mail.imap.connectiontimeout", "10000")
        }

        val session = Session.getInstance(props)
        val store   = session.getStore("imap")
        store.connect(config.host, config.port, config.username, config.password)
        return store
    }

    // ── Message → Domain model ─────────────────────────────────────────────

    private fun Message.toEmailMessage(): EmailMessage {
        val addresses = from?.filterIsInstance<InternetAddress>() ?: emptyList()
        val senderName  = addresses.firstOrNull()?.personal ?: ""
        val senderEmail = addresses.firstOrNull()?.address ?: ""
        val body        = extractTextBody(this)
        val preview     = body.take(200).replace('\n', ' ').replace('\r', ' ')

        return EmailMessage(
            id           = "${messageNumber}_${sentDate?.time ?: System.currentTimeMillis()}",
            subject      = (subject ?: "(No Subject)").trim(),
            sender       = senderName.ifBlank { senderEmail },
            senderEmail  = senderEmail,
            preview      = preview,
            body         = body,
            receivedAt   = sentDate?.time ?: System.currentTimeMillis(),
            isRead       = flags?.contains(Flags.Flag.SEEN) == true
        )
    }

    /**
     * Recursively extracts plain-text content from a MIME message.
     * Prefers text/plain; falls back to stripping HTML tags.
     */
    private fun extractTextBody(part: Part): String {
        return when {
            part.isMimeType("text/plain") ->
                (part.content as? String) ?: ""

            part.isMimeType("text/html") -> {
                val html = (part.content as? String) ?: ""
                // Basic HTML strip — good enough for phishing analysis
                html.replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }

            part.isMimeType("multipart/*") -> {
                val mp = part.content as? MimeMultipart ?: return ""
                // Try plain text parts first
                val plainText = (0 until mp.count)
                    .map { mp.getBodyPart(it) }
                    .filter { it.isMimeType("text/plain") }
                    .joinToString("\n") { extractTextBody(it) }

                plainText.ifBlank {
                    (0 until mp.count)
                        .joinToString("\n") { extractTextBody(mp.getBodyPart(it)) }
                }
            }

            else -> ""
        }
    }
}
