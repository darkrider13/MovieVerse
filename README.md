# MovieVerse (Kotlin)
Native Android app (Kotlin + Jetpack Compose), a 1:1 port of the web / Capacitor version.
Package: `com.trackerxx.movieverse`

- Build: GitHub Actions (`.github/workflows/android.yml`). Secrets: `SIGNING_KEYSTORE_BASE64`, optional `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`. The same package + signing key + rising versionCode lets the APK install over the old app.
- Data of the old app is moved with Settings > Export (old app) and Settings > Import (this app).
- Backup: Settings > Export writes an Excel file (`.xlsx`); Settings > Import reads that `.xlsx` and also the old `.json` backups.
- Project notes and conversion history: `HANDOFF.md`.
