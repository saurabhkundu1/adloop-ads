# AdLoop

An Android prototype for one swipeable ad feed containing both direct sponsor campaigns and provider-supplied ad placements. The publisher receives sponsor/provider revenue; the app records a demo viewer share as credits after a completed view.

## Run the local prototype

1. Start the local API from this folder with Node.js 24: `node server/server.mjs`.
2. Open `http://localhost:8787/sponsor` in a browser to create a sample sponsor campaign. Provider preview ads are seeded automatically.
3. Open this folder in Android Studio and run the app on an Android emulator. The debug app connects to the API through `10.0.2.2:8787`.

The debug APK can also be built with `gradlew.bat assembleDebug` and is written to `app/build/outputs/apk/debug/app-debug.apk`.

## What is working here

- Sponsor campaigns and provider preview ads share one feed and swipe flow.
- Sponsor campaigns can be created in the local sponsor page while provider preview ads remain in the feed.
- The server records watch sessions, applies a once-per-campaign rule to direct sponsor campaigns, allows repeat provider preview views, and records each credit once in a SQLite ledger.
- The wallet shows the source and credits for completed demo views.

## Still demo-only

Provider A/B entries are local mock inventory; no real ad network SDK, provider approval, payout, sponsor payment, identity checks, location verification, or redemption service is connected. Their sample credits demonstrate the publisher-to-viewer revenue share flow and are not cash. The demo region and currency come from `ADLOOP_DEMO_COUNTRY` and `ADLOOP_DEMO_CURRENCY`; users cannot change the server-assigned region in the app. The API refuses `NODE_ENV=production` and accepts only local/emulator requests.
