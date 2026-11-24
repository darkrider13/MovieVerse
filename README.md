<div align="center">

<img src="icon.png" alt="MovieVerse icon" width="132" height="132">

# MovieVerse

*Every story starts with a movie.*

A fast, native Android movie tracker. Keep your own library, follow what is trending, see what releases today, in cinemas and on OTT, and open rich movie details with ratings, cast, trailers and subtitles.

[![Download APK](https://img.shields.io/badge/Download-APK-E50914?style=for-the-badge&logo=android&logoColor=white)](https://github.com/darkrider13/MovieVerse/releases/latest/download/MovieVerse.apk)
[![Build](https://img.shields.io/github/actions/workflow/status/darkrider13/MovieVerse/android.yml?branch=main&style=for-the-badge&label=Build&logo=githubactions&logoColor=white)](https://github.com/darkrider13/MovieVerse/actions)
[![Latest release](https://img.shields.io/github/v/release/darkrider13/MovieVerse?style=for-the-badge&label=Release&color=1f6feb)](https://github.com/darkrider13/MovieVerse/releases/latest)

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android%206.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-23-informational?style=flat-square)
![Target SDK](https://img.shields.io/badge/targetSdk-35-informational?style=flat-square)

[Features](#features) ·
[Install](#install) ·
[Setup](#first-time-setup) ·
[Backup](#backup-and-restore) ·
[Updates](#updates) ·
[Build](#build-from-source) ·
[Project layout](#project-layout)

</div>

---

## Features

### Your Library
- Keep every movie under a **status** (Coming Soon, Plan to watch, Favourite and your own), organised in **folders**.
- Give each movie **your own rating**.
- Filter by genre, rating, year and status, search inside the library, and **drag to reorder**.
- Pull down to refresh the library data.

### Trending
- Trending Movies, Trending Today and Weekly Trending rows, plus Trending TV Shows and Top Rated Movies.
- **IMDb ratings** on the cards, filled in as soon as they arrive.
- Cached for a few hours, so the page opens instantly and refreshes in the background.

### Releases
Three views in one place:

| View | What it shows |
|---|---|
| **Released Today** | Titles that came out today |
| **In Cinemas** | What is coming to cinemas, with year filter |
| **On OTT** | What reaches streaming, for movies or TV |

All views have search plus **Country** and **Genre** filters, and load more as you scroll.

### Movie details
Poster, title and year, overview, **Trailer** (opens on YouTube), **Subtitle** (Bangla and English), duration, country, language, IMDb rating and votes, genres, cinema and OTT dates, budget and collection, with a verdict such as *Blockbuster* or *Disaster* when the numbers are known.

### Cast and crew
Director, writers and the main cast in a clean carousel with exactly four people per page. Tap anyone to open their profile with **Movies** and **Awards**.

### Polish
- Dark theme with a soft red and blue glow, shimmer placeholders while data loads.
- Popups open and close instantly, together with the background blur.
- One-tap **update check** from the top bar.

---

## Install

1. Open the [latest release](https://github.com/darkrider13/MovieVerse/releases/latest) and download **MovieVerse.apk**.
2. Open the file on your phone. If Android asks, allow **Install from this source** for your browser or file manager.
3. Start the app and follow the short setup below.

Requires **Android 6.0 (API 23)** or newer.

---

## First-time setup

MovieVerse loads its data from public movie services, using **your own free API keys**. Add them in **Settings** (gear icon, top right).

| Service | Needed? | Used for |
|---|---|---|
| **TMDB** | Required | Trending, Releases, search, posters, cast, trailers, details |
| **MDBList** | Recommended | Main rating source (IMDb, Letterboxd, Rotten Tomatoes, Metacritic, Trakt and more), budget and collection |
| **OMDb** | Optional | IMDb rating, Rotten Tomatoes score, votes |
| **XMDb** | Optional | Second source of the IMDb rating |
| **SubSource** | Optional | Bangla and English subtitles |

You can add **several keys per service**. When one key hits its limit, the app moves to the next automatically. Without a TMDB key, Trending shows a short note asking you to add one.

---

## Backup and restore

- **Export**: Settings > Export writes your whole library to an Excel file (`.xlsx`) that you can save or share.
- **Import**: Settings > Import reads that file back. Older `.json` backups still work.
- **Moving to a new phone or from the old app**: export on the old one, import on the new one.

---

## Updates

Tap the **refresh icon** in the top bar. The icon spins three times while the app checks the newest build on GitHub, then tells you either *Update available* (with a download dialog that shows progress, size and speed) or *You're up to date*. The app also offers new builds by itself when it starts.

Every build is signed with the same key and has a rising version code, so a new APK installs **over** the old app and keeps your data.

---

## Build from source

The APK is built by **GitHub Actions** (`.github/workflows/android.yml`) on every push to `main`. The signed release build is published to the `latest` release together with a small `version.json` that the app reads for update checks.

**Repository secrets** (Settings > Secrets and variables > Actions):

| Secret | Required | Meaning |
|---|---|---|
| `SIGNING_KEYSTORE_BASE64` | Yes | Your keystore, base64 encoded |
| `SIGNING_KEY_ALIAS` | No | Defaults to `androiddebugkey` |
| `SIGNING_STORE_PASSWORD` | No | Defaults to `android` |
| `SIGNING_KEY_PASSWORD` | No | Defaults to the store password |

**Local build** (JDK 17, Android SDK 35):

```bash
gradle assembleRelease
```

The repository name for update checks is filled in automatically from `GITHUB_REPOSITORY` during the build, so renaming or forking the repo keeps updates working.

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Images | Coil |
| HTML parsing | jsoup |
| Async | Kotlin Coroutines |
| Build | Gradle (Kotlin DSL), R8 optimised release |
| Min / target SDK | 23 / 35 |
| Package | `com.trackerxx.movieverse` |

**Permissions:** internet and network state, plus install-packages for the in-app update. Storage permissions are only requested on Android 9 and older for backup export.

---

## Project layout

```text
app/src/main/java/com/trackerxx/movieverse/
├── data/       library database, models, backup (xlsx / json)
├── net/        TMDB, MDBList, OMDb, XMDb, subtitles, Wikipedia, currency
├── trending/   Trending page logic
├── releases/   Releases page logic
├── enrich/     background ratings and details refresh
├── update/     update check, download, installer
├── ui/
│   ├── library/     home, library, Releases and Trending screens
│   ├── add/         movie details, cast, person, trailer
│   ├── settings/    settings, export, import, update flow
│   ├── components/  dialogs, buttons, motion helpers
│   └── theme/       colors, type, icons
└── util/       formatting and small helpers
```

---

## Credits

Movie data and images are provided by [TMDB](https://www.themoviedb.org/). Ratings come from [MDBList](https://mdblist.com/), [OMDb](https://www.omdbapi.com/) and XMDb. Subtitles come from SubSource.

*This product uses the TMDB API but is not endorsed or certified by TMDB.*

MovieVerse is a personal project. All movie titles, posters and trademarks belong to their respective owners.
