# Cyber Sentinel — Setup Guide

## Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android device running API 26+ (Android 8.0)
- Physical device required for WiFi scanning (emulators won't work)

---

## Step 1 — Add Your Gemini API Key

1. Visit [aistudio.google.com](https://aistudio.google.com) → **Get API Key**
2. Create a key (free tier allows ~60 requests/min)
3. Open `app/build.gradle` and add to `defaultConfig`:

```groovy
defaultConfig {
    ...
    buildConfigField "String", "GEMINI_API_KEY", '"AIzaSy_YOUR_KEY_HERE"'
}
```

4. Enable BuildConfig in `build.gradle` (already included):
```groovy
buildFeatures {
    compose true
    buildConfig true   // ← add this
}
```

5. Access it in code via `BuildConfig.GEMINI_API_KEY`
   *(The EmailViewModel already accepts it as a runtime input for security)*

---

## Step 2 — Gmail App Password Setup

Gmail requires an **App Password** (not your regular password) for IMAP access:

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. **Security** → **2-Step Verification** (must be enabled)
3. **App passwords** → Select app: **Mail**, device: **Android**
4. Copy the 16-character password (no spaces)
5. Enter this password in the app's Email tab

> **Note:** If you use Google Workspace, your admin may need to enable IMAP access.

---

## Step 3 — Build & Run

```bash
# Clone and open in Android Studio
File → Open → select CyberSentinel/

# Let Gradle sync complete, then:
Run → Run 'app'  (Shift+F10)
```

---

## Architecture Overview

```
com.cybersentinel/
├── domain/
│   ├── model/          # Pure Kotlin data classes — no Android deps
│   │   ├── WifiNetwork.kt
│   │   ├── AppAudit.kt
│   │   ├── HiddenApp.kt
│   │   └── EmailPhishing.kt
│   └── repository/     # Interfaces only
│       ├── WifiRepository.kt
│       ├── AppAuditRepository.kt
│       ├── HiddenAppRepository.kt
│       └── EmailRepository.kt
├── data/
│   ├── service/        # Android API wrappers
│   │   ├── WifiScannerService.kt
│   │   ├── AppPermissionAuditorService.kt
│   │   ├── HiddenAppScannerService.kt
│   │   ├── ImapEmailService.kt
│   │   └── GeminiPhishingService.kt
│   └── repository/     # Concrete implementations
├── ui/
│   ├── theme/          # Dark cyberpunk Material 3 theme
│   ├── navigation/     # NavHost + bottom nav
│   └── screens/
│       ├── wifi/       # Tab 1: WiFi Security Scanner
│       ├── apps/       # Tab 2: App Permissions Auditor
│       ├── hidden/     # Tab 3: Hidden App Detector
│       └── email/      # Tab 4: Email Phishing Analyser
└── MainActivity.kt
```

---

## Production Hardening Checklist

- [ ] Replace manual DI with **Hilt** (`@HiltViewModel`, `@Singleton`)
- [ ] Replace IMAP App Passwords with **OAuth2 / Google Sign-In SDK**
- [ ] Store Gemini API key in **EncryptedSharedPreferences** or a backend proxy
- [ ] Add **ProGuard rules** for JavaMail and Retrofit
- [ ] Add `packagingOptions` to exclude duplicate META-INF files from JavaMail
- [ ] Implement **rate limiting** on Gemini calls (max 1 req/sec for free tier)
- [ ] Add **offline caching** with Room for scan results history

---

## Permissions Used

| Permission | Feature | When Requested |
|---|---|---|
| `ACCESS_FINE_LOCATION` | WiFi scanning | Runtime — WiFi tab |
| `ACCESS_WIFI_STATE` | WiFi scanning | Manifest only |
| `QUERY_ALL_PACKAGES` | App auditor & hidden scanner | Manifest only |
| `INTERNET` | Gemini API + IMAP | Manifest only |
