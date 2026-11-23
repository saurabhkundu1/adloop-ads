# AdLoop (`adloop-ads`)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2013%2B-green.svg)](https://developer.android.com)
[![Compile SDK](https://img.shields.io/badge/Compile%20SDK-37-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-brightgreen.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blueviolet.svg)](https://developer.android.com/jetpack/compose)

> **Earn while watching ads** — An Android mobile application prototype featuring a vertical swipeable ad feed containing both direct sponsor campaigns and network provider mock inventory, with viewer revenue sharing, verified watch timers, local SQLite ledger, and user-configurable ad frequency and pacing controls.

---

## 📱 Overview

**AdLoop** reimagines digital advertising by connecting publishers, direct brand sponsors, and viewers in a transparent revenue-sharing loop:
- **Publishers** host sponsor campaigns and provider-supplied ads within a unified, distraction-free vertical feed.
- **Sponsors** can launch targeted video/display campaigns with custom budgets, view duration requirements, and rich branding styles via a web dashboard.
- **Viewers** actively engage with ads, complete the required watch durations, and earn reward credits deposited directly into their in-app wallet.
- **Smart Pacing & Fatigue Prevention** ensures ads are not shown in rapid succession through intelligent frequency capping and minimum cooldown intervals.

---

## ✨ Features

### 1. Vertical Swipeable Ad Feed
- Modern full-screen swipe feed powered by Jetpack Compose.
- Seamlessly interleaves direct brand sponsor campaigns and third-party ad network provider mock placements.
- Color gradient backgrounds, customizable branding art, and headline tags tailored to each sponsor.

### 2. Verified Watch Timers & Rewarded Sessions
- Active countdown timer ensuring viewers watch the full required ad duration before receiving credits.
- Server-side session verification (`/watch/start` and `/watch/complete`) ensuring tamper-proof ledger logging.
- Strict anti-fraud rules: direct sponsor campaigns enforce a single reward per campaign per user; provider preview ads support continuous repeat sessions.

### 3. In-App Ad Frequency & Pacing Manager
- Dynamic pacing system (`AdFrequencyManager.kt`) preventing viewer burnout:
  - **Hourly Ad Cap:** Restricts maximum ad impressions per hour (default: 5 ads/hour).
  - **Cooldown Interval:** Enforces a minimum cooldown period between consecutive ad views (default: 2 minutes).
- User-accessible **Pacing Settings Dialog** in the action bar allows viewing real-time pacing metrics, adjusting limits, and toggling enforcement on the fly.
- Persistent state backed by Android SharedPreferences via AndroidX KTX extensions.

### 4. Transparent Credit Wallet & SQLite Ledger
- Built-in wallet displaying total earned viewer credits.
- Complete transaction history breaking down credit sources, campaigns, and timestamps.
- Native regional currency mapping (`ADLOOP_DEMO_CURRENCY`, default `INR` / `IN`).

### 5. Local Sponsor Campaign Portal
- Web dashboard accessible at `http://localhost:8787/sponsor`.
- Create new sponsor campaigns with real-time gradient picker, headline, description, target countries, duration, and credit allocation.
- Auto-seeding of provider preview inventory.

---

## 🛠️ Architecture & Tech Stack

```
AdLoop
├── app/                  # Android Client (Kotlin + Jetpack Compose)
│   ├── src/main/java/
│   │   └── com/adloop/app/
│   │       ├── MainActivity.kt        # Primary UI, Feed, Wallet & Frequency Dialog
│   │       ├── AdFrequencyManager.kt  # Pacing, cooldown timers & hourly impression limits
│   │       └── AdLoopApi.kt           # HTTP client communicating with backend
│   └── src/test/java/
│       └── com/adloop/app/
│           ├── AdFrequencyTest.kt     # Unit tests for frequency pacing & cooldowns
│           └── AdLoopApiTest.kt       # Unit tests for API models & JSON serialization
└── server/               # Local Node.js Demo Backend
    ├── server.mjs        # REST API + native SQLite (WAL mode)
    ├── sponsor.html      # Web dashboard for sponsor campaign creation
    └── adloop-dev.sqlite # Local ACID-compliant database ledger
```

- **Android Platform:**
  - **Language:** Kotlin 2.4.20
  - **UI Toolkit:** Jetpack Compose with Material 3
  - **SDK Support:** `minSdk = 33`, `compileSdk = 37`, `targetSdk = 37`
  - **Quality & Lint:** Zero-warning enforcement with `warningsAsErrors = true`
- **Backend API:**
  - **Runtime:** Node.js 22+ (ES Modules)
  - **Database:** Native `node:sqlite` (`DatabaseSync`) with Write-Ahead Logging (WAL)
  - **Security:** Strict demo-only guardrails rejecting production environments (`NODE_ENV=production`)

---

## 🚀 Getting Started

### Prerequisites
- **Node.js**: v22.0.0 or higher
- **Android Studio**: Ladybug / Meerkat (or later) with Android SDK 37
- **Java**: JDK 17 or JDK 21 / 25 (bundled Android Studio JBR supported)
- **Device / Emulator**: Android 13+ (API level 33+)

---

### Step 1: Run the Backend Server
From the project root:
```bash
node server/server.mjs
```
The server will start at `http://0.0.0.0:8787`.

- **Sponsor Dashboard**: Open [http://localhost:8787/sponsor](http://localhost:8787/sponsor) in your browser to create sample sponsor campaigns or inspect active campaigns.

---

### Step 2: Build and Run the Android App
1. Open the `AdLoop` folder in Android Studio.
2. Ensure your Android emulator or physical device is connected.
   - When running on an Android Emulator, the app connects to the local server via `10.0.2.2:8787`.
   - When running on a physical device, set your machine's LAN IP in `AdLoopApi.kt`.
3. Build and install:
```powershell
.\gradlew.bat assembleDebug
```
The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

### Step 3: Run Tests & Verification
Run the unit test suite:
```powershell
.\gradlew.bat testDebugUnitTest
```

Run Android Lint quality check:
```powershell
.\gradlew.bat lintDebug
```
Both commands must pass with **0 errors and 0 warnings**.

---

## 📡 API Reference

| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `/session` | `POST` | Initializes or resumes an anonymous user session with regional currency info. |
| `/feed` | `GET` | Fetches active sponsor campaigns and seeded provider placements for the user. |
| `/watch/start` | `POST` | Begins a verified watch session for a specified campaign. |
| `/watch/complete` | `POST` | Verifies watch duration, awards viewer credits, and logs to ledger. |
| `/wallet` | `GET` | Retrieves the viewer's current credit balance and transaction history. |
| `/campaigns` | `POST` | Creates a new sponsor campaign (used by the web dashboard). |

---

## ⚠️ Demo Scope & Disclaimer

This project is a functional architectural prototype and demonstration:
- Ad network provider items are local mock inventory; no live external ad network SDKs (Google AdMob, Unity Ads, etc.) are currently bundled.
- Demo viewer credits show the publisher-to-viewer revenue share mechanism and are not real cash currency.
- Region assignment is determined locally by `ADLOOP_DEMO_COUNTRY` and `ADLOOP_DEMO_CURRENCY`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
