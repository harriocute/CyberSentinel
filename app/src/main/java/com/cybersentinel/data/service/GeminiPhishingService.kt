package com.cybersentinel.data.service

import com.cybersentinel.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls the Gemini 1.5 Flash API to analyse an email for phishing signals.
 *
 * Uses gemini-1.5-flash for cost-efficiency when batch-scanning 50 emails.
 * Swap to gemini-1.5-pro for higher accuracy on suspicious emails.
 *
 * ⚙️  Setup: add your Gemini API key to local.properties:
 *       GEMINI_API_KEY=AIza...
 *   then expose it via BuildConfig (see README).
 */
class GeminiPhishingService(private val apiKey: String) {

    companion object {
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private const val TIMEOUT_MS = 30_000
    }

    // ── Public API ─────────────────────────────────────────────────────────

    suspend fun analyseEmail(email: EmailMessage): PhishingAnalysis = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(email)
        val responseJson = callGeminiApi(prompt)
        parseGeminiResponse(email.id, responseJson)
    }

    // ── Prompt Engineering ─────────────────────────────────────────────────

    /**
     * Structured prompt that instructs Gemini to return a strict JSON payload.
     * Using few-shot examples + explicit schema prevents hallucinations.
     */
    private fun buildPrompt(email: EmailMessage): String = """
You are a cybersecurity expert specialising in email phishing detection.

Analyse the following email and respond ONLY with a valid JSON object — no markdown, no prose, no backticks.

EMAIL TO ANALYSE:
Subject: ${email.subject}
From: ${email.sender} <${email.senderEmail}>
Body:
${email.body.take(3000)}

RESPOND WITH THIS EXACT JSON SCHEMA:
{
  "verdict": "SAFE" | "SUSPICIOUS" | "LIKELY_PHISHING" | "CONFIRMED_PHISHING",
  "confidence_percent": <integer 0-100>,
  "summary": "<1-2 sentence plain-English explanation of your verdict>",
  "spoofed_brand": "<brand name being impersonated, or null>",
  "recommended_action": "<short action the user should take>",
  "suspicious_links": ["<url1>", "<url2>"],
  "threat_indicators": [
    {
      "category": "URGENCY" | "IMPERSONATION" | "SUSPICIOUS_LINK" | "DATA_HARVESTING" | "GRAMMAR" | "SPOOFED_SENDER" | "ATTACHMENT" | "REWARD_SCAM",
      "description": "<specific detail about this indicator>",
      "severity": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
    }
  ]
}

ANALYSIS GUIDELINES:
- CONFIRMED_PHISHING: Clear impersonation + credential harvesting link or malware
- LIKELY_PHISHING: Strong signals (spoofed sender, urgency + link, brand impersonation)
- SUSPICIOUS: Some concerning patterns but inconclusive
- SAFE: No phishing indicators found
- Be conservative — only flag CONFIRMED_PHISHING when highly certain
- Extract actual URLs from the body if present
- If the email appears legitimate (bank statement, newsletter with unsubscribe), mark SAFE
""".trimIndent()

    // ── Gemini API Call ────────────────────────────────────────────────────

    private fun callGeminiApi(prompt: String): String {
        val url = URL("$GEMINI_ENDPOINT?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod          = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput               = true
            connectTimeout         = TIMEOUT_MS
            readTimeout            = TIMEOUT_MS
        }

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)       // Low temp for consistent structured output
                put("maxOutputTokens", 1024)
                put("responseMimeType", "application/json")
            })
            put("safetySettings", JSONArray().apply {
                // Relax safety filters for security analysis content
                listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                       "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    .forEach { cat ->
                        put(JSONObject().apply {
                            put("category", cat)
                            put("threshold", "BLOCK_NONE")
                        })
                    }
            })
        }.toString()

        OutputStreamWriter(conn.outputStream).use { it.write(requestBody) }

        val responseCode = conn.responseCode
        val stream = if (responseCode == 200) conn.inputStream else conn.errorStream
        val rawResponse = stream.bufferedReader().use { it.readText() }

        if (responseCode != 200) {
            throw RuntimeException("Gemini API error $responseCode: $rawResponse")
        }

        return rawResponse
    }

    // ── Response Parsing ───────────────────────────────────────────────────

    private fun parseGeminiResponse(emailId: String, rawJson: String): PhishingAnalysis {
        return try {
            // Extract the text content from Gemini's wrapper
            val root     = JSONObject(rawJson)
            val text     = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            // Strip any accidental markdown fences
            val cleaned = text
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val analysis = JSONObject(cleaned)

            val verdict = safeEnum<PhishingVerdict>(
                analysis.optString("verdict", "SUSPICIOUS")
            ) ?: PhishingVerdict.SUSPICIOUS

            val indicators = mutableListOf<ThreatIndicator>()
            val indicatorArr = analysis.optJSONArray("threat_indicators")
            if (indicatorArr != null) {
                for (i in 0 until indicatorArr.length()) {
                    val ind = indicatorArr.getJSONObject(i)
                    val category = safeEnum<IndicatorCategory>(ind.optString("category", "")) ?: continue
                    val severity = safeEnum<IndicatorSeverity>(ind.optString("severity", "MEDIUM"))
                        ?: IndicatorSeverity.MEDIUM
                    indicators.add(ThreatIndicator(
                        category    = category,
                        description = ind.optString("description", ""),
                        severity    = severity
                    ))
                }
            }

            val links = mutableListOf<String>()
            val linksArr = analysis.optJSONArray("suspicious_links")
            if (linksArr != null) {
                for (i in 0 until linksArr.length()) links.add(linksArr.getString(i))
            }

            PhishingAnalysis(
                emailId           = emailId,
                verdict           = verdict,
                confidencePercent = analysis.optInt("confidence_percent", 50).coerceIn(0, 100),
                threatIndicators  = indicators,
                summary           = analysis.optString("summary", "Analysis complete."),
                suspiciousLinks   = links,
                spoofedBrand      = analysis.optString("spoofed_brand").takeIf { it != "null" && it.isNotBlank() },
                recommendedAction = analysis.optString("recommended_action", "No action required.")
            )
        } catch (e: Exception) {
            // Fallback — never crash the scan due to a parsing error
            PhishingAnalysis(
                emailId           = emailId,
                verdict           = PhishingVerdict.SUSPICIOUS,
                confidencePercent = 0,
                threatIndicators  = emptyList(),
                summary           = "Analysis failed: ${e.message}",
                suspiciousLinks   = emptyList(),
                spoofedBrand      = null,
                recommendedAction = "Manual review recommended."
            )
        }
    }

    private inline fun <reified T : Enum<T>> safeEnum(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value.uppercase().replace(" ", "_") }
}
