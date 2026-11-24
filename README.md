<p align="center">
  <img src="icon.png" alt="MovieVerse icon" width="140" height="140">
</p>

<h1 align="center">MovieVerse</h1>

<p align="center"><i>Every story starts with a movie</i></p>

<p align="center">
  Native Android movie tracker built with Kotlin and Jetpack Compose.
</p>

---

## Features

- **Library**: keep every movie with a status (Coming Soon, Plan to watch, Favourite and more), folders, your own rating, filters and drag-to-reorder.
- **Trending**: Trending Movies, Trending Today and Weekly Trending, with IMDb ratings on every card.
- **Releases**: see what is coming out and when it reaches OTT.
- **Movie details**: poster, trailer, subtitle, duration, country, language, IMDb rating and votes, genre, cinema and OTT dates, budget and collection.
- **Cast & crew**: tap a person to open Person details with their Movies and Awards.
- **Smooth loading**: poster, cast, status and the other sections load with the same soft shimmer boxes.
- **Backup**: Settings > Export writes an Excel file (`.xlsx`), Settings > Import reads it back (old `.json` backups work too).
- **Auto update**: the app tells you when a new build is available.

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Images | Coil |
| Min / target SDK | 23 / 35 |
| Package | `com.trackerxx.movieverse` |

## Build

The APK is built by GitHub Actions (`.github/workflows/android.yml`) on every push to `main` and published to the `latest` release.

Secrets needed (Settings > Secrets and variables > Actions):

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_KEY_ALIAS` (optional)
- `SIGNING_STORE_PASSWORD` (optional)
- `SIGNING_KEY_PASSWORD` (optional)

The same package name, the same signing key and a rising version code let every new APK install over the old app.

## Moving from the old app

Export from the old app (Settings > Export), then import that file in this app (Settings > Import).

## Notes

Project notes and conversion history: `HANDOFF.md`.
